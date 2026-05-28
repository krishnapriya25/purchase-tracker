package com.wex.purchases.exchange;

import com.wex.purchases.exchange.dto.TreasuryRatesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Client for the Treasury Reporting Rates of Exchange dataset on Fiscal Data.
 *
 * <p>Fetches the most recent rate on or before a target date for a given
 * {@code country_currency_desc}. Responses are cached because historical rates do not change.
 * Transient transport failures and 5xx responses are retried with exponential backoff.</p>
 */
@Component
public class TreasuryFiscalDataClient {

    private static final Logger log = LoggerFactory.getLogger(TreasuryFiscalDataClient.class);

    private static final String ENDPOINT_PATH = "/services/api/fiscal_service/v1/accounting/od/rates_of_exchange";
    private static final String FIELDS = "country_currency_desc,exchange_rate,record_date";

    private final RestClient restClient;

    public TreasuryFiscalDataClient(RestClient.Builder restClientBuilder,
                                    @Value("${treasury.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * Fetch the most recent rate on or before {@code asOf} for the given currency descriptor.
     * Returns an empty optional when the dataset contains no such record.
     *
     * @param countryCurrencyDesc Treasury identifier, e.g. "Canada-Dollar" or "Euro Zone-Euro"
     * @param asOf upper bound for the rate's {@code record_date}
     */
    @Cacheable(value = "treasuryRates", unless = "#result == null || #result.isEmpty()")
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 250, multiplier = 2.0)
    )
    public Optional<TreasuryRateRecordView> findLatestRateOnOrBefore(String countryCurrencyDesc, LocalDate asOf) {
        URI uri = buildUri(countryCurrencyDesc, asOf);
        log.debug("Calling Treasury Fiscal Data API: {}", uri);

        TreasuryRatesResponse response;
        try {
            response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new RestClientResponseException(
                                "Treasury API client error",
                                res.getStatusCode(), res.getStatusText(),
                                res.getHeaders(), null, null);
                    })
                    .body(TreasuryRatesResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("Treasury API returned {} for currency={} asOf={}", e.getStatusCode(), countryCurrencyDesc, asOf);
            throw e;
        }

        if (response == null || response.data() == null || response.data().isEmpty()) {
            return Optional.empty();
        }
        var record = response.data().get(0);
        return Optional.of(new TreasuryRateRecordView(
                record.countryCurrencyDesc(), record.exchangeRate(), record.recordDate()));
    }

    private URI buildUri(String countryCurrencyDesc, LocalDate asOf) {
        // The Treasury filter syntax uses literal colons; encode the value to be safe
        // for currency descriptors that contain spaces (e.g. "Euro Zone-Euro").
        String encodedCurrency = URLEncoder.encode(countryCurrencyDesc, StandardCharsets.UTF_8);
        String filter = "country_currency_desc:eq:" + encodedCurrency
                + ",record_date:lte:" + asOf;
        String query = "fields=" + FIELDS
                + "&filter=" + filter
                + "&sort=-record_date"
                + "&page%5Bsize%5D=1"; // page[size]=1, brackets percent-encoded
        return URI.create(ENDPOINT_PATH + "?" + query);
    }

    /**
     * View type returned to the service layer. Avoids leaking the raw Jackson DTO.
     */
    public record TreasuryRateRecordView(String countryCurrencyDesc, java.math.BigDecimal exchangeRate, LocalDate recordDate)
            implements java.io.Serializable {
    }
}

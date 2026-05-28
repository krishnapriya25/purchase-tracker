package com.wex.purchases.exchange;

import com.wex.purchases.common.exception.ExchangeRateUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Resolves the exchange rate to apply when converting a purchase amount to a target currency.
 *
 * <p>Per the product requirement: use the latest rate published on or before the purchase date,
 * provided that rate is no more than six calendar months older than the purchase date. If no such
 * rate exists, the conversion is rejected with {@link ExchangeRateUnavailableException}.</p>
 */
@Service
public class ExchangeRateLookupService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateLookupService.class);

    /** Calendar-month lookback window for the eligible exchange rate. */
    static final int LOOKBACK_MONTHS = 6;

    private final TreasuryFiscalDataClient client;

    public ExchangeRateLookupService(TreasuryFiscalDataClient client) {
        this.client = client;
    }

    /**
     * Find the exchange rate eligible for converting USD into {@code targetCurrency} on {@code purchaseDate}.
     *
     * @throws ExchangeRateUnavailableException when no rate is available within the lookback window
     */
    public ExchangeRate findRateFor(String targetCurrency, LocalDate purchaseDate) {
        Optional<TreasuryFiscalDataClient.TreasuryRateRecordView> result =
                client.findLatestRateOnOrBefore(targetCurrency, purchaseDate);

        if (result.isEmpty()) {
            log.info("No Treasury rate found for currency={} on or before {}", targetCurrency, purchaseDate);
            throw ExchangeRateUnavailableException.forCurrencyAndDate(targetCurrency, purchaseDate);
        }

        var record = result.get();
        LocalDate earliestAcceptable = purchaseDate.minusMonths(LOOKBACK_MONTHS);
        if (record.recordDate().isBefore(earliestAcceptable)) {
            log.info("Latest Treasury rate {} for currency={} is older than {}-month window from {}",
                    record.recordDate(), targetCurrency, LOOKBACK_MONTHS, purchaseDate);
            throw ExchangeRateUnavailableException.forCurrencyAndDate(targetCurrency, purchaseDate);
        }

        return new ExchangeRate(record.countryCurrencyDesc(), record.exchangeRate(), record.recordDate());
    }
}

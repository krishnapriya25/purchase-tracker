package com.wex.purchases.exchange;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpMethod;
import org.hamcrest.Matchers;

@RestClientTest(TreasuryFiscalDataClient.class)
@TestPropertySource(properties = "treasury.base-url=http://treasury.test")
class TreasuryFiscalDataClientTest {

    @Autowired
    private TreasuryFiscalDataClient client;

    @Autowired
    private MockRestServiceServer server;

    private static final String SAMPLE_RESPONSE = """
            {
              "data": [
                {
                  "country_currency_desc": "Canada-Dollar",
                  "exchange_rate": "1.337",
                  "record_date": "2024-12-31"
                }
              ],
              "meta": {"count": 1},
              "links": {}
            }
            """;

    private static final String EMPTY_RESPONSE = """
            {
              "data": [],
              "meta": {"count": 0},
              "links": {}
            }
            """;

    @Test
    void successfulResponse_returnsLatestRate() {
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(Matchers.allOf(
                        Matchers.startsWith("http://treasury.test/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"),
                        Matchers.containsString("fields=country_currency_desc,exchange_rate,record_date"),
                        Matchers.containsString("filter=country_currency_desc:eq:Canada-Dollar,record_date:lte:2025-03-15"),
                        Matchers.containsString("sort=-record_date"))))
                .andRespond(withSuccess(SAMPLE_RESPONSE, MediaType.APPLICATION_JSON));

        Optional<TreasuryFiscalDataClient.TreasuryRateRecordView> result =
                client.findLatestRateOnOrBefore("Canada-Dollar", LocalDate.of(2025, 3, 15));

        assertThat(result).isPresent();
        assertThat(result.get().countryCurrencyDesc()).isEqualTo("Canada-Dollar");
        assertThat(result.get().exchangeRate()).isEqualByComparingTo(new BigDecimal("1.337"));
        assertThat(result.get().recordDate()).isEqualTo(LocalDate.of(2024, 12, 31));
        server.verify();
    }

    @Test
    void emptyDataArray_returnsEmptyOptional() {
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));

        Optional<TreasuryFiscalDataClient.TreasuryRateRecordView> result =
                client.findLatestRateOnOrBefore("No-Such-Currency", LocalDate.of(2025, 3, 15));

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    void clientError_throwsRestClientResponseException() {
        server.expect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() ->
                client.findLatestRateOnOrBefore("Bad-Currency", LocalDate.of(2025, 3, 15)))
                .isInstanceOf(RestClientResponseException.class);
    }
}

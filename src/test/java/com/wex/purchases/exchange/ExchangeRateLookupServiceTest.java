package com.wex.purchases.exchange;

import com.wex.purchases.common.exception.ExchangeRateUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateLookupServiceTest {

    @Mock
    private TreasuryFiscalDataClient client;

    @InjectMocks
    private ExchangeRateLookupService service;

    @Test
    void returnsRate_whenWithinSixMonths() {
        LocalDate purchaseDate = LocalDate.of(2025, 6, 15);
        // 3 months back - well within window
        TreasuryFiscalDataClient.TreasuryRateRecordView record =
                new TreasuryFiscalDataClient.TreasuryRateRecordView(
                        "Canada-Dollar", new BigDecimal("1.337"), LocalDate.of(2025, 3, 31));
        when(client.findLatestRateOnOrBefore(eq("Canada-Dollar"), eq(purchaseDate)))
                .thenReturn(Optional.of(record));

        ExchangeRate rate = service.findRateFor("Canada-Dollar", purchaseDate);

        assertThat(rate.countryCurrencyDesc()).isEqualTo("Canada-Dollar");
        assertThat(rate.rate()).isEqualByComparingTo("1.337");
        assertThat(rate.recordDate()).isEqualTo(LocalDate.of(2025, 3, 31));
    }

    @Test
    void returnsRate_atSixMonthBoundary_inclusive() {
        // Boundary case: rate is exactly 6 calendar months before purchase date -> should be accepted
        LocalDate purchaseDate = LocalDate.of(2025, 7, 15);
        LocalDate recordDate = LocalDate.of(2025, 1, 15); // exactly 6 months earlier
        when(client.findLatestRateOnOrBefore(any(), any()))
                .thenReturn(Optional.of(new TreasuryFiscalDataClient.TreasuryRateRecordView(
                        "Canada-Dollar", new BigDecimal("1.30"), recordDate)));

        ExchangeRate rate = service.findRateFor("Canada-Dollar", purchaseDate);

        assertThat(rate.recordDate()).isEqualTo(recordDate);
    }

    @Test
    void throwsRateUnavailable_whenJustOutsideSixMonths() {
        // Rate is 6 months and 1 day before purchase date -> outside window
        LocalDate purchaseDate = LocalDate.of(2025, 7, 15);
        LocalDate recordDate = LocalDate.of(2025, 1, 14);
        when(client.findLatestRateOnOrBefore(any(), any()))
                .thenReturn(Optional.of(new TreasuryFiscalDataClient.TreasuryRateRecordView(
                        "Canada-Dollar", new BigDecimal("1.30"), recordDate)));

        assertThatThrownBy(() -> service.findRateFor("Canada-Dollar", purchaseDate))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("Canada-Dollar")
                .hasMessageContaining("2025-07-15");
    }

    @Test
    void throwsRateUnavailable_whenClientReturnsEmpty() {
        LocalDate purchaseDate = LocalDate.of(2025, 7, 15);
        when(client.findLatestRateOnOrBefore(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findRateFor("Mars-Credit", purchaseDate))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("Mars-Credit");
    }
}

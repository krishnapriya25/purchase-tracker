package com.wex.purchases.purchase;

import com.wex.purchases.common.exception.ExchangeRateUnavailableException;
import com.wex.purchases.common.exception.InvalidPurchaseAmountException;
import com.wex.purchases.common.exception.PurchaseNotFoundException;
import com.wex.purchases.exchange.ExchangeRate;
import com.wex.purchases.exchange.ExchangeRateLookupService;
import com.wex.purchases.purchase.dto.ConvertedPurchaseResponse;
import com.wex.purchases.purchase.dto.CreatePurchaseRequest;
import com.wex.purchases.purchase.dto.PurchaseResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private PurchaseRepository repository;

    @Mock
    private ExchangeRateLookupService exchangeRateLookupService;

    @InjectMocks
    private PurchaseService purchaseService;

    @Test
    void create_persistsAmountRoundedToNearestCent_halfUp() {
        // 49.995 -> 50.00 with HALF_UP
        CreatePurchaseRequest req = new CreatePurchaseRequest(
                "Coffee", LocalDate.of(2025, 3, 15), new BigDecimal("49.995"));
        when(repository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResponse response = purchaseService.create(req);

        assertThat(response.purchaseAmount()).isEqualByComparingTo("50.00");
        assertThat(response.purchaseAmount().scale()).isEqualTo(2);
        assertThat(response.id()).isNotNull();
        assertThat(response.description()).isEqualTo("Coffee");
        assertThat(response.transactionDate()).isEqualTo(LocalDate.of(2025, 3, 15));
    }

    @Test
    void create_roundsDown_whenSubCentLessThanHalf() {
        // 49.994 -> 49.99 with HALF_UP
        CreatePurchaseRequest req = new CreatePurchaseRequest(
                "Tea", LocalDate.of(2025, 3, 15), new BigDecimal("49.994"));
        when(repository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResponse response = purchaseService.create(req);

        assertThat(response.purchaseAmount()).isEqualByComparingTo("49.99");
    }

    @Test
    void create_rejectsAmountThatRoundsToZero() {
        // 0.004 rounds to 0.00 -> reject
        CreatePurchaseRequest req = new CreatePurchaseRequest(
                "Dust", LocalDate.of(2025, 3, 15), new BigDecimal("0.004"));

        assertThatThrownBy(() -> purchaseService.create(req))
                .isInstanceOf(InvalidPurchaseAmountException.class)
                .hasMessageContaining("at least 0.01 USD");

        verify(repository, never()).save(any());
    }

    @Test
    void findById_returnsStoredPurchase() {
        UUID id = UUID.randomUUID();
        Purchase purchase = new Purchase(id, "Books", LocalDate.of(2025, 3, 15), new BigDecimal("19.99"));
        when(repository.findById(id)).thenReturn(Optional.of(purchase));

        PurchaseResponse response = purchaseService.findById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.description()).isEqualTo("Books");
        assertThat(response.purchaseAmount()).isEqualByComparingTo("19.99");
    }

    @Test
    void findById_throwsNotFound_whenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.findById(id))
                .isInstanceOf(PurchaseNotFoundException.class);
    }

    @Test
    void findByIdInCurrency_convertsAndRoundsResult() {
        UUID id = UUID.randomUUID();
        LocalDate purchaseDate = LocalDate.of(2025, 3, 15);
        Purchase purchase = new Purchase(id, "Books", purchaseDate, new BigDecimal("100.00"));
        when(repository.findById(id)).thenReturn(Optional.of(purchase));

        // 100.00 * 1.3372 = 133.72
        ExchangeRate rate = new ExchangeRate("Canada-Dollar", new BigDecimal("1.3372"), LocalDate.of(2024, 12, 31));
        when(exchangeRateLookupService.findRateFor(eq("Canada-Dollar"), eq(purchaseDate))).thenReturn(rate);

        ConvertedPurchaseResponse response = purchaseService.findByIdInCurrency(id, "Canada-Dollar");

        assertThat(response.originalAmountUsd()).isEqualByComparingTo("100.00");
        assertThat(response.targetCurrency()).isEqualTo("Canada-Dollar");
        assertThat(response.exchangeRate()).isEqualByComparingTo("1.3372");
        assertThat(response.exchangeRateDate()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(response.convertedAmount()).isEqualByComparingTo("133.72");
        assertThat(response.convertedAmount().scale()).isEqualTo(2);
    }

    @Test
    void findByIdInCurrency_roundsConvertedAmountHalfUp() {
        UUID id = UUID.randomUUID();
        LocalDate purchaseDate = LocalDate.of(2025, 3, 15);
        Purchase purchase = new Purchase(id, "Books", purchaseDate, new BigDecimal("10.00"));
        when(repository.findById(id)).thenReturn(Optional.of(purchase));

        // 10.00 * 0.12345 = 1.2345 -> 1.23 (HALF_UP)
        ExchangeRate rate = new ExchangeRate("X-Y", new BigDecimal("0.12345"), LocalDate.of(2025, 3, 1));
        when(exchangeRateLookupService.findRateFor(any(), any())).thenReturn(rate);

        ConvertedPurchaseResponse response = purchaseService.findByIdInCurrency(id, "X-Y");

        assertThat(response.convertedAmount()).isEqualByComparingTo("1.23");
    }

    @Test
    void findByIdInCurrency_propagatesRateUnavailable() {
        UUID id = UUID.randomUUID();
        LocalDate purchaseDate = LocalDate.of(2020, 1, 1);
        Purchase purchase = new Purchase(id, "Old", purchaseDate, new BigDecimal("10.00"));
        when(repository.findById(id)).thenReturn(Optional.of(purchase));
        when(exchangeRateLookupService.findRateFor(eq("Mars-Credit"), eq(purchaseDate)))
                .thenThrow(ExchangeRateUnavailableException.forCurrencyAndDate("Mars-Credit", purchaseDate));

        assertThatThrownBy(() -> purchaseService.findByIdInCurrency(id, "Mars-Credit"))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    void findByIdInCurrency_throwsNotFound_beforeQueryingRate() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.findByIdInCurrency(id, "Canada-Dollar"))
                .isInstanceOf(PurchaseNotFoundException.class);
    }
}

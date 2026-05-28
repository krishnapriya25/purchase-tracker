package com.wex.purchases.common.exception;

import java.time.LocalDate;

/**
 * Raised when the Treasury Reporting Rates of Exchange dataset has no rate for the
 * requested target currency within the configured lookback window prior to the purchase date.
 */
public class ExchangeRateUnavailableException extends RuntimeException {

    private final String targetCurrency;
    private final LocalDate purchaseDate;

    public ExchangeRateUnavailableException(String message, String targetCurrency, LocalDate purchaseDate) {
        super(message);
        this.targetCurrency = targetCurrency;
        this.purchaseDate = purchaseDate;
    }

    public static ExchangeRateUnavailableException forCurrencyAndDate(String targetCurrency, LocalDate purchaseDate) {
        String message = "The purchase cannot be converted to " + targetCurrency
                + ": no exchange rate is available within 6 months on or before " + purchaseDate;
        return new ExchangeRateUnavailableException(message, targetCurrency, purchaseDate);
    }

    public String getTargetCurrency() {
        return targetCurrency;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }
}

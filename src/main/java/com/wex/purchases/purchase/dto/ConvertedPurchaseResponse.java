package com.wex.purchases.purchase.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Stored purchase transaction converted to a target currency using the Treasury Reporting Rates of Exchange")
public record ConvertedPurchaseResponse(

        @Schema(example = "9c7e9d7b-0c4a-4a4e-9a4e-2f3e8a1c2b7d")
        UUID id,

        @Schema(example = "Office supplies")
        String description,

        @Schema(example = "2025-03-15")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate transactionDate,

        @Schema(example = "49.99", description = "Original amount in US dollars")
        BigDecimal originalAmountUsd,

        @Schema(example = "Canada-Dollar", description = "Treasury country_currency_desc of the target currency")
        String targetCurrency,

        @Schema(example = "1.337", description = "Exchange rate applied (USD -> target currency)")
        BigDecimal exchangeRate,

        @Schema(example = "2024-12-31", description = "Record date of the exchange rate used")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate exchangeRateDate,

        @Schema(example = "66.83", description = "Converted amount, rounded to two decimal places")
        BigDecimal convertedAmount
) {
}

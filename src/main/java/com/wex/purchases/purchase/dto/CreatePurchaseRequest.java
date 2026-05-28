package com.wex.purchases.purchase.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body to create a purchase transaction.
 * The purchase amount is in US dollars; values with more than two decimal places
 * are accepted and rounded to the nearest cent on storage.
 */
@Schema(description = "Purchase to create")
public record CreatePurchaseRequest(

        @Schema(example = "Office supplies", maxLength = 50)
        @NotBlank(message = "description is required")
        @Size(max = 50, message = "description must not exceed 50 characters")
        String description,

        @Schema(example = "2025-03-15", description = "ISO-8601 calendar date (YYYY-MM-DD)")
        @NotNull(message = "transactionDate is required")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate transactionDate,

        @Schema(example = "49.99", description = "Amount in USD; rounded to the nearest cent on storage")
        @NotNull(message = "purchaseAmount is required")
        @Positive(message = "purchaseAmount must be greater than zero")
        BigDecimal purchaseAmount
) {
}

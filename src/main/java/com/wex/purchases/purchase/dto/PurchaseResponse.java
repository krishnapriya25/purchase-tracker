package com.wex.purchases.purchase.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Stored purchase transaction")
public record PurchaseResponse(

        @Schema(example = "9c7e9d7b-0c4a-4a4e-9a4e-2f3e8a1c2b7d")
        UUID id,

        @Schema(example = "Office supplies")
        String description,

        @Schema(example = "2025-03-15")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate transactionDate,

        @Schema(example = "49.99", description = "Amount in USD rounded to the nearest cent")
        BigDecimal purchaseAmount
) {
}

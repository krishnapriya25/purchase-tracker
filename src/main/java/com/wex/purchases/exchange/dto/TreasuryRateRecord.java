package com.wex.purchases.exchange.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Single row from the Treasury Fiscal Data {@code rates_of_exchange} dataset.
 * The API returns numeric fields as strings, which Jackson coerces into BigDecimal/LocalDate.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TreasuryRateRecord(

        @JsonProperty("country_currency_desc") String countryCurrencyDesc,
        @JsonProperty("exchange_rate") BigDecimal exchangeRate,
        @JsonProperty("record_date") LocalDate recordDate
) {
}

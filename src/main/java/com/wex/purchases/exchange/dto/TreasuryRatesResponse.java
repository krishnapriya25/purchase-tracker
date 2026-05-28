package com.wex.purchases.exchange.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Top-level shape of the Treasury Fiscal Data API response for the
 * rates_of_exchange endpoint. Only the {@code data} array is needed for our lookup.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TreasuryRatesResponse(List<TreasuryRateRecord> data) {
}

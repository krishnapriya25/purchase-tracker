package com.wex.purchases.exchange;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single Treasury Reporting Rates of Exchange entry. Records are quarterly snapshots
 * keyed by {@code country_currency_desc} and {@code record_date}. Implements
 * {@link Serializable} so it can be safely held by the in-memory Caffeine cache.
 */
public record ExchangeRate(String countryCurrencyDesc, BigDecimal rate, LocalDate recordDate) implements Serializable {
}

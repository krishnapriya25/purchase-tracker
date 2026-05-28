package com.wex.purchases.purchase;

import com.wex.purchases.common.exception.InvalidPurchaseAmountException;
import com.wex.purchases.common.exception.PurchaseNotFoundException;
import com.wex.purchases.exchange.ExchangeRate;
import com.wex.purchases.exchange.ExchangeRateLookupService;
import com.wex.purchases.purchase.dto.ConvertedPurchaseResponse;
import com.wex.purchases.purchase.dto.CreatePurchaseRequest;
import com.wex.purchases.purchase.dto.PurchaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Core service for storing purchase transactions and retrieving them
 * in different currencies via the Treasury Reporting Rates of Exchange.
 */
@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    /** Scale used for all stored and returned monetary amounts. */
    static final int MONEY_SCALE = 2;
    static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final PurchaseRepository repository;
    private final ExchangeRateLookupService exchangeRateLookupService;

    public PurchaseService(PurchaseRepository repository,
                           ExchangeRateLookupService exchangeRateLookupService) {
        this.repository = repository;
        this.exchangeRateLookupService = exchangeRateLookupService;
    }

    /**
     * Persist a new purchase transaction, rounding the supplied amount to the nearest cent.
     */
    @Transactional
    public PurchaseResponse create(CreatePurchaseRequest request) {
        BigDecimal rounded = request.purchaseAmount().setScale(MONEY_SCALE, MONEY_ROUNDING);
        if (rounded.signum() <= 0) {
            throw new InvalidPurchaseAmountException(
                    "purchaseAmount must round to at least 0.01 USD");
        }

        Purchase purchase = new Purchase(
                UUID.randomUUID(),
                request.description(),
                request.transactionDate(),
                rounded);
        Purchase saved = repository.save(purchase);
        log.info("Stored purchase id={} amountUsd={} date={}",
                saved.getId(), saved.getPurchaseAmountUsd(), saved.getTransactionDate());
        return toResponse(saved);
    }

    /** Return a stored purchase in its original USD form. */
    @Transactional(readOnly = true)
    public PurchaseResponse findById(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    /**
     * Return a stored purchase converted to {@code targetCurrency} using the Treasury rate active
     * on or before the purchase date and within the configured lookback window.
     */
    @Transactional(readOnly = true)
    public ConvertedPurchaseResponse findByIdInCurrency(UUID id, String targetCurrency) {
        Purchase purchase = loadOrThrow(id);
        ExchangeRate rate = exchangeRateLookupService.findRateFor(targetCurrency, purchase.getTransactionDate());

        BigDecimal converted = purchase.getPurchaseAmountUsd()
                .multiply(rate.rate())
                .setScale(MONEY_SCALE, MONEY_ROUNDING);

        return new ConvertedPurchaseResponse(
                purchase.getId(),
                purchase.getDescription(),
                purchase.getTransactionDate(),
                purchase.getPurchaseAmountUsd(),
                rate.countryCurrencyDesc(),
                rate.rate(),
                rate.recordDate(),
                converted);
    }

    private Purchase loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new PurchaseNotFoundException(id));
    }

    private PurchaseResponse toResponse(Purchase p) {
        return new PurchaseResponse(p.getId(), p.getDescription(), p.getTransactionDate(), p.getPurchaseAmountUsd());
    }
}

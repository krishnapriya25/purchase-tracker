package com.wex.purchases.common.exception;

import java.util.UUID;

public class PurchaseNotFoundException extends RuntimeException {

    private final UUID id;

    public PurchaseNotFoundException(UUID id) {
        super("Purchase not found: " + id);
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}

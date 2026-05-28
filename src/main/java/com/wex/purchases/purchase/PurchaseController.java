package com.wex.purchases.purchase;

import com.wex.purchases.purchase.dto.ConvertedPurchaseResponse;
import com.wex.purchases.purchase.dto.CreatePurchaseRequest;
import com.wex.purchases.purchase.dto.PurchaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchases")
@Tag(name = "Purchases", description = "Store and retrieve purchase transactions")
@Validated
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    @Operation(summary = "Store a new purchase transaction")
    public ResponseEntity<PurchaseResponse> create(@Valid @RequestBody CreatePurchaseRequest request) {
        PurchaseResponse created = purchaseService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a stored purchase in its original US dollar amount")
    public PurchaseResponse get(@PathVariable UUID id) {
        return purchaseService.findById(id);
    }

    @GetMapping("/{id}/conversions")
    @Operation(
            summary = "Retrieve a stored purchase converted to a target currency",
            description = "Uses the Treasury Reporting Rates of Exchange rate active on or before "
                    + "the purchase date, provided that rate is within the last 6 months."
    )
    public ConvertedPurchaseResponse convert(
            @PathVariable UUID id,
            @RequestParam("currency")
            @NotBlank(message = "currency is required")
            String currency) {
        return purchaseService.findByIdInCurrency(id, currency);
    }
}

package com.wex.purchases.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wex.purchases.common.exception.ExchangeRateUnavailableException;
import com.wex.purchases.common.exception.PurchaseNotFoundException;
import com.wex.purchases.purchase.dto.ConvertedPurchaseResponse;
import com.wex.purchases.purchase.dto.CreatePurchaseRequest;
import com.wex.purchases.purchase.dto.PurchaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PurchaseController.class)
class PurchaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PurchaseService purchaseService;

    @Test
    void createPurchase_returns201_withLocationHeader() throws Exception {
        UUID generatedId = UUID.fromString("9c7e9d7b-0c4a-4a4e-9a4e-2f3e8a1c2b7d");
        when(purchaseService.create(any(CreatePurchaseRequest.class)))
                .thenReturn(new PurchaseResponse(generatedId, "Coffee",
                        LocalDate.of(2025, 3, 15), new BigDecimal("4.99")));

        String body = """
                {
                  "description": "Coffee",
                  "transactionDate": "2025-03-15",
                  "purchaseAmount": 4.99
                }
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.endsWith("/api/v1/purchases/" + generatedId)))
                .andExpect(jsonPath("$.id").value(generatedId.toString()))
                .andExpect(jsonPath("$.description").value("Coffee"))
                .andExpect(jsonPath("$.transactionDate").value("2025-03-15"))
                .andExpect(jsonPath("$.purchaseAmount").value(4.99));
    }

    @Test
    void createPurchase_rejectsDescriptionLongerThan50Characters() throws Exception {
        String tooLong = "x".repeat(51);
        String body = """
                {
                  "description": "%s",
                  "transactionDate": "2025-03-15",
                  "purchaseAmount": 1.00
                }
                """.formatted(tooLong);

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    void createPurchase_acceptsDescriptionExactly50Characters() throws Exception {
        String exactly50 = "x".repeat(50);
        when(purchaseService.create(any())).thenReturn(new PurchaseResponse(
                UUID.randomUUID(), exactly50, LocalDate.of(2025, 3, 15), new BigDecimal("1.00")));

        String body = """
                {
                  "description": "%s",
                  "transactionDate": "2025-03-15",
                  "purchaseAmount": 1.00
                }
                """.formatted(exactly50);

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createPurchase_rejectsBlankDescription() throws Exception {
        String body = """
                {
                  "description": "  ",
                  "transactionDate": "2025-03-15",
                  "purchaseAmount": 1.00
                }
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    void createPurchase_rejectsNegativeAmount() throws Exception {
        String body = """
                {
                  "description": "Refund",
                  "transactionDate": "2025-03-15",
                  "purchaseAmount": -10.00
                }
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.purchaseAmount").exists());
    }

    @Test
    void createPurchase_rejectsZeroAmount() throws Exception {
        String body = """
                {
                  "description": "Free",
                  "transactionDate": "2025-03-15",
                  "purchaseAmount": 0
                }
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.purchaseAmount").exists());
    }

    @Test
    void createPurchase_rejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.transactionDate").exists())
                .andExpect(jsonPath("$.fieldErrors.purchaseAmount").exists());
    }

    @Test
    void createPurchase_rejectsUnparseableDate() throws Exception {
        String body = """
                {
                  "description": "Test",
                  "transactionDate": "not-a-date",
                  "purchaseAmount": 1.00
                }
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    void getPurchase_returnsOriginal() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.findById(id)).thenReturn(new PurchaseResponse(
                id, "Books", LocalDate.of(2025, 3, 15), new BigDecimal("19.99")));

        mockMvc.perform(get("/api/v1/purchases/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.purchaseAmount").value(19.99));
    }

    @Test
    void getPurchase_returns404_whenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.findById(id)).thenThrow(new PurchaseNotFoundException(id));

        mockMvc.perform(get("/api/v1/purchases/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Purchase not found"))
                .andExpect(jsonPath("$.purchaseId").value(id.toString()));
    }

    @Test
    void convertPurchase_returnsConvertedAmount() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.findByIdInCurrency(eq(id), eq("Canada-Dollar")))
                .thenReturn(new ConvertedPurchaseResponse(
                        id, "Books", LocalDate.of(2025, 3, 15),
                        new BigDecimal("100.00"),
                        "Canada-Dollar",
                        new BigDecimal("1.337"),
                        LocalDate.of(2024, 12, 31),
                        new BigDecimal("133.70")));

        mockMvc.perform(get("/api/v1/purchases/{id}/conversions", id)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.targetCurrency").value("Canada-Dollar"))
                .andExpect(jsonPath("$.exchangeRate").value(1.337))
                .andExpect(jsonPath("$.exchangeRateDate").value("2024-12-31"))
                .andExpect(jsonPath("$.convertedAmount").value(133.70));
    }

    @Test
    void convertPurchase_returns422_whenRateUnavailable() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.findByIdInCurrency(any(), any()))
                .thenThrow(ExchangeRateUnavailableException.forCurrencyAndDate(
                        "Mars-Credit", LocalDate.of(2020, 1, 1)));

        mockMvc.perform(get("/api/v1/purchases/{id}/conversions", id)
                        .param("currency", "Mars-Credit"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Exchange rate unavailable"))
                .andExpect(jsonPath("$.targetCurrency").value("Mars-Credit"));
    }

    @Test
    void convertPurchase_returns400_whenCurrencyMissing() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/purchases/{id}/conversions", id))
                .andExpect(status().isBadRequest());
    }
}

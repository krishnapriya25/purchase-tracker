package com.wex.purchases.integration;

import com.wex.purchases.exchange.TreasuryFiscalDataClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test exercising the full HTTP -> service -> JPA path with H2 in-memory storage.
 * The Treasury HTTP client is mocked here; its HTTP behavior is covered by
 * {@link com.wex.purchases.exchange.TreasuryFiscalDataClientTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TreasuryFiscalDataClient treasuryClient;

    @Test
    void storeThenRetrieveOriginal_andConverted() throws Exception {
        // 1. Store a purchase
        String createBody = """
                {
                  "description": "Annual subscription",
                  "transactionDate": "2025-03-15",
                  "purchaseAmount": 199.995
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseAmount").value(200.00)) // rounded HALF_UP
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        String id = responseJson.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        UUID purchaseId = UUID.fromString(id);

        // 2. Retrieve the original
        mockMvc.perform(get("/api/v1/purchases/{id}", purchaseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Annual subscription"))
                .andExpect(jsonPath("$.purchaseAmount").value(200.00));

        // 3. Set up Treasury mock and retrieve converted
        when(treasuryClient.findLatestRateOnOrBefore(eq("Canada-Dollar"), any()))
                .thenReturn(Optional.of(new TreasuryFiscalDataClient.TreasuryRateRecordView(
                        "Canada-Dollar", new BigDecimal("1.337"), LocalDate.of(2024, 12, 31))));

        mockMvc.perform(get("/api/v1/purchases/{id}/conversions", purchaseId)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalAmountUsd").value(200.00))
                .andExpect(jsonPath("$.exchangeRate").value(1.337))
                .andExpect(jsonPath("$.exchangeRateDate").value("2024-12-31"))
                // 200.00 * 1.337 = 267.40
                .andExpect(jsonPath("$.convertedAmount").value(267.40));
    }

    @Test
    void convert_returns422_whenTreasuryHasNoRateInWindow() throws Exception {
        // Store a purchase
        String createBody = """
                {
                  "description": "Vintage",
                  "transactionDate": "2020-01-01",
                  "purchaseAmount": 10.00
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        String id = result.getResponse().getContentAsString()
                .replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        // Treasury returns a record but it's outside the 6-month window
        when(treasuryClient.findLatestRateOnOrBefore(eq("Canada-Dollar"), any()))
                .thenReturn(Optional.of(new TreasuryFiscalDataClient.TreasuryRateRecordView(
                        "Canada-Dollar", new BigDecimal("1.30"), LocalDate.of(2019, 6, 30))));

        mockMvc.perform(get("/api/v1/purchases/{id}/conversions", id)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Exchange rate unavailable"));
    }
}

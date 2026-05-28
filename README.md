# Purchase Tracker

A small Spring Boot service that:

1. **Stores purchase transactions** (description ≤ 50 chars, transaction date, USD amount rounded to the nearest cent, server-generated UUID).
2. **Retrieves a stored purchase** in a target currency, converting via the **Treasury Reporting Rates of Exchange** API and applying the most recent rate published on or before the purchase date — provided that rate is **no more than 6 months** older than the purchase date.

---

## Quick start

### Option A — local Maven (recommended)

Requirements: **Java 21**, **Maven 3.9+**.

```bash
mvn spring-boot:run
```

The service starts on `http://localhost:8080`.

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- H2 console: <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:mem:purchases`, user `sa`, no password)
- Health: <http://localhost:8080/actuator/health>

### Option B — Docker

```bash
docker build -t purchase-tracker .
docker run --rm -p 8080:8080 purchase-tracker
```

### Run the tests

```bash
mvn verify
```

---

## API

### Create a purchase — `POST /api/v1/purchases`

```bash
curl -s -X POST http://localhost:8080/api/v1/purchases \
  -H 'Content-Type: application/json' \
  -d '{
    "description": "Office supplies",
    "transactionDate": "2025-03-15",
    "purchaseAmount": 49.995
  }'
```

```json
{
  "id": "9c7e9d7b-0c4a-4a4e-9a4e-2f3e8a1c2b7d",
  "description": "Office supplies",
  "transactionDate": "2025-03-15",
  "purchaseAmount": 50.00
}
```

Note `49.995` was rounded HALF_UP to `50.00`. Responds `201 Created` with a `Location` header.

### Retrieve a purchase (original USD) — `GET /api/v1/purchases/{id}`

```bash
curl -s http://localhost:8080/api/v1/purchases/9c7e9d7b-0c4a-4a4e-9a4e-2f3e8a1c2b7d
```

### Retrieve a purchase converted to a target currency — `GET /api/v1/purchases/{id}/conversions?currency=...`

The `currency` parameter takes the Treasury `country_currency_desc` value, e.g. `Canada-Dollar`, `Euro Zone-Euro`, `Mexico-Peso`. URL-encode spaces.

```bash
curl -s "http://localhost:8080/api/v1/purchases/9c7e9d7b-0c4a-4a4e-9a4e-2f3e8a1c2b7d/conversions?currency=Canada-Dollar"
```

```json
{
  "id": "9c7e9d7b-0c4a-4a4e-9a4e-2f3e8a1c2b7d",
  "description": "Office supplies",
  "transactionDate": "2025-03-15",
  "originalAmountUsd": 50.00,
  "targetCurrency": "Canada-Dollar",
  "exchangeRate": 1.337,
  "exchangeRateDate": "2024-12-31",
  "convertedAmount": 66.85
}
```

### Errors

All errors are returned as **RFC 7807 Problem Details** (`application/problem+json`).

| Situation                                                  | Status | `title`                       |
|------------------------------------------------------------|--------|-------------------------------|
| Validation failure (bad amount, missing field, etc.)       | 400    | `Validation failed`           |
| Malformed JSON, unparseable date                           | 400    | `Malformed request body`      |
| Amount rounds to zero                                      | 400    | `Invalid purchase amount`     |
| Purchase ID not found                                      | 404    | `Purchase not found`          |
| No rate within 6 months for the requested currency/date    | 422    | `Exchange rate unavailable`   |
| Treasury API responded with an error                       | 502    | `Upstream service error`      |
| Treasury API unreachable                                   | 503    | `Upstream service unavailable`|

---

## Design notes

### Stack

- **Java 21**, **Spring Boot 3.3.5**, **Maven**
- **H2** in-memory database in dev/test (zero setup); `postgres` profile included for production
- **Spring Data JPA** for persistence
- **Spring's `RestClient`** for the Treasury API
- **Spring Retry** with exponential backoff for transient upstream failures
- **Caffeine** in-process cache for Treasury responses (historical rates are immutable)
- **SpringDoc OpenAPI** for Swagger UI
- **JUnit 5**, **AssertJ**, **Mockito**, **MockMvc**, **`MockRestServiceServer`** for tests — no Docker required to run them

### Money & rounding

- All monetary amounts are `BigDecimal` end-to-end; no `double` anywhere.
- USD amounts are persisted with `scale = 2` (cents).
- Rounding mode is `HALF_UP` (the conventional reading of "rounded to the nearest cent").
- A submitted amount with sub-cent precision is **accepted and rounded** on storage. An amount that rounds to `0.00` is rejected as not positive.

### The 6-month rule

> *"must use a currency conversion rate less than or equal to the purchase date from within the last 6 months"*

Implemented in `ExchangeRateLookupService` (see `LOOKBACK_MONTHS = 6`):

```
rate.recordDate <= purchaseDate
rate.recordDate >= purchaseDate.minusMonths(6)     // boundary inclusive
```

If the latest published rate within that window doesn't exist, the conversion endpoint returns **422 Unprocessable Entity** with a problem detail explaining why. The purchase itself remains stored — only the conversion is rejected.

Why 422 and not 404? The purchase exists and the request is well-formed; the server simply cannot satisfy the conversion for the requested currency. RFC 4918 §11.2 fits this case better than 404 (resource missing) or 400 (malformed request).

### Treasury API integration

- Endpoint: `GET /services/api/fiscal_service/v1/accounting/od/rates_of_exchange`
- Single request per lookup, filtered server-side and sorted descending so the latest eligible rate is the first row.
- Currency identifier passed through verbatim as `country_currency_desc` — matches the upstream API exactly, no extra mapping layer.
- Transient transport failures and 5xx responses retried up to 3 times with exponential backoff (250 ms initial, 2× multiplier).
- 4xx responses surface as `502 Bad Gateway` (we won't retry our way out of an upstream client error).
- Successful responses are cached for 24 hours, max 10 000 entries. Historical rates don't change, so we cache aggressively; misses (empty results) are intentionally not cached so a delayed publication won't poison a permanent miss.

### Configuration profiles

- Default (no profile) — H2 in-memory.
- `postgres` — sets `jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}` and `ddl-auto: validate`. In a real production deployment this would be paired with Flyway/Liquibase for schema migrations; that's intentionally out of scope here.
- `test` — used by `@ActiveProfiles("test")` for the end-to-end test; points `treasury.base-url` at a stub host.

### Assumptions & interpretations

| Brief says…                                              | Decision                                                                          |
|----------------------------------------------------------|-----------------------------------------------------------------------------------|
| "Description must not exceed 50 characters"              | Required and non-blank. Documented as `@NotBlank @Size(max=50)`.                  |
| "Valid positive amount rounded to the nearest cent"      | Accept >0, round HALF_UP, reject if rounded result is 0.                          |
| "Valid date format"                                      | ISO-8601 `YYYY-MM-DD`. Future dates are accepted (they'll simply fail conversion).|
| "Currencies supported by the Treasury API"               | Use `country_currency_desc` (e.g. `Canada-Dollar`) as the API parameter.          |
| "Unique identifier upon storage"                         | Server-side `UUID` (v4 random).                                                   |
| "Within the last 6 months"                               | Calendar months (`LocalDate.minusMonths(6)`), boundary inclusive.                 |

---

## Project layout

```
src/main/java/com/wex/purchases/
├── PurchaseTrackerApplication.java
├── purchase/        # entity, repository, service, controller, DTOs
├── exchange/        # Treasury client, rate lookup with 6-month rule, DTOs
└── common/
    ├── config/      # RestClient, Caffeine cache, OpenAPI
    └── exception/   # domain exceptions + RFC 7807 handler

src/test/java/com/wex/purchases/
├── PurchaseTrackerApplicationTests.java       # context-loads smoke
├── purchase/PurchaseServiceTest.java          # service unit tests
├── purchase/PurchaseControllerTest.java       # @WebMvcTest slice
├── exchange/ExchangeRateLookupServiceTest.java# 6-month rule, including boundary
├── exchange/TreasuryFiscalDataClientTest.java # @RestClientTest with MockRestServiceServer
└── integration/PurchaseEndToEndTest.java      # full HTTP -> JPA -> mocked Treasury
```

---

## What's intentionally out of scope

- **Auth** — would be JWT/OAuth2 + Spring Security in production. Skipped for the take-home.
- **Database migrations** — using JPA `ddl-auto: update` for dev; production would use Flyway/Liquibase.
- **Distributed cache** — Caffeine is single-instance. A horizontally-scaled deployment would use Redis with the same cache keys.
- **Listing all currencies** — the brief asks for conversion to a target currency, not for currency discovery. A `GET /currencies` endpoint that proxies the Treasury "list currencies" call would be a natural follow-up.
- **Metrics dashboards** — actuator `/metrics` is exposed; wiring it into Prometheus/Grafana is environment-specific.

package com.wex.purchases.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central exception translation into RFC 7807 {@code application/problem+json} responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI TYPE_NOT_FOUND = URI.create("urn:problem:purchase-not-found");
    private static final URI TYPE_RATE_UNAVAILABLE = URI.create("urn:problem:exchange-rate-unavailable");
    private static final URI TYPE_VALIDATION = URI.create("urn:problem:validation");
    private static final URI TYPE_BAD_REQUEST = URI.create("urn:problem:bad-request");
    private static final URI TYPE_UPSTREAM = URI.create("urn:problem:upstream-unavailable");

    @ExceptionHandler(PurchaseNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(PurchaseNotFoundException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(TYPE_NOT_FOUND);
        pd.setTitle("Purchase not found");
        pd.setProperty("purchaseId", ex.getId().toString());
        pd.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(ExchangeRateUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleRateUnavailable(ExchangeRateUnavailableException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(TYPE_RATE_UNAVAILABLE);
        pd.setTitle("Exchange rate unavailable");
        pd.setProperty("targetCurrency", ex.getTargetCurrency());
        pd.setProperty("purchaseDate", ex.getPurchaseDate().toString());
        pd.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(pd);
    }

    @ExceptionHandler(InvalidPurchaseAmountException.class)
    public ResponseEntity<ProblemDetail> handleInvalidAmount(InvalidPurchaseAmountException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(TYPE_BAD_REQUEST);
        pd.setTitle("Invalid purchase amount");
        pd.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ProblemDetail> handleUpstreamBadResponse(RestClientResponseException ex, HttpServletRequest req) {
        log.error("Treasury API responded with an error: {} {}", ex.getStatusCode(), ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "The Treasury Reporting Rates of Exchange API responded with an error.");
        pd.setType(TYPE_UPSTREAM);
        pd.setTitle("Upstream service error");
        pd.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(pd);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ProblemDetail> handleUpstreamUnreachable(ResourceAccessException ex, HttpServletRequest req) {
        log.error("Treasury API unreachable: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The Treasury Reporting Rates of Exchange API is currently unreachable. Please try again later.");
        pd.setType(TYPE_UPSTREAM);
        pd.setTitle("Upstream service unavailable");
        pd.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, List<String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        org.springframework.validation.FieldError::getField,
                        LinkedHashMap::new,
                        Collectors.mapping(DefaultMessageSourceResolvable::getDefaultMessage, Collectors.toList())));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        pd.setType(TYPE_VALIDATION);
        pd.setTitle("Validation failed");
        pd.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Malformed JSON or invalid field value (e.g. unparseable date or amount).");
        pd.setType(TYPE_BAD_REQUEST);
        pd.setTitle("Malformed request body");
        return ResponseEntity.badRequest().body(pd);
    }

    /**
     * Catch-all for unanticipated failures: log full stack trace, return a generic problem so we
     * don't leak internal details to clients.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception for {} {}", req.getMethod(), req.getRequestURI(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        pd.setTitle("Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
    }
}

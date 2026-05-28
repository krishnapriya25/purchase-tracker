package com.wex.purchases.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Provides a shared, properly-configured {@link RestClient.Builder} for outbound HTTP calls.
 * Timeouts are sourced from configuration so we can tune them per environment.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder(
            @Value("${treasury.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${treasury.read-timeout-ms:10000}") long readTimeoutMs) {

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings));
    }
}

package com.wex.purchases.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI purchaseTrackerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Purchase Tracker API")
                        .description("Store purchase transactions in USD and retrieve them converted into currencies "
                                + "supported by the Treasury Reporting Rates of Exchange.")
                        .version("v1")
                        .contact(new Contact().name("Engineering").email("engineering@example.com"))
                        .license(new License().name("Apache 2.0")));
    }
}

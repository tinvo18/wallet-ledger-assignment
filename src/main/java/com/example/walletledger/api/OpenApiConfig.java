package com.example.walletledger.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI walletLedgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Wallet Ledger API")
                .version("v1")
                .description("REST API for wallet creation, credit/debit operations, balance and history.")
                .contact(new Contact().name("Wallet Ledger Team")));
    }
}


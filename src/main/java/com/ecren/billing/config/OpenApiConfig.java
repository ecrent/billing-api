package com.ecren.billing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Billing API")
                        .description("Multi-tenant SaaS subscription billing: flat base fee + usage overage, proration on plan change, idempotent payments, append-only ledger.")
                        .version("1.0.0"))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local"),
                        new Server().url("https://billing-api.ecren.dev").description("Production")
                ));
    }
}

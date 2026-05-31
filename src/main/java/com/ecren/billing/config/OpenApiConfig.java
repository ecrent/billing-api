package com.ecren.billing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    private static final Set<String> PUBLIC_PREFIXES = Set.of("/api/v1/tenants", "/api/v1/plans");

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Billing API")
                        .description("Multi-tenant SaaS subscription billing: flat base fee + usage overage, proration on plan change, idempotent payments, append-only ledger.")
                        .version("1.0.0"))
                .servers(List.of(new Server().url("/").description("Current host")));
    }

    @Bean
    public OperationCustomizer tenantIdHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            RequestMapping mapping = handlerMethod.getBeanType().getAnnotation(RequestMapping.class);
            if (mapping != null) {
                for (String path : mapping.value()) {
                    if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
                        return operation;
                    }
                }
            }
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name("X-Tenant-ID")
                    .required(true)
                    .schema(new StringSchema().format("uuid").example("00000000-0000-0000-0000-000000000001"))
                    .description("Tenant identifier"));
            return operation;
        };
    }
}

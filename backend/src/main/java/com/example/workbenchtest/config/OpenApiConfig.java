package com.example.workbenchtest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI applicationOpenApi() {
        return new OpenAPI().info(new Info()
                .title("workbench-test API")
                .version("v1")
                .description("workbench-test application scaffold"));
    }
}

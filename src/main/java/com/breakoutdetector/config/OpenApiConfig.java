package com.breakoutdetector.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI breakoutDetectorOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Breakout Detector API")
                .description("Stock market breakout detection, staircase pattern analysis, and screening API")
                .version("1.0.0")
                .contact(new Contact()
                    .name("Breakout Detector")
                    .email(""))
                .license(new License()
                    .name("MIT")));
    }
}

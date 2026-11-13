package com.brandon.polizas.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacion OpenAPI publicada en {@code /swagger-ui.html}.
 * Declara la api-key para que el boton "Authorize" de Swagger UI la envie en cada peticion.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_API_KEY = "apiKey";

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Gestion de Polizas")
                        .version("1.0.0")
                        .description("Polizas de arrendamiento individuales y colectivas y sus riesgos. "
                                + "Pulse Authorize e ingrese la api-key (por defecto 123456)."))
                .components(new Components().addSecuritySchemes(ESQUEMA_API_KEY, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("x-api-key")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_API_KEY));
    }
}

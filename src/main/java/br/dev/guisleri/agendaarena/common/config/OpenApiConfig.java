package br.dev.guisleri.agendaarena.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do OpenAPI e o esquema Bearer JWT (botão "Authorize" do Swagger UI: cole o
 * accessToken devolvido por POST /auth/login).
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI agendaArenaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Agenda Arena API")
                        .description("Agendamento de quadras e campos esportivos")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(
                        BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

}

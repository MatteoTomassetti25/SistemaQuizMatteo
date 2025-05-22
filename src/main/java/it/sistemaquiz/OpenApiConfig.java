package it.sistemaquiz;

import org.springframework.context.annotation.Bean;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Configuration;

// Nessuna modifica richiesta qui se le annotazioni sono usate nei controller
// e questo bean è già configurato per scansionare i controller.
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Sistema Quiz POO ")
                        .version("1.0")
                        .description("API per Sistema Quiz per Corso di Programmazione Orientata agli Oggetti. " +
                                     "Include autenticazione e funzionalità di esecuzione test."))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Autenticazione JWT. Il token viene gestito tramite cookie HTTP-only 'jwtToken' " +
                                                     "impostato al login e letto automaticamente dal server per le richieste protette.")));
    }
}
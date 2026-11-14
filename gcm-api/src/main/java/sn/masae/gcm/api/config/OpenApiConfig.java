package sn.masae.gcm.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gcmOpenApi() {
        return new OpenAPI()
                .info(new Info().title("GCM MASAE — API Comptabilité des matières")
                        .description("API REST de la plateforme de comptabilité des matières (Décret n°2018-842, Instruction n°0001/MFB/DGB). "
                                + "Les points d'accès /api/interop/v1 sont destinés aux systèmes tiers (SIGIF, SYSBUDGEP) via l'en-tête X-API-Key.")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("apiKey", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-Key")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}

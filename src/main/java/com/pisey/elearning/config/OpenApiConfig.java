package com.pisey.elearning.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI "Authorize" logs in through Keycloak (authorization code + PKCE, client elearning-frontend).
 */
@Configuration
@EnableConfigurationProperties(KeycloakProperties.class)
public class OpenApiConfig {

    private static final String SCHEME = "keycloak";

    @Bean
    public OpenAPI openAPI(KeycloakProperties keycloak) {
        OAuthFlow authorizationCode = new OAuthFlow()
                .authorizationUrl(keycloak.openIdConnectUrl() + "/auth")
                .tokenUrl(keycloak.openIdConnectUrl() + "/token")
                .scopes(new Scopes()
                        .addString("openid", "OpenID Connect")
                        .addString("profile", "Name")
                        .addString("email", "Email address"));

        return new OpenAPI()
                .info(new Info().title("E-Learning API").version("v1"))
                .components(new Components().addSecuritySchemes(SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(new OAuthFlows().authorizationCode(authorizationCode))))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}

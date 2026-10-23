package com.pisey.elearning.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * RestClient for the Keycloak Admin API. The interceptor fetches a client-credentials token for
 * registration {@code keycloak-admin} and caches it until it expires.
 */
@Configuration
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakAdminClientConfig {

    public static final String REGISTRATION_ID = "keycloak-admin";

    /**
     * Fixed principal so one service-account token is shared by all requests instead of being cached per end user.
     */
    private static final Authentication SERVICE_PRINCIPAL = new AnonymousAuthenticationToken(
            REGISTRATION_ID, REGISTRATION_ID, AuthorityUtils.createAuthorityList("ROLE_SERVICE"));

    @Bean
    public RestClient keycloakAdminRestClient(RestClient.Builder builder,
                                              KeycloakProperties keycloak,
                                              ClientRegistrationRepository clientRegistrations,
                                              OAuth2AuthorizedClientService authorizedClientService) {
        var clientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrations, authorizedClientService);
        var interceptor = new OAuth2ClientHttpRequestInterceptor(clientManager);
        interceptor.setClientRegistrationIdResolver(request -> REGISTRATION_ID);
        interceptor.setPrincipalResolver(request -> SERVICE_PRINCIPAL);
        // Drop a rejected token so the next call fetches a fresh one
        interceptor.setAuthorizationFailureHandler(
                OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(authorizedClientService));

        return builder
                .baseUrl(keycloak.adminRealmUrl())
                .requestInterceptor(interceptor)
                .build();
    }

    /** Plain client (no token) for the realm's token endpoint, used to verify passwords. */
    @Bean
    public RestClient keycloakTokenRestClient(RestClient.Builder builder, KeycloakProperties keycloak) {
        return builder
                .baseUrl(keycloak.openIdConnectUrl())
                .build();
    }
}

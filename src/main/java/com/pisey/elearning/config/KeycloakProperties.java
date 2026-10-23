package com.pisey.elearning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code keycloak.*} properties: where the Keycloak server and realm live.
 */
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(String serverUrl, String realm) {

    /** e.g. http://localhost:8081/realms/elearning */
    public String realmUrl() {
        return serverUrl + "/realms/" + realm;
    }

    /** e.g. http://localhost:8081/admin/realms/elearning */
    public String adminRealmUrl() {
        return serverUrl + "/admin/realms/" + realm;
    }

    /** e.g. http://localhost:8081/realms/elearning/protocol/openid-connect */
    public String openIdConnectUrl() {
        return realmUrl() + "/protocol/openid-connect";
    }
}

package com.lms.tenancy.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lms.keycloak")
record KeycloakProperties(String baseUrl, String realm, String adminClientId, String adminClientSecret) {
}

package com.back.coach.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void openApi_hasBearerJwtSecurityScheme() {
        OpenAPI openAPI = new OpenApiConfig().openAPI();

        SecurityScheme scheme = openAPI.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.BEARER_AUTH_SCHEME);

        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(openAPI.getSecurity())
                .anySatisfy(requirement ->
                        assertThat(requirement).containsKey(OpenApiConfig.BEARER_AUTH_SCHEME));
    }
}

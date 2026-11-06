package com.lms.shared.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document is the contract the frontend's TypeScript types are generated from,
 * so it has to describe the JSON exactly.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    /**
     * {@link ApiNullable} on a record component → OpenAPI 3.1 {@code type: [T, "null"]},
     * which becomes {@code T | null} in TypeScript. Everything else is non-null.
     */
    @Bean
    PropertyCustomizer nullableProperties() {
        return (property, type) -> {
            if (type.getCtxAnnotations() != null) {
                for (var annotation : type.getCtxAnnotations()) {
                    if (annotation.annotationType() == ApiNullable.class) {
                        if (property.get$ref() != null) {
                            // A reference cannot carry a type of its own: "this object, or null".
                            Schema<Object> ref = new Schema<>();
                            ref.set$ref(property.get$ref());
                            Schema<Object> nul = new Schema<>();
                            nul.addType("null");
                            Schema<Object> either = new Schema<>();
                            either.setOneOf(new ArrayList<>(java.util.List.<Schema>of(ref, nul)));
                            return either;
                        }
                        if (property.getTypes() == null || property.getTypes().isEmpty()) {
                            if (property.getType() != null) {
                                property.addType(property.getType());
                            }
                        }
                        property.addType("null");
                    }
                }
            }
            return property;
        };
    }

    @Bean
    OpenApiCustomizer lmsContract() {
        return api -> {
            api.info(new Info().title("LMS API").version("v1")
                    .description("B2B multi-tenant LMS. Errors are RFC 9457 problem+json with `code` and `traceId`."));
            if (api.getComponents() == null) {
                api.setComponents(new Components());
            }
            api.getComponents().addSecuritySchemes("bearer",
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
            api.addSecurityItem(new SecurityRequirement().addList("bearer"));

            // Response records always serialize every component (null included), so every
            // property is present: mark them required, or generated types would make each one
            // optional. Request schemas keep the "required" springdoc derives from validation.
            Map<String, Schema> schemas = api.getComponents().getSchemas();
            if (schemas == null) {
                return;
            }
            schemas.forEach((name, schema) -> {
                if (!name.endsWith("Request") && !name.endsWith("Input") && !name.equals("OutlineEntry")
                        && schema.getProperties() != null) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
            });
        };
    }
}

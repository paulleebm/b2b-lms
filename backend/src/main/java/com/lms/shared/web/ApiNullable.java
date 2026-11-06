package com.lms.shared.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * "This response field can be null." Becomes {@code T | null} in the generated TypeScript types
 * (see {@link OpenApiConfig}). Fields without it are never null in responses.
 *
 * <p>A declaration annotation on purpose: JSpecify's {@code @Nullable} is TYPE_USE only, and
 * springdoc reads property (declaration) annotations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface ApiNullable {
}

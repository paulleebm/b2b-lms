package com.lms.shared.web;

import org.springframework.http.HttpStatus;

/**
 * A business error with a stable machine-readable {@code code} (e.g. TENANT_SLUG_TAKEN) and a
 * human message. Rendered as RFC 9457 Problem Details by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** 404 is used for "exists but belongs to another tenant" too, so existence never leaks. */
    public static ApiException notFound(String code, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, code, detail);
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiException forbidden(String code, String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, code, detail);
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }
}

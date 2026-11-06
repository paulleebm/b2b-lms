package com.lms.shared.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * A business error with a stable machine-readable {@code code} (e.g. TENANT_SLUG_TAKEN) and a
 * human message. Rendered as RFC 9457 Problem Details by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private List<?> errors = List.of();

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

    /** Details rendered as the problem's {@code errors} member (e.g. why a course cannot be published). */
    public List<?> errors() {
        return errors;
    }

    public ApiException withErrors(List<?> errors) {
        this.errors = List.copyOf(errors);
        return this;
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

    /** 422: well-formed request, but the resource is not in a state that allows it. */
    public static ApiException unprocessable(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, detail);
    }

    /** 412: the client's If-Match no longer matches — someone else changed it first. */
    public static ApiException preconditionFailed(String code, String detail) {
        return new ApiException(HttpStatus.PRECONDITION_FAILED, code, detail);
    }

    /** 428: this write requires If-Match, so a lost update cannot happen by accident. */
    public static ApiException preconditionRequired(String code, String detail) {
        return new ApiException(HttpStatus.PRECONDITION_REQUIRED, code, detail);
    }
}

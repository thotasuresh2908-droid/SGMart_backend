package com.sgmart.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns every error into the same JSON shape (see {@link ApiError}). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getCode(), ex.getMessage(), ex.getField()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        FieldError first = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = first != null ? first.getDefaultMessage() : "Please check the details and try again.";
        String field = first != null ? first.getField() : null;
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", message, field));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("BAD_REQUEST", "The request could not be read. Please check the values sent."));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("CHANGED", "This was just updated by someone else. Refresh and try again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("CONFLICT", "This conflicts with data that already exists."));
    }

    /**
     * Everything not handled above.
     *  - Permission errors (from @PreAuthorize) are passed on so Spring Security answers 401/403.
     *  - Spring's own request errors (wrong method, wrong content type, unknown path ...) keep
     *    their 4xx status.
     *  - Anything else is a bug: log it with its stack trace and answer a clean 500 JSON.
     * Without this, a hidden server error reached the browser as a confusing 403 for signed-in
     * shoppers and 401 for guests.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex, HttpServletRequest request) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        if (ex instanceof ErrorResponse spring) {
            HttpStatusCode status = spring.getStatusCode();
            if (status.is5xxServerError()) {
                log.error("{} {} failed", request.getMethod(), request.getRequestURI(), ex);
                return ResponseEntity.status(status).body(serverError());
            }
            String code;
            String message;
            switch (status.value()) {
                case 404 -> {
                    code = "NOT_FOUND";
                    message = "Not found.";
                }
                case 405 -> {
                    code = "METHOD_NOT_ALLOWED";
                    message = "This action isn't allowed here.";
                }
                case 415 -> {
                    code = "UNSUPPORTED_MEDIA_TYPE";
                    message = "The request type isn't supported.";
                }
                default -> {
                    code = "BAD_REQUEST";
                    message = "The request could not be processed. Please check the values sent.";
                }
            }
            return ResponseEntity.status(status).headers(spring.getHeaders()).body(ApiError.of(code, message));
        }
        log.error("{} {} failed", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(serverError());
    }

    private static ApiError serverError() {
        return ApiError.of("SERVER_ERROR", "Something went wrong on our side. Please try again.");
    }
}

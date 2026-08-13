package com.example.inventory.auth;

import com.example.inventory.shared.ApiError;
import com.example.inventory.shared.ApiErrorCode;
import com.example.inventory.shared.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthenticationRateLimitException.class)
    ResponseEntity<ApiError> handleRateLimit(AuthenticationRateLimitException exception,
                                             HttpServletRequest request) {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                ApiErrorCode.RATE_LIMIT_EXCEEDED, "Too many authentication attempts",
                request.getRequestURI(), CorrelationIdFilter.from(request), Map.of());
        return ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER,
                        Long.toString(exception.retryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(InvalidAuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                ApiErrorCode.AUTHENTICATION_FAILED, "Authentication failed",
                request.getRequestURI(), CorrelationIdFilter.from(request), Map.of());
        return ResponseEntity.status(status).body(body);
    }
}

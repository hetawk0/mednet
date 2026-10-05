package com.mednet.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.auth.api.AccountAuthController.AuthRequestException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthRequest(
            AuthRequestException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return error(status, status.name(), message, List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::toDetail)
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                "Request body is missing or invalid",
                List.of(),
                request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "MISSING_PARAMETER",
                "A required request parameter is missing",
                List.of(new ApiErrorDetail(exception.getParameterName(), "Required parameter is missing")),
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidParameter(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "A request parameter has an invalid value",
                List.of(new ApiErrorDetail(exception.getName(), "Invalid parameter value")),
                request);
    }

    private static ApiErrorDetail toDetail(FieldError error) {
        return new ApiErrorDetail(error.getField(), error.getDefaultMessage() == null
                ? "Invalid value"
                : error.getDefaultMessage());
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorDetail> details,
            HttpServletRequest request) {
        String requestId = (String) request.getAttribute(ApiRequestIdFilter.ATTRIBUTE_NAME);
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                false,
                new ApiError(code, message, details),
                message,
                new ApiMeta(requestId)));
    }

    public record ApiErrorResponse(boolean success, ApiError error, String detail, ApiMeta meta) {
    }

    public record ApiError(String code, String message, List<ApiErrorDetail> details) {
    }

    public record ApiErrorDetail(String field, String message) {
    }

    public record ApiMeta(String requestId) {
    }
}

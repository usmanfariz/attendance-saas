package com.attendance.saas.exception;

import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.ApiResponse.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

/**
 * Turns every exception into the standard {@link ApiResponse} error envelope so
 * clients never see a stack trace or a Spring default body.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("API error {} on {}", code, request.getRequestURI(), ex);
        } else {
            log.debug("API error {} on {}: {}", code, request.getRequestURI(), ex.getMessage());
        }
        return build(code.getStatus(), ex.getMessage(), code, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Validasi gagal", ErrorCode.VALIDATION_ERROR, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Validasi gagal", ErrorCode.VALIDATION_ERROR, errors);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex) {
        log.debug("Malformed request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Format request tidak valid", ErrorCode.BAD_REQUEST, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Email atau password salah", ErrorCode.INVALID_CREDENTIALS, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Autentikasi gagal", ErrorCode.UNAUTHORIZED, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN,
                "Anda tidak memiliki hak akses untuk operasi ini", ErrorCode.FORBIDDEN, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT,
                "Data melanggar batasan integritas (kemungkinan duplikat)", ErrorCode.CONFLICT, null);
    }

    @ExceptionHandler({NoHandlerFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        return build(HttpStatus.NOT_FOUND, "Endpoint tidak ditemukan", ErrorCode.NOT_FOUND, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Terjadi kesalahan pada server", ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status,
                                                    String message,
                                                    ErrorCode code,
                                                    List<ApiError> errors) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, code.name(), errors));
    }
}

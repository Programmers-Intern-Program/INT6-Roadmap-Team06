package com.back.coach.global.exception;

import com.back.coach.global.logging.TraceIdFilter;
import com.back.coach.global.response.ApiErrorResponse;
import com.back.coach.global.response.FieldErrorDetail;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleServiceException(ServiceException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("ServiceException: code={}, message={}", code, ex.getMessage());
        return build(code, ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return validationResponse(extractFieldErrors(ex));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBind(BindException ex) {
        return validationResponse(extractFieldErrors(ex));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex) {
        List<FieldErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDetail(lastSegment(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return validationResponse(errors);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex) {
        List<FieldErrorDetail> errors = null;
        if (ex instanceof MethodArgumentTypeMismatchException e) {
            errors = List.of(new FieldErrorDetail(e.getName(), "invalid"));
        } else if (ex instanceof MissingServletRequestParameterException e) {
            errors = List.of(new FieldErrorDetail(e.getParameterName(), "required"));
        }
        return validationResponse(errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        log.error("Unexpected error", ex);
        return build(code, code.getDefaultMessage(), null);
    }

    private ResponseEntity<ApiErrorResponse> validationResponse(List<FieldErrorDetail> errors) {
        ErrorCode code = ErrorCode.INVALID_INPUT;
        ApiErrorResponse body = ApiErrorResponse
                .withFieldErrors(code, code.getDefaultMessage(), errors)
                .withTraceId(MDC.get(TraceIdFilter.MDC_KEY));
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode code, String message, List<FieldErrorDetail> fieldErrors) {
        ApiErrorResponse body = (fieldErrors == null || fieldErrors.isEmpty())
                ? ApiErrorResponse.of(code, message)
                : ApiErrorResponse.withFieldErrors(code, message, fieldErrors);
        return ResponseEntity
                .status(code.getStatus())
                .body(body.withTraceId(MDC.get(TraceIdFilter.MDC_KEY)));
    }

    private List<FieldErrorDetail> extractFieldErrors(BindException ex) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorDetail(fe.getField(), fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"));
        }
        return errors;
    }

    private String lastSegment(String path) {
        int i = path.lastIndexOf('.');
        return (i >= 0 && i < path.length() - 1) ? path.substring(i + 1) : path;
    }
}

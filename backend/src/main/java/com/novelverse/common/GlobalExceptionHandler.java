package com.novelverse.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiErrorResponse> handleApiException(
      ApiException exception, HttpServletRequest request) {
    return response(
        exception.getStatus(),
        exception.getCode(),
        exception.getMessage(),
        request.getRequestURI(),
        List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<ApiErrorResponse.FieldIssue> fields =
        exception.getBindingResult().getFieldErrors().stream()
            .map(this::fieldIssue)
            .collect(
                Collectors.toMap(
                    ApiErrorResponse.FieldIssue::field, issue -> issue, (first, ignored) -> first))
            .values()
            .stream()
            .toList();
    return response(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_FAILED",
        "One or more fields are invalid.",
        request.getRequestURI(),
        fields);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleConstraintValidation(
      ConstraintViolationException exception, HttpServletRequest request) {
    List<ApiErrorResponse.FieldIssue> fields =
        exception.getConstraintViolations().stream()
            .map(
                violation ->
                    new ApiErrorResponse.FieldIssue(
                        violation.getPropertyPath().toString(), violation.getMessage()))
            .toList();
    return response(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_FAILED",
        "One or more fields are invalid.",
        request.getRequestURI(),
        fields);
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleConflict(
      org.springframework.dao.DataIntegrityViolationException exception,
      HttpServletRequest request) {
    return response(
        HttpStatus.CONFLICT,
        "RESOURCE_CONFLICT",
        "The requested change conflicts with existing data.",
        request.getRequestURI(),
        List.of());
  }

  private ApiErrorResponse.FieldIssue fieldIssue(FieldError error) {
    return new ApiErrorResponse.FieldIssue(error.getField(), error.getDefaultMessage());
  }

  private ResponseEntity<ApiErrorResponse> response(
      HttpStatus status,
      String code,
      String message,
      String path,
      List<ApiErrorResponse.FieldIssue> fieldErrors) {
    return ResponseEntity.status(status)
        .body(
            new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                path,
                UUID.randomUUID().toString(),
                fieldErrors));
  }
}

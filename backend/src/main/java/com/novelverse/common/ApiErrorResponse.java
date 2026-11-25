package com.novelverse.common;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    String traceId,
    List<FieldIssue> fieldErrors) {

  public record FieldIssue(String field, String message) {}
}

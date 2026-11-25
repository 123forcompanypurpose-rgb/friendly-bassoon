package com.novelverse.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorWriter {

  private final ObjectMapper objectMapper;

  public ApiErrorWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String message,
      String path,
      List<ApiErrorResponse.FieldIssue> fieldErrors)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(),
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

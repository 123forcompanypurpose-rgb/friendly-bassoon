package com.novelverse.health.web;

import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

  private final HealthEndpoint healthEndpoint;

  public HealthController(HealthEndpoint healthEndpoint) {
    this.healthEndpoint = healthEndpoint;
  }

  @GetMapping
  public ResponseEntity<HealthResponse> getHealth() {
    HealthComponent health = healthEndpoint.health();
    String status = health.getStatus().getCode();
    HttpStatus httpStatus = "UP".equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(httpStatus).body(new HealthResponse(status));
  }

  public record HealthResponse(String status) {}
}

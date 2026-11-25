package com.novelverse.health.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.novelverse.common.ApiErrorWriter;
import com.novelverse.security.IpHashService;
import com.novelverse.security.JwtService;
import com.novelverse.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class HealthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private HealthEndpoint healthEndpoint;

  @MockBean private ApiErrorWriter apiErrorWriter;

  @MockBean private IpHashService ipHashService;

  @MockBean private JwtService jwtService;

  @MockBean private UserRepository userRepository;

  @Test
  void reportsHealthyBackend() throws Exception {
    given(healthEndpoint.health()).willReturn(Health.up().build());

    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void reportsUnavailableDatabase() throws Exception {
    given(healthEndpoint.health()).willReturn(Health.down().build());

    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"));
  }
}

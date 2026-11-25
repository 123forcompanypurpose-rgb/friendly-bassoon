package com.novelverse.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuthIntegrationTest {

  private static final String CSRF_HEADER = "X-Requested-With";
  private static final String CSRF_VALUE = "novelverse";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("novelverse")
          .withUsername("novelverse")
          .withPassword("novelverse");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("DB_URL", POSTGRES::getJdbcUrl);
    registry.add("DB_USER", POSTGRES::getUsername);
    registry.add("DB_PASSWORD", POSTGRES::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private JavaMailSender mailSender;

  @Test
  void migratesDatabaseAndCompletesVerificationLoginRotationRecoveryAndLockout() throws Exception {
    doNothing().when(mailSender).send(any(SimpleMailMessage.class));

    assertMigrationsAndPublicAccess();
    mockMvc
        .perform(get("/api/v1/users/not-a-real-user"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"bad\",\"username\":\"x\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String email = "reader-" + suffix + "@example.test";
    String username = "reader_" + suffix;
    String password = "NovelVerse-Secret-94!";
    register(email, username, password);

    String expiredVerificationToken = lastMailToken();
    mockMvc
        .perform(
            post("/api/v1/auth/resend-verification")
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email)))
        .andExpect(status().isOk());
    String verificationToken = lastMailToken();
    mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("token", expiredVerificationToken)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("token", verificationToken)))
        .andExpect(status().isNoContent());

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .header(CSRF_HEADER, CSRF_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"identifier\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.username").value(username))
            .andReturn();
    String oldRefreshToken = cookieValue(login, "refresh_token");
    Cookie accessCookie = new Cookie("access_token", cookieValue(login, "access_token"));
    org.junit.jupiter.api.Assertions.assertTrue(
        cookieHeader(login, "access_token").toLowerCase().contains("httponly"));
    org.junit.jupiter.api.Assertions.assertTrue(
        cookieHeader(login, "access_token").contains("Path=/;"));
    org.junit.jupiter.api.Assertions.assertTrue(
        cookieHeader(login, "refresh_token").contains("Path=/;"));
    mockMvc
        .perform(get("/api/v1/me").cookie(accessCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email));
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/me")
                .cookie(accessCookie)
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"displayName\":\"Updated Reader\",\"bio\":\"Avid reader\","
                        + "\"socialLinks\":{\"website\":\"https://example.test/reader\"},"
                        + "\"birthDate\":\"1990-01-01\",\"showMature\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Updated Reader"))
        .andExpect(jsonPath("$.showMature").value(true));
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/me")
                .cookie(accessCookie)
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PROFILE_DISPLAY_NAME_REQUIRED"));
    mockMvc
        .perform(
            post("/api/v1/me/avatar/presign")
                .cookie(accessCookie)
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/png\",\"contentLength\":100}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AVATAR_STORAGE_UNAVAILABLE"));
    mockMvc
        .perform(
            post("/api/v1/users/" + username + "/follow")
                .cookie(accessCookie)
                .header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USER_SELF_ACTION"));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"writer-"
                        + suffix
                        + "@example.test\",\"username\":\"writer_"
                        + suffix
                        + "\",\"password\":\"NovelVerse-Secret-94!\",\"acceptedTerms\":true}"))
        .andExpect(status().isAccepted());
    String writerUsername = "writer_" + suffix;
    mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("token", lastMailToken())))
        .andExpect(status().isNoContent());
    MvcResult writerLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .header(CSRF_HEADER, CSRF_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"identifier\":\"writer-"
                            + suffix
                            + "@example.test\",\"password\":\"NovelVerse-Secret-94!\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String writerRefreshToken = cookieValue(writerLogin, "refresh_token");
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header(CSRF_HEADER, CSRF_VALUE)
                .cookie(new Cookie("refresh_token", writerRefreshToken)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .header(CSRF_HEADER, CSRF_VALUE)
                .cookie(new Cookie("refresh_token", writerRefreshToken)))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v1/users/" + writerUsername + "/follow")
                .cookie(accessCookie)
                .header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/v1/users/" + username))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(username))
        .andExpect(jsonPath("$.email").doesNotExist());
    mockMvc
        .perform(
            post("/api/v1/users/" + writerUsername + "/block")
                .cookie(accessCookie)
                .header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isNoContent());
    Long remainingFollows =
        jdbcTemplate.queryForObject(
            "select count(*) from follows where follower_id = (select id from users where username = ?)",
            Long.class,
            username);
    org.junit.jupiter.api.Assertions.assertEquals(0L, remainingFollows);
    mockMvc
        .perform(
            post("/api/v1/users/" + writerUsername + "/follow")
                .cookie(accessCookie)
                .header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USER_INTERACTION_BLOCKED"));
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/users/" + writerUsername + "/block")
                .cookie(accessCookie)
                .header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isNoContent());

    MvcResult refresh =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .header(CSRF_HEADER, CSRF_VALUE)
                    .cookie(new Cookie("refresh_token", oldRefreshToken)))
            .andExpect(status().isOk())
            .andReturn();
    String rotatedRefreshToken = cookieValue(refresh, "refresh_token");
    org.junit.jupiter.api.Assertions.assertNotEquals(oldRefreshToken, rotatedRefreshToken);

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .header(CSRF_HEADER, CSRF_VALUE)
                .cookie(new Cookie("refresh_token", oldRefreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REFRESH_REUSE"));
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .header(CSRF_HEADER, CSRF_VALUE)
                .cookie(new Cookie("refresh_token", rotatedRefreshToken)))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email)))
        .andExpect(status().isAccepted());
    String resetToken = lastMailToken();
    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"token\":\"" + resetToken + "\",\"newPassword\":\"NovelVerse-Secret-95!\"}"))
        .andExpect(status().isNoContent());
    MvcResult changeLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .header(CSRF_HEADER, CSRF_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"identifier\":\""
                            + email
                            + "\",\"password\":\"NovelVerse-Secret-95!\"}"))
            .andExpect(status().isOk())
            .andReturn();
    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .cookie(new Cookie("access_token", cookieValue(changeLogin, "access_token")))
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\"wrong-password\","
                        + "\"newPassword\":\"NovelVerse-Secret-96!\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH_CURRENT_PASSWORD_INVALID"));
    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .cookie(new Cookie("access_token", cookieValue(changeLogin, "access_token")))
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\"NovelVerse-Secret-95!\","
                        + "\"newPassword\":\"NovelVerse-Secret-96!\"}"))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .header(CSRF_HEADER, CSRF_VALUE)
                .cookie(new Cookie("refresh_token", cookieValue(changeLogin, "refresh_token"))))
        .andExpect(status().isUnauthorized());

    String remoteAddress = "198.51.100.41";
    for (int attempt = 0; attempt < 5; attempt++) {
      mockMvc
          .perform(
              withRemoteAddress(
                  post("/api/v1/auth/login")
                      .header(CSRF_HEADER, CSRF_VALUE)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          "{\"identifier\":\"" + username + "\",\"password\":\"wrong-password\"}"),
                  remoteAddress))
          .andExpect(status().isUnauthorized());
    }
    Integer lockedAttempts =
        jdbcTemplate.queryForObject(
            "select count(*) from login_attempts where failed_attempts >= 5 and locked_until > now()",
            Integer.class);
    org.junit.jupiter.api.Assertions.assertEquals(1, lockedAttempts);

    MvcResult finalLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .header(CSRF_HEADER, CSRF_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"identifier\":\""
                            + email
                            + "\",\"password\":\"NovelVerse-Secret-96!\"}"))
            .andExpect(status().isOk())
            .andReturn();
    mockMvc
        .perform(
            post("/api/v1/auth/logout-all")
                .cookie(new Cookie("access_token", cookieValue(finalLogin, "access_token")))
                .header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .header(CSRF_HEADER, CSRF_VALUE)
                .cookie(new Cookie("refresh_token", cookieValue(finalLogin, "refresh_token"))))
        .andExpect(status().isUnauthorized());
    String userId =
        objectMapper
            .readTree(finalLogin.getResponse().getContentAsString())
            .path("user")
            .path("id")
            .asText();
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/me")
                .cookie(new Cookie("access_token", cookieValue(finalLogin, "access_token")))
                .header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isNoContent());
    String deletedStatus =
        jdbcTemplate.queryForObject(
            "select status from users where id = ?", String.class, Long.valueOf(userId));
    org.junit.jupiter.api.Assertions.assertEquals("DELETED", deletedStatus);
    mockMvc
        .perform(
            get("/api/v1/me")
                .cookie(new Cookie("access_token", cookieValue(finalLogin, "access_token"))))
        .andExpect(status().isUnauthorized());
  }

  private void assertMigrationsAndPublicAccess() throws Exception {
    Integer appliedMigrations =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where success", Integer.class);
    Integer seededGenres =
        jdbcTemplate.queryForObject("select count(*) from genres", Integer.class);
    org.junit.jupiter.api.Assertions.assertEquals(5, appliedMigrations);
    org.junit.jupiter.api.Assertions.assertEquals(21, seededGenres);

    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mockMvc
        .perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    mockMvc
        .perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_HEADER_REQUIRED"));
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  private void register(String email, String username, String password) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\""
                        + email
                        + "\",\"username\":\""
                        + username
                        + "\",\"password\":\""
                        + password
                        + "\",\"acceptedTerms\":true}"))
        .andExpect(status().isAccepted());
  }

  private String lastMailToken() {
    org.mockito.ArgumentCaptor<SimpleMailMessage> messages =
        org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
    org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.atLeastOnce())
        .send(messages.capture());
    SimpleMailMessage latest = messages.getValue();
    Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(latest.getText());
    org.junit.jupiter.api.Assertions.assertTrue(matcher.find());
    return matcher.group(1);
  }

  private String json(String key, String value) throws Exception {
    return objectMapper.writeValueAsString(java.util.Map.of(key, value));
  }

  private MockHttpServletRequestBuilder withRemoteAddress(
      MockHttpServletRequestBuilder builder, String remoteAddress) {
    return builder.with(
        request -> {
          request.setRemoteAddr(remoteAddress);
          return request;
        });
  }

  private String cookieValue(MvcResult result, String name) {
    String header = cookieHeader(result, name);
    return header.substring((name + "=").length(), header.indexOf(';'));
  }

  private String cookieHeader(MvcResult result, String name) {
    return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
        .filter(header -> header.startsWith(name + "="))
        .findFirst()
        .orElseThrow();
  }
}

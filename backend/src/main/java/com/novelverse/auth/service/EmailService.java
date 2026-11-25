package com.novelverse.auth.service;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class EmailService {

  private final JavaMailSender mailSender;
  private final String sender;
  private final URI webOrigin;

  public EmailService(
      JavaMailSender mailSender,
      @Value("${app.mail-from:noreply@novelverse.local}") String sender,
      @Value("${app.web-origin}") String webOrigin) {
    this.mailSender = mailSender;
    this.sender = sender;
    this.webOrigin = URI.create(webOrigin);
  }

  public void sendVerification(String email, String rawToken) {
    send(
        email,
        "Verify your NovelVerse email",
        "Verify your email address using this link (valid for 24 hours):\n\n"
            + tokenLink("/verify-email", rawToken));
  }

  public void sendPasswordReset(String email, String rawToken) {
    send(
        email,
        "Reset your NovelVerse password",
        "Reset your password using this link (valid for 1 hour):\n\n"
            + tokenLink("/reset-password", rawToken));
  }

  private void send(String recipient, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(sender);
    message.setTo(recipient);
    message.setSubject(subject);
    message.setText(body);
    mailSender.send(message);
  }

  private String tokenLink(String path, String token) {
    return UriComponentsBuilder.fromUri(webOrigin)
        .path(path)
        .queryParam("token", token)
        .build()
        .toUriString();
  }
}

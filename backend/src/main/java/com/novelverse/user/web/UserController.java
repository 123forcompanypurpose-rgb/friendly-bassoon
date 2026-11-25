package com.novelverse.user.web;

import com.novelverse.auth.service.AuthCookieService;
import com.novelverse.security.UserPrincipal;
import com.novelverse.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Users")
public class UserController {

  private final UserService userService;
  private final AuthCookieService cookies;

  public UserController(UserService userService, AuthCookieService cookies) {
    this.userService = userService;
    this.cookies = cookies;
  }

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Get the current user's private profile")
  public ProfileResponses.MyProfile getMe(@AuthenticationPrincipal UserPrincipal principal) {
    return userService.getMyProfile(principal.id());
  }

  @PatchMapping("/me")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Update the current user's profile")
  public ProfileResponses.MyProfile updateMe(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody ProfileRequests.UpdateProfileRequest request) {
    return userService.updateMyProfile(principal.id(), request);
  }

  @DeleteMapping("/me")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Soft-delete the current account and anonymize its profile")
  public ResponseEntity<Void> deleteMe(
      @AuthenticationPrincipal UserPrincipal principal, HttpServletResponse response) {
    userService.deleteMyAccount(principal.id());
    cookies.clearSessionCookies(response);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/users/{username}")
  @Operation(summary = "Get a public user profile")
  public ProfileResponses.PublicProfile getPublicProfile(@PathVariable String username) {
    return userService.getPublicProfile(username);
  }

  @PostMapping("/users/{username}/follow")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Follow a user")
  public ResponseEntity<Void> follow(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String username) {
    userService.follow(principal.id(), username);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @DeleteMapping("/users/{username}/follow")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Unfollow a user")
  public ResponseEntity<Void> unfollow(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String username) {
    userService.unfollow(principal.id(), username);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/users/{username}/block")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Block a user and remove mutual follows")
  public ResponseEntity<Void> block(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String username) {
    userService.block(principal.id(), username);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/users/{username}/block")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Unblock a user")
  public ResponseEntity<Void> unblock(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String username) {
    userService.unblock(principal.id(), username);
    return ResponseEntity.noContent().build();
  }
}

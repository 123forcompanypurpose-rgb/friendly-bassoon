package com.novelverse.content.web;

import com.novelverse.content.service.HomeService;
import com.novelverse.content.web.ContentResponses.HomeResponse;
import com.novelverse.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Discovery")
public class HomeController {

  private final HomeService homeService;

  public HomeController(HomeService homeService) {
    this.homeService = homeService;
  }

  @GetMapping("/home")
  @Operation(summary = "Get public story sections and active genres for the home page")
  public HomeResponse home(@AuthenticationPrincipal UserPrincipal principal) {
    return homeService.getHome(principal == null ? null : principal.id());
  }
}

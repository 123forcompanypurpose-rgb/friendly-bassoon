package com.novelverse.security;

import com.novelverse.user.domain.UserRole;

public record UserPrincipal(Long id, String username, UserRole role) {}

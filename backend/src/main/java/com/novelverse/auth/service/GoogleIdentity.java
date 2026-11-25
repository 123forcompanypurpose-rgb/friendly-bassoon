package com.novelverse.auth.service;

public record GoogleIdentity(String subject, String email, String name, boolean emailVerified) {}

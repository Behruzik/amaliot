package com.example.amaliyot.security;

import com.example.amaliyot.domain.Role;

public record AuthPrincipal(Long userId, Role role) {}

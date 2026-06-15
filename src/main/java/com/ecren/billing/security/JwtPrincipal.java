package com.ecren.billing.security;

import java.util.UUID;

public record JwtPrincipal(UUID userId, String email, String role, UUID tenantId) {}

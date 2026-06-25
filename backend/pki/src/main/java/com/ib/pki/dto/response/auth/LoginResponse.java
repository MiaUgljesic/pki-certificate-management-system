package com.ib.pki.dto.response.auth;

import com.ib.pki.model.enums.UserRole;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        UserRole role,
        String organizationName,
        boolean twoFactorRequired
) {
}


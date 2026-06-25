package com.ib.pki.dto.response.auth;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
}

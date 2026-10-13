package com.fincore.identity.infrastructure.web.dto;

import com.fincore.identity.application.TokenPair;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

    public static TokenResponse from(TokenPair tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresInSeconds());
    }

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, refreshToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}

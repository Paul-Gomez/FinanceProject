package com.fincore.identity.application;

import java.util.UUID;

public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds, UUID sessionId) {
}

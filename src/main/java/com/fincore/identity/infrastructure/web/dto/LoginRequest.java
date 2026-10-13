package com.fincore.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        @Size(max = 100) String deviceName
) {
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***, deviceName=" + deviceName + "]";
    }
}

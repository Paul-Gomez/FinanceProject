package com.fincore.identity.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String password,
        @Size(max = 100) String displayName
) {
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***, displayName=" + displayName + "]";
    }
}

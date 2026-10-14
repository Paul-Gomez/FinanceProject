package com.fincore.identity.application;

import com.fincore.identity.domain.TokenPurpose;

/** Lleva el token en claro solo en memoria, hasta que se envía el email. */
public record IdentityEmailRequested(TokenPurpose purpose, String email, String displayName, String token) {

    @Override
    public String toString() {
        return "IdentityEmailRequested[purpose=" + purpose + ", email=" + email + ", token=***]";
    }
}

package com.fincore.accounts.infrastructure.web.dto;

import com.fincore.accounts.domain.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeAccountStatusRequest(
        @NotNull AccountStatus status
) {
}

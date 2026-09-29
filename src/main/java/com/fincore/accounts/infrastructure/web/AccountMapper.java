package com.fincore.accounts.infrastructure.web;

import com.fincore.accounts.domain.Account;
import com.fincore.accounts.infrastructure.web.dto.AccountResponse;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerId(),
                account.getName(),
                account.getType(),
                account.balance().currency().getCurrencyCode(),
                account.balance().amount(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}

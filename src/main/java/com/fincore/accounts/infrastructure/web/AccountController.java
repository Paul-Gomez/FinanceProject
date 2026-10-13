package com.fincore.accounts.infrastructure.web;

import com.fincore.identity.infrastructure.security.CurrentUserId;
import com.fincore.accounts.application.AccountService;
import com.fincore.accounts.domain.Account;
import com.fincore.accounts.infrastructure.web.dto.AccountResponse;
import com.fincore.accounts.infrastructure.web.dto.ChangeAccountStatusRequest;
import com.fincore.accounts.infrastructure.web.dto.CreateAccountRequest;
import com.fincore.accounts.infrastructure.web.dto.UpdateAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AccountMapper accountMapper;

    public AccountController(AccountService accountService, AccountMapper accountMapper) {
        this.accountService = accountService;
        this.accountMapper = accountMapper;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @CurrentUserId UUID ownerId,
            @Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountMapper.toResponse(account));
    }

    @GetMapping
    public List<AccountResponse> listMine(@CurrentUserId UUID ownerId) {
        return accountService.listByOwner(ownerId).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @GetMapping("/{accountId}")
    public AccountResponse getById(@CurrentUserId UUID ownerId, @PathVariable UUID accountId) {
        return accountMapper.toResponse(accountService.getForOwner(accountId, ownerId));
    }

    @PutMapping("/{accountId}")
    public AccountResponse update(
            @CurrentUserId UUID ownerId,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        return accountMapper.toResponse(accountService.update(accountId, ownerId, request));
    }

    @PatchMapping("/{accountId}/status")
    public AccountResponse changeStatus(
            @CurrentUserId UUID ownerId,
            @PathVariable UUID accountId,
            @Valid @RequestBody ChangeAccountStatusRequest request) {
        return accountMapper.toResponse(accountService.changeStatus(accountId, ownerId, request.status()));
    }
}

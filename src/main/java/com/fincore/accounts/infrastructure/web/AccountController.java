package com.fincore.accounts.infrastructure.web;

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

    // TODO(identity): sustituir esta cabecera por el usuario autenticado (JWT) cuando exista el módulo identity.
    private static final String OWNER_HEADER = "X-User-Id";

    private final AccountService accountService;
    private final AccountMapper accountMapper;

    public AccountController(AccountService accountService, AccountMapper accountMapper) {
        this.accountService = accountService;
        this.accountMapper = accountMapper;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @RequestHeader(OWNER_HEADER) UUID ownerId,
            @Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountMapper.toResponse(account));
    }

    @GetMapping
    public List<AccountResponse> listMine(@RequestHeader(OWNER_HEADER) UUID ownerId) {
        return accountService.listByOwner(ownerId).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @GetMapping("/{accountId}")
    public AccountResponse getById(@RequestHeader(OWNER_HEADER) UUID ownerId, @PathVariable UUID accountId) {
        return accountMapper.toResponse(accountService.getForOwner(accountId, ownerId));
    }

    @PutMapping("/{accountId}")
    public AccountResponse update(
            @RequestHeader(OWNER_HEADER) UUID ownerId,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        return accountMapper.toResponse(accountService.update(accountId, ownerId, request));
    }

    @PatchMapping("/{accountId}/status")
    public AccountResponse changeStatus(
            @RequestHeader(OWNER_HEADER) UUID ownerId,
            @PathVariable UUID accountId,
            @Valid @RequestBody ChangeAccountStatusRequest request) {
        return accountMapper.toResponse(accountService.changeStatus(accountId, ownerId, request.status()));
    }
}

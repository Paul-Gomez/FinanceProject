package com.fincore.accounts.application;

import com.fincore.accounts.domain.Account;
import com.fincore.accounts.domain.AccountStatus;
import com.fincore.accounts.infrastructure.persistence.AccountJpaRepository;
import com.fincore.accounts.infrastructure.web.dto.CreateAccountRequest;
import com.fincore.accounts.infrastructure.web.dto.UpdateAccountRequest;
import com.fincore.shared.money.Money;
import com.fincore.users.application.UserQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountJpaRepository accountRepository;
    private final UserQueryService userQueryService;

    public AccountService(AccountJpaRepository accountRepository, UserQueryService userQueryService) {
        this.accountRepository = accountRepository;
        this.userQueryService = userQueryService;
    }

    @Transactional
    public Account create(UUID ownerId, CreateAccountRequest request) {
        if (!userQueryService.existsById(ownerId)) {
            throw new UnknownOwnerException(ownerId);
        }

        BigDecimal initialAmount = request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO;
        Money initialBalance = Money.of(initialAmount, Money.currencyFor(request.currencyCode()));

        Account account = new Account(ownerId, request.name(), request.type(), initialBalance);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<Account> listByOwner(UUID ownerId) {
        return accountRepository.findAllByOwnerId(ownerId);
    }

    @Transactional(readOnly = true)
    public Account getForOwner(UUID accountId, UUID requesterId) {
        Account account = findByIdOrThrow(accountId);
        requireOwnership(account, requesterId);
        return account;
    }

    @Transactional
    public Account update(UUID accountId, UUID requesterId, UpdateAccountRequest request) {
        Account account = findByIdOrThrow(accountId);
        requireOwnership(account, requesterId);
        account.rename(request.name());
        return account;
    }

    @Transactional
    public Account changeStatus(UUID accountId, UUID requesterId, AccountStatus targetStatus) {
        Account account = findByIdOrThrow(accountId);
        requireOwnership(account, requesterId);
        account.changeStatus(targetStatus);
        return account;
    }

    private Account findByIdOrThrow(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private void requireOwnership(Account account, UUID requesterId) {
        if (!account.getOwnerId().equals(requesterId)) {
            throw new AccountAccessDeniedException(account.getId());
        }
    }
}

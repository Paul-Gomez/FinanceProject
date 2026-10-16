package com.fincore.accounts.application;

import com.fincore.accounts.domain.Account;
import com.fincore.accounts.domain.AccountStatus;
import com.fincore.accounts.infrastructure.persistence.AccountJpaRepository;
import com.fincore.accounts.infrastructure.web.dto.CreateAccountRequest;
import com.fincore.accounts.infrastructure.web.dto.UpdateAccountRequest;
import com.fincore.shared.audit.AuditAction;
import com.fincore.shared.audit.AuditEvent;
import com.fincore.shared.money.Money;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountJpaRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AccountService(AccountJpaRepository accountRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Account create(UUID ownerId, CreateAccountRequest request) {
        BigDecimal initialAmount = request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO;
        Money initialBalance = Money.ofExact(initialAmount, Money.currencyFor(request.currencyCode()));

        Account account = saveOrFailIfNameTaken(
                new Account(ownerId, request.name(), request.type(), initialBalance.currency()));

        if (!initialBalance.isZero()) {
            eventPublisher.publishEvent(new AccountOpenedEvent(account.getId(), ownerId, initialBalance));
        }
        eventPublisher.publishEvent(AuditEvent.of(ownerId, AuditAction.CREATE_ACCOUNT, "ACCOUNT", account.getId(),
                Map.of("type", account.getType().name(), "currency", account.balance().currency().getCurrencyCode())));
        return account;
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
        Account saved = saveOrFailIfNameTaken(account);
        eventPublisher.publishEvent(AuditEvent.of(requesterId, AuditAction.UPDATE_ACCOUNT, "ACCOUNT", accountId));
        return saved;
    }

    @Transactional
    public Account changeStatus(UUID accountId, UUID requesterId, AccountStatus targetStatus) {
        Account account = findByIdOrThrow(accountId);
        requireOwnership(account, requesterId);
        account.changeStatus(targetStatus);
        eventPublisher.publishEvent(AuditEvent.of(requesterId, AuditAction.CHANGE_ACCOUNT_STATUS, "ACCOUNT", accountId,
                Map.of("status", targetStatus.name())));
        return account;
    }

    /**
     * El índice único es quien garantiza que no se repita el nombre. Se fuerza el flush aquí para
     * que la violación salte dentro del servicio y no al confirmar la transacción, donde acabaría en un 500.
     */
    private Account saveOrFailIfNameTaken(Account account) {
        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new AccountNameAlreadyExistsException(account.getName());
        }
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

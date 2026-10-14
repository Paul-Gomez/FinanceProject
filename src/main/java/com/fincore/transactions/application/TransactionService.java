package com.fincore.transactions.application;

import com.fincore.accounts.application.AccountBalanceService;
import com.fincore.accounts.application.AccountService;
import com.fincore.categories.application.CategoryQueryService;
import com.fincore.categories.application.CategoryRuleService;
import com.fincore.categories.domain.CategoryKind;
import com.fincore.shared.money.Money;
import com.fincore.transactions.domain.Transaction;
import com.fincore.transactions.domain.TransactionType;
import com.fincore.transactions.infrastructure.persistence.TransactionJpaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionJpaRepository transactionRepository;
    private final AccountBalanceService accountBalanceService;
    private final AccountService accountService;
    private final CategoryQueryService categoryQueryService;
    private final CategoryRuleService categoryRuleService;
    private final ApplicationEventPublisher eventPublisher;

    public TransactionService(TransactionJpaRepository transactionRepository,
                              AccountBalanceService accountBalanceService,
                              AccountService accountService,
                              CategoryQueryService categoryQueryService,
                              CategoryRuleService categoryRuleService,
                              ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.accountBalanceService = accountBalanceService;
        this.accountService = accountService;
        this.categoryQueryService = categoryQueryService;
        this.categoryRuleService = categoryRuleService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Movimiento y saldo se guardan en la misma transacción: o se confirman los dos
     * o no se confirma ninguno.
     */
    @Transactional
    public Transaction create(UUID requesterId, RecordTransactionCommand request) {
        Money amount = Money.ofExact(request.amount(), Money.currencyFor(request.currencyCode()));
        LocalDate date = request.date() != null ? request.date() : LocalDate.now();

        if (request.type() != TransactionType.INCOME && request.type() != TransactionType.EXPENSE) {
            throw new UnsupportedTransactionTypeException(request.type());
        }
        CategoryKind expectedKind = request.type() == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;

        // Si no se indica categoría, se intenta con las reglas de categorización automática.
        UUID categoryId = request.categoryId();
        if (categoryId != null) {
            categoryQueryService.requireUsable(categoryId, requesterId, expectedKind);
        } else {
            categoryId = categoryRuleService.suggestCategory(requesterId, request.description(), expectedKind).orElse(null);
        }

        Transaction transaction = request.type() == TransactionType.INCOME
                ? Transaction.income(request.accountId(), amount, categoryId, request.description(), date,
                        request.reference(), request.metadata())
                : Transaction.expense(request.accountId(), amount, categoryId, request.description(), date,
                        request.reference(), request.metadata());

        accountBalanceService.applyDelta(request.accountId(), requesterId, transaction.signedAmount());
        Transaction saved = transactionRepository.save(transaction);
        eventPublisher.publishEvent(new TransactionRecordedEvent(saved.getId(), requesterId, saved.getAccountId(),
                saved.getType(), saved.getCategoryId(), saved.getTransactionDate()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Transaction> listByAccount(UUID accountId, UUID requesterId, Pageable pageable) {
        accountService.getForOwner(accountId, requesterId);
        return transactionRepository.findAllByAccountId(accountId, pageable);
    }
}

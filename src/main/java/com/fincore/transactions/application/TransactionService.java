package com.fincore.transactions.application;

import com.fincore.accounts.application.AccountBalanceService;
import com.fincore.accounts.application.AccountService;
import com.fincore.accounts.domain.Account;
import com.fincore.categories.application.CategoryQueryService;
import com.fincore.categories.application.CategoryRuleService;
import com.fincore.categories.domain.CategoryKind;
import com.fincore.shared.money.Money;
import com.fincore.transactions.domain.Transaction;
import com.fincore.transactions.domain.TransactionType;
import com.fincore.transactions.infrastructure.persistence.TransactionJpaRepository;
import com.fincore.transactions.infrastructure.persistence.TransactionSpecifications;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * Listado con filtros, paginado y ordenado en la base de datos. Siempre se restringe a las
     * cuentas del usuario: si pide una cuenta concreta se comprueba que sea suya (403 si no).
     */
    @Transactional(readOnly = true)
    public Page<Transaction> search(UUID requesterId, TransactionSearchCriteria criteria, Pageable pageable) {
        List<UUID> accountIds;
        if (criteria.accountId() != null) {
            accountService.getForOwner(criteria.accountId(), requesterId);
            accountIds = List.of(criteria.accountId());
        } else {
            accountIds = accountService.listByOwner(requesterId).stream().map(Account::getId).toList();
            if (accountIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }

        Specification<Transaction> spec = TransactionSpecifications.inAccounts(accountIds);
        if (!criteria.includeVoided()) {
            spec = spec.and(TransactionSpecifications.postedOnly());
        }
        if (criteria.from() != null) {
            spec = spec.and(TransactionSpecifications.dateFrom(criteria.from()));
        }
        if (criteria.to() != null) {
            spec = spec.and(TransactionSpecifications.dateTo(criteria.to()));
        }
        if (criteria.categoryId() != null) {
            List<UUID> categories = new ArrayList<>(List.of(criteria.categoryId()));
            if (criteria.includeSubcategories()) {
                categories.addAll(categoryQueryService.childrenByParent(requesterId)
                        .getOrDefault(criteria.categoryId(), List.of()));
            }
            spec = spec.and(TransactionSpecifications.inCategories(categories));
        }
        if (criteria.type() != null) {
            spec = spec.and(TransactionSpecifications.ofType(criteria.type()));
        }
        if (criteria.minAmount() != null) {
            spec = spec.and(TransactionSpecifications.absoluteAmountAtLeast(criteria.minAmount()));
        }
        if (criteria.maxAmount() != null) {
            spec = spec.and(TransactionSpecifications.absoluteAmountAtMost(criteria.maxAmount()));
        }
        if (criteria.text() != null && !criteria.text().isBlank()) {
            spec = spec.and(TransactionSpecifications.containsText(criteria.text().trim()));
        }
        return transactionRepository.findAll(spec, pageable);
    }
}

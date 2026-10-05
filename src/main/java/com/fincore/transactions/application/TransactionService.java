package com.fincore.transactions.application;

import com.fincore.accounts.application.AccountBalanceService;
import com.fincore.accounts.application.AccountService;
import com.fincore.shared.money.Money;
import com.fincore.transactions.domain.Transaction;
import com.fincore.transactions.infrastructure.persistence.TransactionJpaRepository;
import com.fincore.transactions.infrastructure.web.dto.CreateTransactionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionJpaRepository transactionRepository;
    private final AccountBalanceService accountBalanceService;
    private final AccountService accountService;

    public TransactionService(TransactionJpaRepository transactionRepository,
                              AccountBalanceService accountBalanceService,
                              AccountService accountService) {
        this.transactionRepository = transactionRepository;
        this.accountBalanceService = accountBalanceService;
        this.accountService = accountService;
    }

    /**
     * Movimiento y saldo se guardan en la misma transacción: o se confirman los dos
     * o no se confirma ninguno.
     */
    @Transactional
    public Transaction create(UUID requesterId, CreateTransactionRequest request) {
        Money amount = Money.of(request.amount(), Currency.getInstance(request.currencyCode()));
        LocalDate date = request.date() != null ? request.date() : LocalDate.now();

        Transaction transaction = switch (request.type()) {
            case INCOME -> Transaction.income(request.accountId(), amount, request.categoryId(),
                    request.description(), date, request.reference(), request.metadata());
            case EXPENSE -> Transaction.expense(request.accountId(), amount, request.categoryId(),
                    request.description(), date, request.reference(), request.metadata());
            default -> throw new UnsupportedTransactionTypeException(request.type());
        };

        accountBalanceService.applyDelta(request.accountId(), requesterId, transaction.signedAmount());
        return transactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> listByAccount(UUID accountId, UUID requesterId, Pageable pageable) {
        accountService.getForOwner(accountId, requesterId);
        return transactionRepository.findAllByAccountId(accountId, pageable);
    }
}

package com.fincore.transactions.application;

import com.fincore.accounts.application.AccountBalanceService;
import com.fincore.shared.money.Money;
import com.fincore.transactions.domain.Transaction;
import com.fincore.transactions.infrastructure.persistence.TransactionJpaRepository;
import com.fincore.transactions.infrastructure.web.dto.CreateTransferRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private final TransactionJpaRepository transactionRepository;
    private final AccountBalanceService accountBalanceService;

    public TransferService(TransactionJpaRepository transactionRepository,
                           AccountBalanceService accountBalanceService) {
        this.transactionRepository = transactionRepository;
        this.accountBalanceService = accountBalanceService;
    }

    public record TransferResult(UUID transferId, Transaction outgoing, Transaction incoming) {
    }

    /**
     * Los dos saldos y las dos patas se confirman juntos o no se confirma nada:
     * nunca puede quedar "cuenta A -100 y cuenta B +0".
     */
    @Transactional
    public TransferResult transfer(UUID requesterId, CreateTransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new SameAccountTransferException();
        }

        Money amount = Money.of(request.amount(), Money.currencyFor(request.currencyCode()));
        LocalDate date = request.date() != null ? request.date() : LocalDate.now();
        UUID transferId = UUID.randomUUID();

        accountBalanceService.transfer(request.fromAccountId(), request.toAccountId(), requesterId, amount);

        Transaction outgoing = Transaction.transferLeg(transferId, request.fromAccountId(), amount.negate(),
                request.description(), date, request.reference());
        Transaction incoming = Transaction.transferLeg(transferId, request.toAccountId(), amount,
                request.description(), date, request.reference());
        transactionRepository.saveAll(List.of(outgoing, incoming));

        return new TransferResult(transferId, outgoing, incoming);
    }
}

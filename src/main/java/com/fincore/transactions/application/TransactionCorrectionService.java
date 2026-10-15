package com.fincore.transactions.application;

import com.fincore.accounts.application.AccountBalanceService;
import com.fincore.accounts.application.AccountService;
import com.fincore.accounts.domain.Account;
import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;
import com.fincore.shared.money.Money;
import com.fincore.transactions.domain.InvalidTransactionOperationException;
import com.fincore.transactions.domain.Transaction;
import com.fincore.transactions.domain.TransactionType;
import com.fincore.transactions.infrastructure.persistence.TransactionJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Correcciones sobre movimientos ya registrados: anular y reembolsar. Nada se borra ni se edita:
 * un error se corrige con un movimiento nuevo o anulando el original, y el historial cuenta lo
 * que pasó. En las dos operaciones el movimiento original y el saldo cambian en la misma transacción.
 */
@Service
public class TransactionCorrectionService {

    public static class TransactionNotFoundException extends BusinessException {
        public TransactionNotFoundException(UUID id) {
            super(ErrorType.NOT_FOUND, "TRANSACTION_NOT_FOUND", "No existe ningún movimiento con id " + id);
        }
    }

    public static class RefundExceedsExpenseException extends BusinessException {
        public RefundExceedsExpenseException(Money requested, Money refundable) {
            super(ErrorType.UNPROCESSABLE, "REFUND_EXCEEDS_EXPENSE",
                    "No se puede reembolsar %s: como mucho quedan %s por reembolsar".formatted(requested, refundable));
        }
    }

    private final TransactionJpaRepository repository;
    private final AccountService accountService;
    private final AccountBalanceService accountBalanceService;
    private final Clock clock;

    public TransactionCorrectionService(TransactionJpaRepository repository, AccountService accountService,
                                        AccountBalanceService accountBalanceService, Clock clock) {
        this.repository = repository;
        this.accountService = accountService;
        this.accountBalanceService = accountBalanceService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Transaction get(UUID transactionId, UUID requesterId) {
        return owned(repository.findById(transactionId).orElse(null), transactionId, requesterId);
    }

    /**
     * Anula el movimiento y revierte su efecto en el saldo. Una transferencia se anula entera (las dos
     * patas): anular solo una dejaría dinero creado o destruido. Un gasto con reembolsos vivos no se
     * puede anular hasta anular los reembolsos, para no dejar reembolsos de un gasto que "no existe".
     */
    @Transactional
    public void voidTransaction(UUID transactionId, UUID requesterId) {
        Transaction tx = owned(repository.findByIdForUpdate(transactionId).orElse(null), transactionId, requesterId);
        if (!tx.isPosted()) {
            throw new InvalidTransactionOperationException("el movimiento ya está anulado");
        }
        Instant now = clock.instant();

        if (tx.getType() == TransactionType.TRANSFER) {
            List<Transaction> legs = repository.findTransferLegsForUpdate(tx.getTransferId());
            Transaction outgoing = legs.stream().filter(l -> l.getAmount().signum() < 0).findFirst().orElseThrow();
            Transaction incoming = legs.stream().filter(l -> l.getAmount().signum() > 0).findFirst().orElseThrow();
            accountBalanceService.transfer(incoming.getAccountId(), outgoing.getAccountId(), requesterId,
                    incoming.signedAmount());
            legs.forEach(leg -> leg.markVoided(now));
            return;
        }

        if (tx.getType() == TransactionType.EXPENSE && repository.sumPostedRefunds(tx.getId()).signum() > 0) {
            throw new InvalidTransactionOperationException("tiene reembolsos; anula antes los reembolsos");
        }
        accountBalanceService.applyDelta(tx.getAccountId(), requesterId, tx.signedAmount().negate());
        tx.markVoided(now);
    }

    /**
     * Reembolsa un gasto (por defecto, todo lo que quede). El gasto se bloquea: si llegan dos
     * reembolsos a la vez, el segundo ve lo que ya devolvió el primero y no se puede pasar del total.
     */
    @Transactional
    public Transaction refund(UUID expenseId, UUID requesterId, BigDecimal amount, LocalDate date, String description) {
        Transaction expense = owned(repository.findByIdForUpdate(expenseId).orElse(null), expenseId, requesterId);
        if (expense.getType() != TransactionType.EXPENSE || !expense.isPosted()) {
            throw new InvalidTransactionOperationException("solo se pueden reembolsar gastos no anulados");
        }

        Money spent = expense.signedAmount().negate();
        Money alreadyRefunded = Money.of(repository.sumPostedRefunds(expense.getId()), spent.currency());
        Money refundable = spent.subtract(alreadyRefunded);
        Money requested = amount != null ? Money.ofExact(amount, spent.currency()) : refundable;
        if (!requested.isPositive() || requested.compareTo(refundable) > 0) {
            throw new RefundExceedsExpenseException(requested, refundable);
        }

        Transaction refund = Transaction.refundOf(expense, requested, date != null ? date : LocalDate.now(clock),
                description != null ? description : "Reembolso: " + (expense.getDescription() != null ? expense.getDescription() : ""));
        accountBalanceService.applyDelta(expense.getAccountId(), requesterId, requested);
        return repository.save(refund);
    }

    /** Un movimiento de otra persona se trata como inexistente (404), sin confirmar que existe. */
    private Transaction owned(Transaction tx, UUID transactionId, UUID requesterId) {
        if (tx == null || accountService.listByOwner(requesterId).stream().map(Account::getId)
                .noneMatch(id -> id.equals(tx.getAccountId()))) {
            throw new TransactionNotFoundException(transactionId);
        }
        return tx;
    }
}

package com.fincore.accounts.application;

import com.fincore.accounts.domain.Account;
import com.fincore.accounts.infrastructure.persistence.AccountJpaRepository;
import com.fincore.shared.money.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * API pública del módulo accounts para que otros módulos (transactions) muevan saldo.
 * Exige una transacción ya abierta: el saldo y el movimiento que lo justifica tienen
 * que confirmarse o deshacerse juntos, así que la transacción la abre quien llama.
 */
@Service
public class AccountBalanceService {

    private final AccountJpaRepository accountRepository;

    public AccountBalanceService(AccountJpaRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Money applyDelta(UUID accountId, UUID requesterId, Money signedDelta) {
        Account account = lockOwnedAccount(accountId, requesterId);
        account.applyDelta(signedDelta);
        return account.balance();
    }

    /**
     * Mueve el importe de una cuenta a otra. Las dos filas se bloquean siempre en orden de id:
     * si dos transferencias cruzadas (A→B y B→A) las bloquearan en orden distinto, cada una
     * se quedaría esperando a la otra y la base de datos acabaría abortando una por deadlock.
     * Si cualquiera de las dos cuentas no admite el movimiento se lanza la excepción antes
     * de que quien llama guarde nada, y su transacción lo deshace todo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void transfer(UUID fromAccountId, UUID toAccountId, UUID requesterId, Money amount) {
        Map<UUID, Account> locked = new HashMap<>();
        for (UUID id : Stream.of(fromAccountId, toAccountId).sorted().toList()) {
            locked.put(id, lockOwnedAccount(id, requesterId));
        }

        locked.get(fromAccountId).applyDelta(amount.negate());
        locked.get(toAccountId).applyDelta(amount);
    }

    private Account lockOwnedAccount(UUID accountId, UUID requesterId) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (!account.getOwnerId().equals(requesterId)) {
            throw new AccountAccessDeniedException(accountId);
        }
        return account;
    }
}

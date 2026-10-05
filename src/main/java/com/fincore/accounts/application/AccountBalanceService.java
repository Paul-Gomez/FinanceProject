package com.fincore.accounts.application;

import com.fincore.accounts.domain.Account;
import com.fincore.accounts.infrastructure.persistence.AccountJpaRepository;
import com.fincore.shared.money.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    private Account lockOwnedAccount(UUID accountId, UUID requesterId) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (!account.getOwnerId().equals(requesterId)) {
            throw new AccountAccessDeniedException(accountId);
        }
        return account;
    }
}

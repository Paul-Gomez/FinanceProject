package com.fincore.accounts.domain;

import com.fincore.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    void createsAccountAsActiveWithGivenInitialBalance() {
        Account account = new Account(UUID.randomUUID(), "Cuenta nómina", AccountType.BANK, Money.of("1000.00", "EUR"));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.balance()).isEqualTo(Money.of("1000.00", "EUR"));
    }

    @Test
    void renameUpdatesName() {
        Account account = new Account(UUID.randomUUID(), "Antiguo nombre", AccountType.CASH, Money.zero(java.util.Currency.getInstance("EUR")));

        account.rename("Nuevo nombre");

        assertThat(account.getName()).isEqualTo("Nuevo nombre");
    }

    @Test
    void allowsValidStatusTransition() {
        Account account = new Account(UUID.randomUUID(), "Cuenta", AccountType.BANK, Money.zero(java.util.Currency.getInstance("EUR")));

        account.changeStatus(AccountStatus.ARCHIVED);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ARCHIVED);
    }

    @Test
    void rejectsTransitionOutOfClosed() {
        Account account = new Account(UUID.randomUUID(), "Cuenta", AccountType.BANK, Money.zero(java.util.Currency.getInstance("EUR")));
        account.changeStatus(AccountStatus.CLOSED);

        assertThatThrownBy(() -> account.changeStatus(AccountStatus.ACTIVE))
                .isInstanceOf(InvalidAccountStatusTransitionException.class);
    }
}

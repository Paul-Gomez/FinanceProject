package com.fincore.accounts.domain;

import com.fincore.shared.money.CurrencyMismatchException;
import com.fincore.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    private static Account newEurAccount() {
        return new Account(UUID.randomUUID(), "Cuenta", AccountType.BANK, EUR);
    }

    private static Account newEurAccountWith(String balance) {
        Account account = newEurAccount();
        account.applyDelta(Money.of(balance, "EUR"));
        return account;
    }

    @Test
    void newAccountIsActiveAndStartsAtZero() {
        Account account = newEurAccount();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.balance()).isEqualTo(Money.zero(EUR));
    }

    @Test
    void renameUpdatesName() {
        Account account = newEurAccount();

        account.rename("Nuevo nombre");

        assertThat(account.getName()).isEqualTo("Nuevo nombre");
    }

    @Test
    void allowsValidStatusTransition() {
        Account account = newEurAccount();

        account.changeStatus(AccountStatus.ARCHIVED);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ARCHIVED);
    }

    @Test
    void applyDeltaUpdatesBalance() {
        Account account = newEurAccountWith("100.00");

        account.applyDelta(Money.of("-30.50", "EUR"));

        assertThat(account.balance()).isEqualTo(Money.of("69.50", "EUR"));
    }

    @Test
    void applyDeltaRejectsOtherCurrency() {
        Account account = newEurAccountWith("100.00");

        assertThatThrownBy(() -> account.applyDelta(Money.of("10.00", "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void applyDeltaRejectsNonActiveAccount() {
        Account account = newEurAccountWith("100.00");
        account.changeStatus(AccountStatus.ARCHIVED);

        assertThatThrownBy(() -> account.applyDelta(Money.of("10.00", "EUR")))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void rejectsTransitionOutOfClosed() {
        Account account = newEurAccount();
        account.changeStatus(AccountStatus.CLOSED);

        assertThatThrownBy(() -> account.changeStatus(AccountStatus.ACTIVE))
                .isInstanceOf(InvalidAccountStatusTransitionException.class);
    }
}

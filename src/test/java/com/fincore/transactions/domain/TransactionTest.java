package com.fincore.transactions.domain;

import com.fincore.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @Test
    void incomeKeepsPositiveSign() {
        Transaction tx = Transaction.income(UUID.randomUUID(), Money.of("25.00", "EUR"), null, "Nómina", TODAY, null, null);

        assertThat(tx.signedAmount()).isEqualTo(Money.of("25.00", "EUR"));
        assertThat(tx.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.POSTED);
    }

    @Test
    void expenseIsStoredWithNegativeSign() {
        Transaction tx = Transaction.expense(UUID.randomUUID(), Money.of("25.00", "EUR"), null, "Compra", TODAY, null, null);

        assertThat(tx.signedAmount()).isEqualTo(Money.of("-25.00", "EUR"));
    }

    @Test
    void transferLegsShareTheTransferId() {
        UUID transferId = UUID.randomUUID();
        Transaction out = Transaction.transferLeg(transferId, UUID.randomUUID(), Money.of("-40.00", "EUR"), null, TODAY, null);
        Transaction in = Transaction.transferLeg(transferId, UUID.randomUUID(), Money.of("40.00", "EUR"), null, TODAY, null);

        assertThat(out.getTransferId()).isEqualTo(in.getTransferId());
        assertThat(out.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(out.signedAmount().add(in.signedAmount()).isZero()).isTrue();
    }

    @Test
    void rejectsZeroOrNegativeAmounts() {
        UUID account = UUID.randomUUID();

        assertThatThrownBy(() -> Transaction.income(account, Money.of("0.00", "EUR"), null, null, TODAY, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Transaction.expense(account, Money.of("-3.00", "EUR"), null, null, TODAY, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

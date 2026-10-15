package com.fincore.transactions.infrastructure.persistence;

import com.fincore.transactions.domain.Transaction;
import com.fincore.transactions.domain.TransactionStatus;
import com.fincore.transactions.domain.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/** Piezas del filtro dinámico del listado. Cada una se aplica solo si el filtro viene informado. */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> inAccounts(Collection<UUID> accountIds) {
        return (root, query, cb) -> root.get("accountId").in(accountIds);
    }

    public static Specification<Transaction> postedOnly() {
        return (root, query, cb) -> cb.equal(root.get("status"), TransactionStatus.POSTED);
    }

    public static Specification<Transaction> dateFrom(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), from);
    }

    public static Specification<Transaction> dateTo(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), to);
    }

    public static Specification<Transaction> inCategories(Collection<UUID> categoryIds) {
        return (root, query, cb) -> root.get("categoryId").in(categoryIds);
    }

    public static Specification<Transaction> ofType(TransactionType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> absoluteAmountAtLeast(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(cb.abs(root.get("amount")), min);
    }

    public static Specification<Transaction> absoluteAmountAtMost(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(cb.abs(root.get("amount")), max);
    }

    /**
     * Búsqueda por texto en descripción o referencia, sin distinguir mayúsculas. Los comodines que
     * escriba el usuario (% y _) se escapan para que se busquen literalmente.
     */
    public static Specification<Transaction> containsText(String text) {
        String pattern = "%" + text.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("description")), pattern, '\\'),
                cb.like(cb.lower(root.get("reference")), pattern, '\\'));
    }
}

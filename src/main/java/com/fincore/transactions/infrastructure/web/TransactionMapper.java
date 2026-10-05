package com.fincore.transactions.infrastructure.web;

import com.fincore.transactions.domain.Transaction;
import com.fincore.transactions.infrastructure.web.dto.TransactionResponse;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getTransferId(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getCurrencyCode(),
                transaction.getCategoryId(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getReference(),
                transaction.getMetadata(),
                transaction.getCreatedAt()
        );
    }
}

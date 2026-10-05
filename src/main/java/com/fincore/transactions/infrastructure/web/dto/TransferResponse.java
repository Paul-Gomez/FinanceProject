package com.fincore.transactions.infrastructure.web.dto;

import java.util.UUID;

public record TransferResponse(
        UUID transferId,
        TransactionResponse outgoing,
        TransactionResponse incoming
) {
}

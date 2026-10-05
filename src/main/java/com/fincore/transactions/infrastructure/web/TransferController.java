package com.fincore.transactions.infrastructure.web;

import com.fincore.shared.idempotency.IdempotencyService;
import com.fincore.transactions.application.TransferService;
import com.fincore.transactions.infrastructure.web.dto.CreateTransferRequest;
import com.fincore.transactions.infrastructure.web.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    // TODO(identity): sustituir esta cabecera por el usuario autenticado (JWT) cuando exista el módulo identity.
    private static final String OWNER_HEADER = "X-User-Id";

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final TransferService transferService;
    private final TransactionMapper transactionMapper;
    private final IdempotencyService idempotencyService;

    public TransferController(TransferService transferService, TransactionMapper transactionMapper,
                              IdempotencyService idempotencyService) {
        this.transferService = transferService;
        this.transactionMapper = transactionMapper;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @RequestHeader(OWNER_HEADER) UUID requesterId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {
        IdempotencyService.Outcome<TransferResponse> outcome = idempotencyService.execute(
                requesterId, idempotencyKey, "POST /api/v1/transfers", request,
                HttpStatus.CREATED.value(), TransferResponse.class,
                () -> {
                    TransferService.TransferResult result = transferService.transfer(requesterId, request);
                    return new TransferResponse(
                            result.transferId(),
                            transactionMapper.toResponse(result.outgoing()),
                            transactionMapper.toResponse(result.incoming()));
                });

        ResponseEntity.BodyBuilder response = ResponseEntity.status(outcome.status());
        if (outcome.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }
        return response.body(outcome.body());
    }
}

package com.fincore.transactions.infrastructure.web;

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

    private final TransferService transferService;
    private final TransactionMapper transactionMapper;

    public TransferController(TransferService transferService, TransactionMapper transactionMapper) {
        this.transferService = transferService;
        this.transactionMapper = transactionMapper;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @RequestHeader(OWNER_HEADER) UUID requesterId,
            @Valid @RequestBody CreateTransferRequest request) {
        TransferService.TransferResult result = transferService.transfer(requesterId, request);
        TransferResponse body = new TransferResponse(
                result.transferId(),
                transactionMapper.toResponse(result.outgoing()),
                transactionMapper.toResponse(result.incoming()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}

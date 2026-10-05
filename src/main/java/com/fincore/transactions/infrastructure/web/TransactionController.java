package com.fincore.transactions.infrastructure.web;

import com.fincore.shared.web.PageResponse;
import com.fincore.transactions.application.TransactionService;
import com.fincore.transactions.infrastructure.web.dto.CreateTransactionRequest;
import com.fincore.transactions.infrastructure.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    // TODO(identity): sustituir esta cabecera por el usuario autenticado (JWT) cuando exista el módulo identity.
    private static final String OWNER_HEADER = "X-User-Id";
    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    public TransactionController(TransactionService transactionService, TransactionMapper transactionMapper) {
        this.transactionService = transactionService;
        this.transactionMapper = transactionMapper;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @RequestHeader(OWNER_HEADER) UUID requesterId,
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse body = transactionMapper.toResponse(transactionService.create(requesterId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @RequestHeader(OWNER_HEADER) UUID requesterId,
            @RequestParam UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("createdAt")));

        return PageResponse.from(transactionService.listByAccount(accountId, requesterId, pageable),
                transactionMapper::toResponse);
    }
}

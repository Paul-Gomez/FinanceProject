package com.fincore.transactions.infrastructure.web;

import com.fincore.identity.infrastructure.security.CurrentUserId;
import com.fincore.shared.idempotency.IdempotencyService;
import com.fincore.shared.web.PageResponse;
import com.fincore.transactions.application.TransactionCorrectionService;
import com.fincore.transactions.application.TransactionSearchCriteria;
import com.fincore.transactions.application.TransactionService;
import com.fincore.transactions.domain.TransactionType;
import com.fincore.transactions.infrastructure.web.dto.CreateTransactionRequest;
import com.fincore.transactions.infrastructure.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;
    private final IdempotencyService idempotencyService;
    private final TransactionCorrectionService correctionService;

    public TransactionController(TransactionService transactionService, TransactionMapper transactionMapper,
                                 IdempotencyService idempotencyService, TransactionCorrectionService correctionService) {
        this.transactionService = transactionService;
        this.transactionMapper = transactionMapper;
        this.idempotencyService = idempotencyService;
        this.correctionService = correctionService;
    }

    public record RefundRequest(@Positive BigDecimal amount, LocalDate date, @Size(max = 255) String description) {
    }

    @GetMapping("/{transactionId}")
    public TransactionResponse get(@CurrentUserId UUID requesterId, @PathVariable UUID transactionId) {
        return transactionMapper.toResponse(correctionService.get(transactionId, requesterId));
    }

    /** No borra: anula el movimiento y revierte su efecto en el saldo. */
    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> voidTransaction(@CurrentUserId UUID requesterId, @PathVariable UUID transactionId) {
        correctionService.voidTransaction(transactionId, requesterId);
        return ResponseEntity.noContent().build();
    }

    /** Sin importe, reembolsa todo lo que quede por reembolsar. */
    @PostMapping("/{transactionId}/refunds")
    public ResponseEntity<TransactionResponse> refund(@CurrentUserId UUID requesterId, @PathVariable UUID transactionId,
                                                      @Valid @RequestBody(required = false) RefundRequest request) {
        RefundRequest body = request != null ? request : new RefundRequest(null, null, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionMapper.toResponse(
                correctionService.refund(transactionId, requesterId, body.amount(), body.date(), body.description())));
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @CurrentUserId UUID requesterId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request) {
        IdempotencyService.Outcome<TransactionResponse> outcome = idempotencyService.execute(
                requesterId, idempotencyKey, "POST /api/v1/transactions", request,
                HttpStatus.CREATED.value(), TransactionResponse.class,
                () -> transactionMapper.toResponse(transactionService.create(requesterId, request.toCommand())));

        ResponseEntity.BodyBuilder response = ResponseEntity.status(outcome.status());
        if (outcome.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }
        return response.body(outcome.body());
    }

    public enum SortField { DATE, AMOUNT }

    /**
     * Listado de los movimientos del usuario con filtros opcionales. Solo se puede ordenar por los
     * campos de SortField (no por cualquier propiedad que mande el cliente); a igualdad, el más
     * reciente primero para que la paginación sea estable.
     */
    @GetMapping
    public PageResponse<TransactionResponse> list(
            @CurrentUserId UUID requesterId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "true") boolean includeSubcategories,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeVoided,
            @RequestParam(defaultValue = "DATE") SortField sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort order = switch (sort) {
            case DATE -> Sort.by(direction, "transactionDate");
            case AMOUNT -> Sort.by(direction, "amount");
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize,
                order.and(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        TransactionSearchCriteria criteria = new TransactionSearchCriteria(accountId, from, to, categoryId,
                includeSubcategories, type, minAmount, maxAmount, q, includeVoided);
        return PageResponse.from(transactionService.search(requesterId, criteria, pageable),
                transactionMapper::toResponse);
    }
}

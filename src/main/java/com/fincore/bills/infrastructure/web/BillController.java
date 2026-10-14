package com.fincore.bills.infrastructure.web;

import com.fincore.bills.application.BillService;
import com.fincore.bills.application.BillService.BillData;
import com.fincore.bills.domain.Bill;
import com.fincore.bills.domain.BillStatus;
import com.fincore.identity.infrastructure.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    public record BillRequest(
            @NotBlank @Size(max = 120) String provider,
            @NotNull @Positive BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @NotNull LocalDate issueDate,
            @NotNull LocalDate dueDate,
            UUID categoryId,
            @Size(max = 500) String notes) {

        BillData toData() {
            return new BillData(provider, amount, currencyCode, issueDate, dueDate, categoryId, notes);
        }
    }

    public record PayBillRequest(@NotNull UUID accountId, LocalDate paymentDate) {
    }

    public record BillResponse(UUID id, String provider, BigDecimal amount, String currencyCode, LocalDate issueDate,
                               LocalDate dueDate, UUID categoryId, BillStatus status, Instant paidAt,
                               UUID paidTransactionId, String notes) {
        static BillResponse from(Bill b) {
            return new BillResponse(b.getId(), b.getProvider(), b.amount().amount(), b.getCurrencyCode(),
                    b.getIssueDate(), b.getDueDate(), b.getCategoryId(), b.getStatus(), b.getPaidAt(),
                    b.getPaidTransactionId(), b.getNotes());
        }
    }

    @PostMapping
    public ResponseEntity<BillResponse> create(@CurrentUserId UUID userId, @Valid @RequestBody BillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BillResponse.from(billService.create(userId, request.toData())));
    }

    @GetMapping
    public List<BillResponse> list(@CurrentUserId UUID userId, @RequestParam(required = false) BillStatus status) {
        return billService.list(userId, status).stream().map(BillResponse::from).toList();
    }

    @GetMapping("/{billId}")
    public BillResponse get(@CurrentUserId UUID userId, @PathVariable UUID billId) {
        return BillResponse.from(billService.get(billId, userId));
    }

    @PutMapping("/{billId}")
    public BillResponse edit(@CurrentUserId UUID userId, @PathVariable UUID billId,
                             @Valid @RequestBody BillRequest request) {
        return BillResponse.from(billService.edit(billId, userId, request.toData()));
    }

    @PostMapping("/{billId}/pay")
    public BillResponse pay(@CurrentUserId UUID userId, @PathVariable UUID billId,
                            @Valid @RequestBody PayBillRequest request) {
        return BillResponse.from(billService.pay(billId, userId, request.accountId(), request.paymentDate()));
    }

    @PostMapping("/{billId}/cancel")
    public BillResponse cancel(@CurrentUserId UUID userId, @PathVariable UUID billId) {
        return BillResponse.from(billService.cancel(billId, userId));
    }
}

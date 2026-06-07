package com.banking.ledger.transaction;

import com.banking.ledger.account.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final AccountService accountService;

    public record AmountRequest(@NotNull @Positive Long amount) {}

    public record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @NotNull @Positive Long amount) {}

    @PostMapping("/accounts/{id}/deposit")
    public TransactionResponse deposit(
            @PathVariable("id") UUID accountId,
            @Valid @RequestBody AmountRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        accountService.get(accountId);
        return TransactionResponse.of(transactionService.deposit(accountId, req.amount(), key));
    }

    @PostMapping("/accounts/{id}/withdraw")
    public TransactionResponse withdraw(
            @PathVariable("id") UUID accountId,
            @Valid @RequestBody AmountRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        accountService.get(accountId);
        return TransactionResponse.of(transactionService.withdraw(accountId, req.amount(), key));
    }

    @PostMapping("/transfers")
    public TransactionResponse transfer(
            @Valid @RequestBody TransferRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return TransactionResponse.of(transactionService.transfer(
            req.fromAccountId(), req.toAccountId(), req.amount(), key));
    }

    @GetMapping("/transactions/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return TransactionResponse.of(transactionService.get(id));
    }
}

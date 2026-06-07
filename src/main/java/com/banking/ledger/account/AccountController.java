package com.banking.ledger.account;

import com.banking.ledger.ledger.LedgerEntry;
import com.banking.ledger.ledger.LedgerEntryRepository;
import com.banking.ledger.ledger.LedgerEntryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final LedgerEntryRepository entries;

    public record CreateAccountRequest(
            @NotBlank String owner,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency) {}

    public record AccountResponse(UUID id, String owner, String currency, long balance) {
        static AccountResponse of(Account a, long balance) {
            return new AccountResponse(a.getId(), a.getOwner(), a.getCurrency(), balance);
        }
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest req) {
        Account a = accountService.create(req.owner(), req.currency());
        return ResponseEntity
            .created(URI.create("/accounts/" + a.getId()))
            .body(AccountResponse.of(a, 0L));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        Account a = accountService.get(id);
        return AccountResponse.of(a, accountService.balance(id));
    }

    @GetMapping("/{id}/transactions")
    public List<LedgerEntryResponse> history(@PathVariable UUID id, Pageable pageable) {
        Page<LedgerEntry> page = entries.findByAccountIdOrderByCreatedAtDesc(id, pageable);
        return page.map(LedgerEntryResponse::of).getContent();
    }
}

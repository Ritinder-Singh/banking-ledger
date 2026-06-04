package com.banking.ledger.ledger;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID accountId,
        UUID transactionId,
        EntryType entryType,
        long amount,
        long balanceAfter,
        Instant createdAt) {

    public static LedgerEntryResponse of(LedgerEntry e) {
        return new LedgerEntryResponse(
            e.getId(),
            e.getAccountId(),
            e.getTransactionId(),
            e.getEntryType(),
            e.getAmount(),
            e.getBalanceAfter(),
            e.getCreatedAt());
    }
}

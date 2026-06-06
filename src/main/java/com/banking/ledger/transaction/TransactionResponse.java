package com.banking.ledger.transaction;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionType type,
        TransactionStatus status,
        String idempotencyKey,
        Instant createdAt) {

    public static TransactionResponse of(Transaction t) {
        return new TransactionResponse(
            t.getId(), t.getType(), t.getStatus(), t.getIdempotencyKey(), t.getCreatedAt());
    }
}

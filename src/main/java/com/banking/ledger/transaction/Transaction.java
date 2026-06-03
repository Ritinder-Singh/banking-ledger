package com.banking.ledger.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Transaction(TransactionType type, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.status = TransactionStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }
}

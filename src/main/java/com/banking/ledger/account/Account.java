package com.banking.ledger.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    public static final String SYSTEM_OWNER = "SYSTEM";

    @Id
    private UUID id;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Account(UUID id, String owner, String currency) {
        this.id = id;
        this.owner = owner;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public boolean isSystem() {
        return SYSTEM_OWNER.equals(owner);
    }
}

package com.banking.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banking.ledger.account.AccountService;
import com.banking.ledger.common.exception.InsufficientFundsException;
import com.banking.ledger.transaction.Transaction;
import com.banking.ledger.transaction.TransactionService;
import com.banking.ledger.transaction.TransactionStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class LedgerFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired AccountService accounts;
    @Autowired TransactionService transactions;

    @Test
    void depositTransferWithdrawFlow() {
        UUID alice = accounts.create("alice", "USD").getId();
        UUID bob = accounts.create("bob", "USD").getId();

        transactions.deposit(alice, 10_000L, null);
        assertThat(accounts.balance(alice)).isEqualTo(10_000L);

        Transaction t = transactions.transfer(alice, bob, 3_000L, null);
        assertThat(t.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(accounts.balance(alice)).isEqualTo(7_000L);
        assertThat(accounts.balance(bob)).isEqualTo(3_000L);

        transactions.withdraw(bob, 1_000L, null);
        assertThat(accounts.balance(bob)).isEqualTo(2_000L);
    }

    @Test
    void overdraftIsRejected() {
        UUID alice = accounts.create("alice", "USD").getId();
        assertThatThrownBy(() -> transactions.withdraw(alice, 100L, null))
            .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void idempotencyKeyDedupes() {
        UUID alice = accounts.create("alice", "USD").getId();
        UUID bob = accounts.create("bob", "USD").getId();
        transactions.deposit(alice, 5_000L, null);

        Transaction first = transactions.transfer(alice, bob, 1_000L, "key-1");
        Transaction second = transactions.transfer(alice, bob, 1_000L, "key-1");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(accounts.balance(alice)).isEqualTo(4_000L);
        assertThat(accounts.balance(bob)).isEqualTo(1_000L);
    }
}

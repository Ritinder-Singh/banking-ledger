package com.banking.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.ledger.account.AccountService;
import com.banking.ledger.transaction.TransactionService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// The headline test: N concurrent transfers from a single source account, all
// going through @Transactional + SELECT FOR UPDATE on the same row, must
// produce an exact final balance with zero lost updates and zero double-spends.
// This is the proof that the locking strategy works under load — not just that
// it compiles.
@SpringBootTest
@Testcontainers
class ConcurrentTransferStressTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired AccountService accounts;
    @Autowired TransactionService transactions;

    @Test
    void concurrentTransfersFromOneSourcePreserveExactBalance() throws InterruptedException {
        UUID source = accounts.create("source", "USD").getId();
        UUID sink = accounts.create("sink", "USD").getId();

        long startingBalance = 1_000_000L;
        transactions.deposit(source, startingBalance, null);

        int threads = 20;
        int transfersPerThread = 50;
        long amountEach = 100L;
        long expectedMoved = (long) threads * transfersPerThread * amountEach;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger succeeded = new AtomicInteger();
        List<Throwable> failures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < transfersPerThread; i++) {
                        transactions.transfer(source, sink, amountEach, null);
                        succeeded.incrementAndGet();
                    }
                } catch (Throwable ex) {
                    synchronized (failures) { failures.add(ex); }
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(failures).isEmpty();
        assertThat(succeeded.get()).isEqualTo(threads * transfersPerThread);

        assertThat(accounts.balance(source)).isEqualTo(startingBalance - expectedMoved);
        assertThat(accounts.balance(sink)).isEqualTo(expectedMoved);
    }

    @Test
    void concurrentBidirectionalTransfersDoNotDeadlock() throws InterruptedException {
        UUID a = accounts.create("a", "USD").getId();
        UUID b = accounts.create("b", "USD").getId();
        transactions.deposit(a, 100_000L, null);
        transactions.deposit(b, 100_000L, null);

        int threads = 16;
        int perThread = 25;
        long amount = 10L;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> failures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final boolean reverse = (t % 2 == 0);
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        if (reverse) {
                            transactions.transfer(a, b, amount, null);
                        } else {
                            transactions.transfer(b, a, amount, null);
                        }
                    }
                } catch (Throwable ex) {
                    synchronized (failures) { failures.add(ex); }
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        // If the deterministic lock ordering is wrong this hangs and the timeout fires.
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(failures).isEmpty();
        // Each direction runs (threads/2) * perThread times with equal amount,
        // so net movement is zero and both balances stay at 100_000.
        assertThat(accounts.balance(a)).isEqualTo(100_000L);
        assertThat(accounts.balance(b)).isEqualTo(100_000L);
    }
}

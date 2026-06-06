package com.banking.ledger.transaction;

import com.banking.ledger.account.Account;
import com.banking.ledger.account.AccountRepository;
import com.banking.ledger.common.exception.AccountNotFoundException;
import com.banking.ledger.common.exception.CurrencyMismatchException;
import com.banking.ledger.common.exception.InsufficientFundsException;
import com.banking.ledger.common.exception.TransactionNotFoundException;
import com.banking.ledger.ledger.EntryType;
import com.banking.ledger.ledger.LedgerEntry;
import com.banking.ledger.ledger.LedgerEntryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final TransactionRepository transactions;
    private final MeterRegistry meter;

    @Transactional(readOnly = true)
    public Transaction get(UUID id) {
        return transactions.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @Transactional
    public Transaction deposit(UUID accountId, long amount, String idempotencyKey) {
        Optional<Transaction> existing = idempotent(idempotencyKey);
        if (existing.isPresent()) {
            countDuplicate(TransactionType.DEPOSIT);
            return existing.get();
        }

        Account user = lock(accountId);
        Account system = systemFor(user.getCurrency());
        Transaction txn = transactions.save(new Transaction(TransactionType.DEPOSIT, idempotencyKey));
        post(txn, system, user, amount);
        txn.setStatus(TransactionStatus.COMPLETED);
        countCompleted(TransactionType.DEPOSIT);
        return txn;
    }

    @Transactional
    public Transaction withdraw(UUID accountId, long amount, String idempotencyKey) {
        Optional<Transaction> existing = idempotent(idempotencyKey);
        if (existing.isPresent()) {
            countDuplicate(TransactionType.WITHDRAWAL);
            return existing.get();
        }

        Account user = lock(accountId);
        Account system = systemFor(user.getCurrency());
        Transaction txn = transactions.save(new Transaction(TransactionType.WITHDRAWAL, idempotencyKey));
        post(txn, user, system, amount);
        txn.setStatus(TransactionStatus.COMPLETED);
        countCompleted(TransactionType.WITHDRAWAL);
        return txn;
    }

    @Transactional
    public Transaction transfer(UUID fromId, UUID toId, long amount, String idempotencyKey) {
        Optional<Transaction> existing = idempotent(idempotencyKey);
        if (existing.isPresent()) {
            countDuplicate(TransactionType.TRANSFER);
            return existing.get();
        }
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }
        // Lock in deterministic order to avoid deadlocks between two concurrent
        // transfers that touch the same pair of accounts in opposite directions.
        Account first = lock(min(fromId, toId));
        Account second = lock(max(fromId, toId));
        Account from = first.getId().equals(fromId) ? first : second;
        Account to = first.getId().equals(fromId) ? second : first;
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException(from.getCurrency(), to.getCurrency());
        }
        Transaction txn = transactions.save(new Transaction(TransactionType.TRANSFER, idempotencyKey));
        post(txn, from, to, amount);
        txn.setStatus(TransactionStatus.COMPLETED);
        countCompleted(TransactionType.TRANSFER);
        return txn;
    }

    private void countCompleted(TransactionType type) {
        meter.counter("transactions.completed", "type", type.name().toLowerCase()).increment();
    }

    private void countDuplicate(TransactionType type) {
        meter.counter("idempotent.duplicates.blocked", "type", type.name().toLowerCase()).increment();
    }

    // Post a balanced debit + credit pair. Caller must already hold pessimistic
    // locks on both accounts.
    private void post(Transaction txn, Account debit, Account credit, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        long debitBalance = entries.balanceOf(debit.getId()) - amount;
        if (debitBalance < 0 && !debit.isSystem()) {
            throw new InsufficientFundsException(debit.getId());
        }
        long creditBalance = entries.balanceOf(credit.getId()) + amount;
        entries.save(new LedgerEntry(debit.getId(), txn.getId(), EntryType.DEBIT, amount, debitBalance));
        entries.save(new LedgerEntry(credit.getId(), txn.getId(), EntryType.CREDIT, amount, creditBalance));
    }

    private Account lock(UUID accountId) {
        return accounts.findByIdForUpdate(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private Account systemFor(String currency) {
        return accounts.findSystemForUpdate(currency)
            .orElseGet(() -> accounts.save(new Account(UUID.randomUUID(), Account.SYSTEM_OWNER, currency)));
    }

    private Optional<Transaction> idempotent(String key) {
        return key == null ? Optional.empty() : transactions.findByIdempotencyKey(key);
    }

    private static UUID min(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static UUID max(UUID a, UUID b) {
        return a.compareTo(b) > 0 ? a : b;
    }
}

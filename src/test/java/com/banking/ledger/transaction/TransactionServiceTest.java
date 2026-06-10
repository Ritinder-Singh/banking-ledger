package com.banking.ledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banking.ledger.account.Account;
import com.banking.ledger.account.AccountRepository;
import com.banking.ledger.common.exception.AccountNotFoundException;
import com.banking.ledger.common.exception.CurrencyMismatchException;
import com.banking.ledger.common.exception.InsufficientFundsException;
import com.banking.ledger.ledger.LedgerEntry;
import com.banking.ledger.ledger.LedgerEntryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock AccountRepository accounts;
    @Mock LedgerEntryRepository entries;
    @Mock TransactionRepository transactions;
    TransactionService service;

    private Account alice;
    private Account bob;
    private Account system;

    @BeforeEach
    void setUp() {
        alice = new Account(UUID.randomUUID(), "alice", "USD");
        bob = new Account(UUID.randomUUID(), "bob", "USD");
        system = new Account(UUID.randomUUID(), Account.SYSTEM_OWNER, "USD");
        service = new TransactionService(accounts, entries, transactions, new SimpleMeterRegistry());
    }

    private void stubTxnSave() {
        when(transactions.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void depositLocksUserAndSystemAccountsAndPostsPair() {
        stubTxnSave();
        when(accounts.findByIdForUpdate(alice.getId())).thenReturn(Optional.of(alice));
        when(accounts.findSystemForUpdate("USD")).thenReturn(Optional.of(system));
        when(entries.balanceOf(any(UUID.class))).thenReturn(0L);

        Transaction txn = service.deposit(alice.getId(), 500L, null);

        assertThat(txn.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(txn.getType()).isEqualTo(TransactionType.DEPOSIT);
        verify(entries, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    void withdrawWithoutFundsThrows() {
        stubTxnSave();
        when(accounts.findByIdForUpdate(alice.getId())).thenReturn(Optional.of(alice));
        when(accounts.findSystemForUpdate("USD")).thenReturn(Optional.of(system));
        when(entries.balanceOf(alice.getId())).thenReturn(50L);

        assertThatThrownBy(() -> service.withdraw(alice.getId(), 100L, null))
            .isInstanceOf(InsufficientFundsException.class);
        verify(entries, never()).save(any(LedgerEntry.class));
    }

    @Test
    void transferAcrossCurrenciesIsRejected() {
        Account euros = new Account(UUID.randomUUID(), "eve", "EUR");
        when(accounts.findByIdForUpdate(any(UUID.class)))
            .thenReturn(Optional.of(alice))
            .thenReturn(Optional.of(euros));

        assertThatThrownBy(() -> service.transfer(alice.getId(), euros.getId(), 100L, null))
            .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void idempotencyKeyShortCircuits() {
        Transaction existing = new Transaction(TransactionType.TRANSFER, "key-1");
        when(transactions.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        Transaction got = service.transfer(alice.getId(), bob.getId(), 100L, "key-1");

        assertThat(got).isSameAs(existing);
        verify(accounts, never()).findByIdForUpdate(any());
        verify(entries, never()).save(any(LedgerEntry.class));
    }

    @Test
    void missingAccountThrows() {
        when(accounts.findByIdForUpdate(any(UUID.class))).thenReturn(Optional.empty());
        UUID gone = UUID.randomUUID();
        assertThatThrownBy(() -> service.deposit(gone, 100L, null))
            .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void transferToSameAccountIsRejected() {
        assertThatThrownBy(() -> service.transfer(alice.getId(), alice.getId(), 100L, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transferLocksBothAccountsInDeterministicOrder() {
        stubTxnSave();
        when(accounts.findByIdForUpdate(any(UUID.class)))
            .thenReturn(Optional.of(alice))
            .thenReturn(Optional.of(bob));
        when(entries.balanceOf(any(UUID.class))).thenReturn(1_000L);

        service.transfer(alice.getId(), bob.getId(), 100L, null);

        UUID lo = alice.getId().compareTo(bob.getId()) <= 0 ? alice.getId() : bob.getId();
        UUID hi = alice.getId().compareTo(bob.getId()) <= 0 ? bob.getId() : alice.getId();
        verify(accounts).findByIdForUpdate(eq(lo));
        verify(accounts).findByIdForUpdate(eq(hi));
    }
}

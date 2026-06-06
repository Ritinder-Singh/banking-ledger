package com.banking.ledger.account;

import com.banking.ledger.common.exception.AccountNotFoundException;
import com.banking.ledger.ledger.LedgerEntryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;

    @Transactional
    public Account create(String owner, String currency) {
        Account account = new Account(UUID.randomUUID(), owner, currency.toUpperCase());
        return accounts.save(account);
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public long balance(UUID id) {
        return entries.balanceOf(id);
    }
}

package com.banking.ledger.ledger;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    // Balance derived from history, never read from a mutable column. The
    // pessimistic lock on the account row guarantees this read sees a
    // consistent slice.
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN e.entryType = com.banking.ledger.ledger.EntryType.CREDIT
                                 THEN e.amount ELSE -e.amount END), 0)
        FROM LedgerEntry e
        WHERE e.accountId = :accountId
        """)
    long balanceOf(@Param("accountId") UUID accountId);
}

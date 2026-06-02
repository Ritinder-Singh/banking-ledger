CREATE TABLE accounts (
    id           UUID         PRIMARY KEY,
    owner        VARCHAR(255) NOT NULL,
    currency     VARCHAR(3)   NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- One SYSTEM cash account per currency. Used as the counterparty for deposits
-- and withdrawals so every transaction is a balanced debit + credit pair.
CREATE UNIQUE INDEX uq_accounts_system_currency
    ON accounts(currency) WHERE owner = 'SYSTEM';

CREATE TABLE transactions (
    id               UUID         PRIMARY KEY,
    type             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    idempotency_key  VARCHAR(255) UNIQUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Money is stored as BIGINT minor units (e.g. cents). This avoids any
-- floating-point rounding and matches how core banking systems represent value.
CREATE TABLE ledger_entries (
    id              UUID        PRIMARY KEY,
    account_id      UUID        NOT NULL REFERENCES accounts(id),
    transaction_id  UUID        NOT NULL REFERENCES transactions(id),
    entry_type      VARCHAR(10) NOT NULL,
    amount          BIGINT      NOT NULL CHECK (amount > 0),
    balance_after   BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_account_created
    ON ledger_entries(account_id, created_at);

CREATE INDEX idx_ledger_transaction
    ON ledger_entries(transaction_id);

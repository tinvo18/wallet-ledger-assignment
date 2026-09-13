CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    player_id VARCHAR(100) NOT NULL UNIQUE,
    balance NUMERIC(19, 2) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT wallets_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    request_id VARCHAR(100) NOT NULL UNIQUE,
    player_id VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    balance_after NUMERIC(19, 2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    reference_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ledger_amount_positive CHECK (amount > 0)
);
CREATE INDEX idx_ledger_player_created ON ledger_transactions(player_id, created_at DESC);

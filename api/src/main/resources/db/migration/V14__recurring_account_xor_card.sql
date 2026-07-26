-- Sessão #32: fixo pode ser em conta OU em cartão de crédito (antes só conta).
-- Fecha a pendência deixada pela #25 ("fixo em cartão = melhoria futura").
-- Puramente aditiva/relaxante: os fixos existentes têm account_id preenchido e
-- card_id NULL, então já satisfazem o XOR — sem backfill, sem downtime.

ALTER TABLE recurring_transactions
    ALTER COLUMN account_id DROP NOT NULL,
    ADD COLUMN card_id UUID NULL REFERENCES cards (id);

-- exatamente um dos dois: mesma modelagem de transactions (chk_transactions_account_xor_card, V12)
ALTER TABLE recurring_transactions ADD CONSTRAINT chk_recurring_account_xor_card
    CHECK ((account_id IS NOT NULL AND card_id IS NULL) OR (account_id IS NULL AND card_id IS NOT NULL));

CREATE INDEX idx_recurring_card ON recurring_transactions (card_id);

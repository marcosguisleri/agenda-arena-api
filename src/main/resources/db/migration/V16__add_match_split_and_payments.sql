-- Partida, fatia 3: rateio, pagamento declarado, fechamento e chave Pix.
-- share_amount: valor congelado ao fechar (nulo enquanto aberta: o valor é calculado na leitura).
-- paid_amount: valor da cota no momento em que o pagamento foi declarado (sinaliza mudança após
-- reabrir). "Pago" é declaração manual, não confirmação bancária.

ALTER TABLE match_participants
    ADD COLUMN pays_share BOOLEAN;

UPDATE match_participants
SET pays_share = (position = 'FIELD');

ALTER TABLE match_participants
    ALTER COLUMN pays_share SET NOT NULL;

ALTER TABLE match_participants
    ADD COLUMN share_amount NUMERIC(10, 2),
    ADD COLUMN paid         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN paid_amount  NUMERIC(10, 2);

ALTER TABLE match_participants
    ADD CONSTRAINT chk_match_participants_share_amount_non_negative
        CHECK (share_amount IS NULL OR share_amount >= 0),
    ADD CONSTRAINT chk_match_participants_paid_amount_consistent
        CHECK ((paid AND paid_amount IS NOT NULL AND paid_amount >= 0)
            OR (NOT paid AND paid_amount IS NULL));

ALTER TABLE matches
    ADD COLUMN pix_key   VARCHAR(140),
    ADD COLUMN closed_at TIMESTAMPTZ;

-- Chave Pix com tipo, titular e banco: do estabelecimento (configurada pelo admin) e da partida
-- (cópia da chave do estabelecimento ou chave própria do organizador). Só exibição; o pagamento
-- acontece fora da plataforma.

-- 1) Partidas: pix_key era texto livre, sem tipo nem titular (agora obrigatórios). Ainda não há
--    produção; as chaves antigas são descartadas em vez de inventar tipo e titular.
UPDATE matches
SET pix_key = NULL
WHERE pix_key IS NOT NULL;

ALTER TABLE matches
    ADD COLUMN pix_source      VARCHAR(20),
    ADD COLUMN pix_key_type    VARCHAR(20),
    ADD COLUMN pix_holder_name VARCHAR(120),
    ADD COLUMN pix_bank_name   VARCHAR(80);

ALTER TABLE matches
    ADD CONSTRAINT chk_matches_pix_source_valid
        CHECK (pix_source IS NULL OR pix_source IN ('ESTABLISHMENT', 'ORGANIZER')),
    ADD CONSTRAINT chk_matches_pix_key_type_valid
        CHECK (pix_key_type IS NULL OR pix_key_type IN ('CPF', 'CNPJ', 'PHONE', 'EMAIL', 'RANDOM')),
    ADD CONSTRAINT chk_matches_pix_all_or_nothing
        CHECK (
            (pix_key IS NULL AND pix_source IS NULL AND pix_key_type IS NULL
                AND pix_holder_name IS NULL AND pix_bank_name IS NULL)
            OR
            (pix_key IS NOT NULL AND pix_source IS NOT NULL AND pix_key_type IS NOT NULL
                AND pix_holder_name IS NOT NULL)
        );

-- 2) Estabelecimentos: chave Pix opcional (tudo ou nada; banco é opcional).
ALTER TABLE establishments
    ADD COLUMN pix_key_type    VARCHAR(20),
    ADD COLUMN pix_key         VARCHAR(140),
    ADD COLUMN pix_holder_name VARCHAR(120),
    ADD COLUMN pix_bank_name   VARCHAR(80);

ALTER TABLE establishments
    ADD CONSTRAINT chk_establishments_pix_key_type_valid
        CHECK (pix_key_type IS NULL OR pix_key_type IN ('CPF', 'CNPJ', 'PHONE', 'EMAIL', 'RANDOM')),
    ADD CONSTRAINT chk_establishments_pix_all_or_nothing
        CHECK (
            (pix_key IS NULL AND pix_key_type IS NULL
                AND pix_holder_name IS NULL AND pix_bank_name IS NULL)
            OR
            (pix_key IS NOT NULL AND pix_key_type IS NOT NULL
                AND pix_holder_name IS NOT NULL)
        );

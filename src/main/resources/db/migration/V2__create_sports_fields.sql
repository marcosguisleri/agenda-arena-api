CREATE TABLE sports_fields
(
    id               BIGSERIAL    PRIMARY KEY,
    establishment_id BIGINT       NOT NULL,
    name             VARCHAR(120) NOT NULL,
    sport_type       VARCHAR(50)  NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_sports_fields_establishment
        FOREIGN KEY (establishment_id)
            REFERENCES establishments (id),

    CONSTRAINT uk_sports_fields_establishment_name
        UNIQUE (establishment_id, name),

    CONSTRAINT ck_sports_fields_name_not_blank
        CHECK (btrim(name) <> ''),

    CONSTRAINT ck_sports_fields_sport_type
        CHECK (sport_type IN ('SOCIETY'))
);
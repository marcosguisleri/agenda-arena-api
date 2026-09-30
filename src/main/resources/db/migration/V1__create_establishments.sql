CREATE TABLE establishments
(
    id     BIGSERIAL    PRIMARY KEY,
    name   VARCHAR(120) NOT NULL,
    active BOOLEAN      NOT NULL DEFAULT TRUE
);
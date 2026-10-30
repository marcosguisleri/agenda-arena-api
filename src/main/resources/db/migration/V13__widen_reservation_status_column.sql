-- CANCELLATION_REQUESTED (22 caracteres) não cabia em VARCHAR(20). A V12 não é editada porque já
-- foi aplicada; o alargamento da coluna vem nesta migration.

ALTER TABLE reservations
    ALTER COLUMN status TYPE VARCHAR(30);

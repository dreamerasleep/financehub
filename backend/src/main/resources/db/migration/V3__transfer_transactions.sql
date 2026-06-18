-- FinanceHub V3: support TRANSFER transactions
-- Single-row model: from account = account_id, to account = to_account_id, category_id NULL.
-- INCOME/EXPENSE rows keep their original shape (category_id NOT NULL, to_account_id NULL).

ALTER TABLE transactions
    ALTER COLUMN category_id DROP NOT NULL;

ALTER TABLE transactions
    ADD COLUMN to_account_id BIGINT REFERENCES accounts(id) ON DELETE CASCADE;

ALTER TABLE transactions
    DROP CONSTRAINT transactions_type_check;

ALTER TABLE transactions
    ADD CONSTRAINT transactions_type_check
        CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER'));

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_transfer_shape CHECK (
        (type = 'TRANSFER'
            AND to_account_id IS NOT NULL
            AND to_account_id <> account_id
            AND category_id IS NULL)
        OR
        (type IN ('INCOME', 'EXPENSE')
            AND to_account_id IS NULL
            AND category_id IS NOT NULL)
    );

CREATE INDEX idx_transactions_to_account_id ON transactions(to_account_id);

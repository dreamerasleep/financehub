-- FinanceHub V2: categories and transactions

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(60)  NOT NULL,
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE')),
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_categories_owner CHECK (
        (is_system = TRUE  AND user_id IS NULL) OR
        (is_system = FALSE AND user_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_categories_system_name
    ON categories (name, kind) WHERE is_system = TRUE;
CREATE UNIQUE INDEX uq_categories_user_name
    ON categories (user_id, name, kind) WHERE is_system = FALSE;
CREATE INDEX idx_categories_user_id ON categories(user_id);

CREATE TABLE transactions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    account_id   BIGINT        NOT NULL REFERENCES accounts(id)   ON DELETE CASCADE,
    category_id  BIGINT        NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    type         VARCHAR(10)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount       NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    txn_date     DATE          NOT NULL,
    note         VARCHAR(255),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_user_id      ON transactions(user_id);
CREATE INDEX idx_transactions_account_id   ON transactions(account_id);
CREATE INDEX idx_transactions_user_date    ON transactions(user_id, txn_date DESC);

-- Seed system categories
INSERT INTO categories (user_id, name, kind, is_system) VALUES
    (NULL, '薪資',     'INCOME',  TRUE),
    (NULL, '獎金',     'INCOME',  TRUE),
    (NULL, '投資收益', 'INCOME',  TRUE),
    (NULL, '其他收入', 'INCOME',  TRUE),
    (NULL, '飲食',     'EXPENSE', TRUE),
    (NULL, '交通',     'EXPENSE', TRUE),
    (NULL, '居住',     'EXPENSE', TRUE),
    (NULL, '娛樂',     'EXPENSE', TRUE),
    (NULL, '醫療',     'EXPENSE', TRUE),
    (NULL, '購物',     'EXPENSE', TRUE),
    (NULL, '其他支出', 'EXPENSE', TRUE);

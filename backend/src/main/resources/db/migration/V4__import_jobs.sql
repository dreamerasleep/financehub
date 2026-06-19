-- FinanceHub V4: import_jobs staging tables for CSV/XLSX bulk import

CREATE TABLE import_jobs (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename     VARCHAR(255) NOT NULL,
    format       VARCHAR(10)  NOT NULL CHECK (format IN ('CSV', 'XLSX')),
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'COMMITTED', 'CANCELLED', 'EXPIRED')),
    row_count    INT NOT NULL DEFAULT 0,
    ok_count     INT NOT NULL DEFAULT 0,
    error_count  INT NOT NULL DEFAULT 0,
    dup_count    INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    committed_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '24 hours'
);

CREATE INDEX idx_import_jobs_user_id ON import_jobs(user_id);
CREATE INDEX idx_import_jobs_status_expires ON import_jobs(status, expires_at);

CREATE TABLE import_job_rows (
    id                   BIGSERIAL PRIMARY KEY,
    job_id               BIGINT NOT NULL REFERENCES import_jobs(id) ON DELETE CASCADE,
    row_index            INT NOT NULL,
    raw_json             JSONB NOT NULL,
    parsed_type          VARCHAR(10),
    parsed_amount        NUMERIC(18, 2),
    parsed_date          DATE,
    parsed_account_id    BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    parsed_to_account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    parsed_category_id   BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    parsed_note          VARCHAR(255),
    dedup_hash           VARCHAR(64),
    status               VARCHAR(15) NOT NULL
                         CHECK (status IN ('OK', 'ERROR', 'DUPLICATE')),
    error_message        VARCHAR(500),
    UNIQUE (job_id, row_index)
);

CREATE INDEX idx_import_job_rows_job_id_status ON import_job_rows(job_id, status);

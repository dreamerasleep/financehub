# 資料庫設計

## 整體 ER 圖

```
users ─┬─< accounts ─┐
       │             │
       └─< categories │  (user_id 可為 NULL = 系統分類)
       │             ▼
       └─< transactions ──> accounts
                       └──> categories
```

`transactions` 同時參照 `accounts` 與 `categories`；`categories.user_id` 為 NULL 代表系統預設分類，全使用者共用。

未來會加：
- `receipts` (Sprint 4)
- `fx_rates`, `stock_quotes` (Sprint 5)
- `budgets` (Sprint 6)

## Migration 政策

- **所有 schema 改動走 Flyway**，放在 `backend/src/main/resources/db/migration/`
- 檔名規範：`V<版號>__<描述>.sql`，如 `V1__init_users_and_accounts.sql`
- 已 release 的 migration **不可改動**，只能新增 V2、V3...
- JPA 啟動時 `spring.jpa.hibernate.ddl-auto: validate`，schema 不一致直接 boot 失敗

## 表結構

### `users`

| 欄位 | 型別 | 限制 | 說明 |
| ---- | ---- | ---- | ---- |
| `id` | `BIGSERIAL` | PK | 自動遞增 |
| `email` | `VARCHAR(255)` | NOT NULL, UNIQUE | 登入帳號 |
| `name` | `VARCHAR(100)` | NOT NULL | 顯示名稱 |
| `password_hash` | `VARCHAR(255)` | NOT NULL | BCrypt 雜湊 |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, default `now()` | 註冊時間 |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, default `now()` | 最後更新 |

索引：`idx_users_email` on `email`

### `accounts`

| 欄位 | 型別 | 限制 | 說明 |
| ---- | ---- | ---- | ---- |
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | FK → `users.id` ON DELETE CASCADE | 所屬使用者 |
| `name` | `VARCHAR(100)` | NOT NULL | 帳戶名稱 |
| `type` | `VARCHAR(20)` | NOT NULL, CHECK | `CHECKING`/`SAVING`/`CREDIT`/`INVESTMENT`/`CASH` |
| `currency` | `VARCHAR(3)` | NOT NULL, default `'TWD'` | ISO 4217 |
| `current_balance` | `NUMERIC(18,2)` | NOT NULL, default `0` | 當下餘額 |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, default `now()` | |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, default `now()` | |

索引：`idx_accounts_user_id` on `user_id`

### `categories` (V2)

| 欄位 | 型別 | 限制 | 說明 |
| ---- | ---- | ---- | ---- |
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | NULL 表示系統分類；非 NULL 時 FK → `users.id` ON DELETE CASCADE | 擁有者 |
| `name` | `VARCHAR(60)` | NOT NULL | 顯示名稱 |
| `kind` | `VARCHAR(10)` | NOT NULL, CHECK | `INCOME` / `EXPENSE` |
| `is_system` | `BOOLEAN` | NOT NULL, default `FALSE` | 是否為內建分類 |

額外約束：
- `chk_categories_owner`：`is_system=TRUE` 須配合 `user_id IS NULL`，反之亦然
- 唯一索引：系統分類 `(name, kind)` 唯一；使用者分類 `(user_id, name, kind)` 唯一
- 種子：V2 migration 寫入 4 個 INCOME + 7 個 EXPENSE 預設分類

### `transactions` (V2, V3)

| 欄位 | 型別 | 限制 | 說明 |
| ---- | ---- | ---- | ---- |
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | NOT NULL, FK → `users.id` ON DELETE CASCADE | 擁有者 |
| `account_id` | `BIGINT` | NOT NULL, FK → `accounts.id` ON DELETE CASCADE | 影響的（來源）帳戶 |
| `to_account_id` | `BIGINT` | NULL（V3），FK → `accounts.id` ON DELETE CASCADE | 轉帳目標帳戶 |
| `category_id` | `BIGINT` | NULL（V3），FK → `categories.id` ON DELETE RESTRICT | 分類 |
| `type` | `VARCHAR(10)` | NOT NULL, CHECK | `INCOME` / `EXPENSE` / `TRANSFER`（V3 起） |
| `amount` | `NUMERIC(18,2)` | NOT NULL, CHECK `amount > 0` | 絕對值，方向由 `type` 決定 |
| `txn_date` | `DATE` | NOT NULL | 交易日 |
| `note` | `VARCHAR(255)` | NULL | 備註 |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | NOT NULL, default `now()` | |

額外約束（`chk_transactions_transfer_shape`）：

- `type = 'TRANSFER'`：`to_account_id IS NOT NULL` 且 `≠ account_id`、`category_id IS NULL`
- `type IN ('INCOME', 'EXPENSE')`：`to_account_id IS NULL`、`category_id IS NOT NULL`

索引：
- `idx_transactions_user_id` on `user_id`
- `idx_transactions_account_id` on `account_id`
- `idx_transactions_to_account_id` on `to_account_id`（V3）
- `idx_transactions_user_date` on `(user_id, txn_date DESC)`（列表頁主要查詢）

餘額同步：`TransactionService` 在 `@Transactional` 內同時寫入交易並調整 `accounts.current_balance`。轉帳會同時調整來源（−amount）與目標（+amount）兩邊，更新 / 刪除前會先還原雙邊舊金額。跨幣別轉帳目前回 400，待 Sprint 5 匯率服務上線後解鎖。

### `import_jobs` (V4)

| 欄位 | 型別 | 限制 | 說明 |
| ---- | ---- | ---- | ---- |
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | NOT NULL FK → `users.id` CASCADE | |
| `filename` | `VARCHAR(255)` | NOT NULL | 原始檔名 |
| `format` | `VARCHAR(10)` | NOT NULL, CHECK | `CSV` / `XLSX` |
| `status` | `VARCHAR(20)` | NOT NULL, CHECK | `PENDING` / `COMMITTED` / `CANCELLED` / `EXPIRED` |
| `row_count` `ok_count` `error_count` `dup_count` | `INT` | NOT NULL default 0 | 統計快取 |
| `created_at` | `TIMESTAMPTZ` | NOT NULL default `now()` | |
| `committed_at` | `TIMESTAMPTZ` | NULL | 確認時間 |
| `expires_at` | `TIMESTAMPTZ` | NOT NULL default `now()+24h` | TTL |

### `import_job_rows` (V4)

| 欄位 | 型別 | 限制 | 說明 |
| ---- | ---- | ---- | ---- |
| `id` | `BIGSERIAL` | PK | |
| `job_id` | `BIGINT` | NOT NULL FK → `import_jobs.id` CASCADE | |
| `row_index` | `INT` | NOT NULL, `(job_id, row_index)` UNIQUE | 1-based |
| `raw_json` | `JSONB` | NOT NULL | 原始一列 |
| `parsed_type` `parsed_amount` `parsed_date` `parsed_account_id` `parsed_to_account_id` `parsed_category_id` `parsed_note` | 與 transactions 同 | 所有 FK 用 ON DELETE SET NULL | 預解析結果 |
| `dedup_hash` | `CHAR(64)` | | SHA-256(canonical key) |
| `status` | `VARCHAR(15)` | NOT NULL, CHECK | `OK` / `ERROR` / `DUPLICATE` |
| `error_message` | `VARCHAR(500)` | NULL | 失敗原因 |

`dedup_hash` 算法與 `transactions` 列在 commit 流程內逐筆比對；既有 `transactions` 表**不**新增 hash 欄。

## 設計取捨

- **`NUMERIC(18,2)`**：金額用十進位精準存，**不用 float/double**（浮點誤差會在月底對帳爆炸）。
- **`TIMESTAMPTZ`**：所有時間戳帶時區，避免日後跨時區出現「莫名其妙差一天」。
- **`CHECK` 約束**：在 DB 層強制 type 枚舉，即使應用層 bug 也不會塞錯值。
- **ON DELETE CASCADE**：刪 user 連同帳戶一起刪；確保不會有孤兒資料。

## 連線設定

預設值（本機）：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/financehub
    username: financehub
    password: financehub_dev
```

生產用環境變數覆蓋：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。

# 資料庫設計

## 整體 ER 圖

```
┌─────────────────────────┐
│        users            │
├─────────────────────────┤
│ id           BIGSERIAL  │◄─┐
│ email        UNIQUE     │  │
│ name                    │  │
│ password_hash           │  │
│ created_at              │  │
│ updated_at              │  │
└─────────────────────────┘  │
                             │ FK (ON DELETE CASCADE)
                             │
┌─────────────────────────┐  │
│       accounts          │  │
├─────────────────────────┤  │
│ id           BIGSERIAL  │  │
│ user_id      → users.id │──┘
│ name                    │
│ type         (CHECK)    │
│ currency     VARCHAR(3) │
│ current_balance         │
│ created_at              │
│ updated_at              │
└─────────────────────────┘
```

未來會加：
- `transactions` (Sprint 2)
- `categories` (Sprint 2)
- `import_jobs` (Sprint 3)
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

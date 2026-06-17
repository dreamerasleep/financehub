# 整體架構

## 元件圖

```
┌─────────────┐          ┌─────────────────┐         ┌─────────────┐
│             │          │                 │         │             │
│  Browser    │ ───────► │  React Frontend │ ──────► │  Spring     │
│  (使用者)   │  HTTPS   │  (Vite + AntD)  │  REST   │  Boot API   │
│             │          │                 │  + JWT  │             │
└─────────────┘          └─────────────────┘         └──────┬──────┘
                                                            │
                                                            │ JPA
                                                            ▼
                                                     ┌─────────────┐
                                                     │  PostgreSQL │
                                                     │     16      │
                                                     └─────────────┘
```

未來會接：

- **OCR 服務**（Sprint 4）
- **匯率 / 股價 API**（Sprint 5）— 每日排程拉資料

## 資料流範例：使用者新增帳戶

```
[使用者] ─點選「新增帳戶」─► [前端 React]
                                │
                                ▼
                       POST /api/v1/accounts
                       Authorization: Bearer <JWT>
                       Body: { name, type, currency, currentBalance }
                                │
                                ▼
                       [SecurityFilter] 驗證 JWT，取出 userId
                                │
                                ▼
                       [AccountController.create]
                                │
                                ▼
                       [AccountService.create(userId, ...)]
                                │
                                ▼
                       [AccountRepository.save]
                                │
                                ▼
                       [PostgreSQL: INSERT accounts ...]
                                │
                                ▼
                       回傳 201 + AccountResponse
```

## 部署架構（規劃中）

```
                    ┌────────────────────────┐
                    │      Render Cloud      │
                    │                        │
   ┌─────────┐      │  ┌──────────────────┐  │
   │ Browser │ ───► │  │  Static Site     │  │  (React build)
   └─────────┘      │  │  (前端)          │  │
                    │  └────────┬─────────┘  │
                    │           │ REST       │
                    │           ▼            │
                    │  ┌──────────────────┐  │
                    │  │  Web Service     │  │  (Spring Boot jar)
                    │  │  (後端)          │  │
                    │  └────────┬─────────┘  │
                    │           │ JDBC       │
                    │           ▼            │
                    │  ┌──────────────────┐  │
                    │  │  Managed Postgres│  │
                    │  └──────────────────┘  │
                    └────────────────────────┘
```

## 設計原則

1. **單一使用者範圍隔離**：所有查詢都會帶 `userId`；service 層保證一個 user 看不到別人的資料。
2. **無狀態 JWT**：後端不存 session；前端拿 token 自己保管。
3. **Migration first**：所有 schema 改動走 Flyway；JPA 設定為 `ddl-auto: validate`。
4. **測試以整合測試為主**：用 Testcontainers 跑真實 PostgreSQL，不用 H2。
5. **可拋棄式環境**：本機用 Docker Compose；上線用 Render Managed。換環境只改連線字串。

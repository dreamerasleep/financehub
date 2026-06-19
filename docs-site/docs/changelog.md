# 變更紀錄

採 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/) 格式。版本號採 [Semantic Versioning](https://semver.org/lang/zh-TW/)。

> 細部任務狀態以 repo 內 `docs/workbook.md` 為準；本檔聚焦**對使用者可感知的變化**。

---

## [Unreleased] — Sprint 3 完成

### 新增（後端）
- Flyway V4:`import_jobs`、`import_job_rows` 雙表 + JSONB raw、SHA-256 dedup hash
- CSV / XLSX 匯入:parser 抽象（Commons CSV + POI streaming）、RowResolver、ImportJobService、ImportCommitter（呼叫既有 TransactionService 完成餘額同步）
- 過期排程:`@Scheduled` 每小時把 24h 未確認的 job 標 EXPIRED
- 整合測試擴充至 39 筆（+19）+ 17 個 parser/resolver 單元測試

### 新增（前端）
- `/import` 頁:拖放上傳、預覽表（OK / 錯誤 / 重複狀態彩色 tag）、checkbox 規則（ERROR/DUPLICATE 不可勾）、部分確認匯入、取消
- 導覽列加「匯入」入口

---

## [0.3.5] — 2026-06-18 — Sprint 2.5

### 新增（後端）
- Flyway V3 migration：`transactions.to_account_id`、`category_id` 改 NULL、`type` CHECK 加 `TRANSFER`、`chk_transactions_transfer_shape` 跨欄位約束
- TRANSFER 交易：單筆 row 同時記錄來源 / 目標帳戶；`TransactionService` 在 `@Transactional` 內同步兩個帳戶餘額；更新 / 刪除會還原雙邊
- 驗證：拒絕相同帳戶轉帳、拒絕跨幣別、拒絕誤帶 `categoryId`
- 整合測試擴充至 20/20（新增 6 個 TRANSFER 案例）

### 新增（前端）
- 交易頁支援轉帳：類型多「轉帳」選項；轉帳模式隱藏分類、改顯示目標帳戶下拉（自動過濾同幣別、排除來源帳戶）
- 列表「帳戶」欄位轉帳顯示「來源 → 目標」
- 帳戶頁兩端餘額即時反映轉帳

---

## [0.3.0] — 2026-06-18 — Sprint 2 完成

### 新增（後端）
- Flyway V2 migration：`categories`、`transactions`、11 個系統分類種子
- 交易 CRUD API（`/api/v1/transactions` GET/POST/PUT/DELETE、`from`/`to` 日期過濾）
- 分類列出 API（`GET /api/v1/categories`，系統 + 自訂）
- `TransactionService` 在 `@Transactional` 內同步更新帳戶 `current_balance`，編輯 / 刪除會先還原再套用
- 後端整合測試 14/14 全綠（原 8 + 新增 6）

### 新增（前端）
- 交易列表頁（`/transactions`，依日期排序、type 顏色標籤、日期區間 filter）
- 新增 / 編輯 modal：類型 → 帳戶 → 分類（kind 連動）→ 金額 → 日期 → 備註
- 帳戶頁餘額即時反映交易變更
- 導覽列加上「交易」入口，預設首頁改 `/transactions`

---

## [0.2.0] — 2026-06-17 — Sprint 1 前端完成

### 新增（前端）
- React 19 + Vite 8 + TypeScript 6 + Ant Design 5 骨架
- 註冊 / 登入頁（tab 切換、AntD 表單驗證、中文化）
- 帳戶列表頁（受保護路由、CRUD modal、Popconfirm 刪除）
- axios JWT interceptor + 401 自動導向登入
- Zustand auth store（persist 至 localStorage）
- TanStack Query 管理 server state
- React Router v7 受保護路由 + 自動跳轉
- frontend-ci.yml（lint + typecheck + build）

---

## [0.1.0] — 2026-06-17

### 新增
- 使用者註冊（`POST /api/v1/auth/register`）
- 使用者登入並取得 JWT（`POST /api/v1/auth/login`）
- 取得自己資訊（`GET /api/v1/auth/me`）
- 帳戶 CRUD（`/api/v1/accounts` GET/POST/PUT/DELETE）
- 多帳戶、多幣別、5 種帳戶類型
- 跨使用者資料隔離
- API 健康檢查（`/api/v1/health`、`/actuator/health`）
- Swagger UI（`/swagger-ui.html`）
- 後端整合測試 8/8 全綠

### 基礎建設
- Java 21 + Spring Boot 3.3.4
- PostgreSQL 16 (Docker Compose 本機)
- Flyway 資料庫 migration V1
- Spring Security 6 + JWT
- GitHub Actions CI（actions/checkout@v5、setup-java@v5）
- Monorepo（`backend/`、`frontend/`、`docs/`）

---

## 路線圖

| 版本 | Sprint | 內容 | 預估 |
| ---- | ------ | ---- | ---- |
| 0.2.0 | S1 完成 | 前端 React UI 上線 | 1 週 |
| 0.3.0 | S2 | 交易紀錄 + 手動輸入 | 2 週 |
| 0.4.0 | S3 | CSV / Excel 匯入 | 2 週 |
| 0.5.0 | S4 | 收據 OCR | 2 週 |
| 0.6.0 | S5 | 公開 API（匯率 / 股價）| 2 週 |
| 0.7.0 | S6 | Dashboard + ECharts | 2 週 |
| 1.0.0 | S7 | Render 上線 + 強化 | 3 週 |

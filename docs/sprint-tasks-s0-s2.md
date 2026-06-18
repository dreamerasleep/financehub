# FinanceHub — Sprint 0 ~ Sprint 2 任務拆解（ClickUp 匯入藍圖）

> 來源：`docs/plan.md` Sprint 計畫
> 用途：作為 ClickUp MCP 自動建立 Task 的 source of truth
> 命名規則：`[Sprint]-[FeatureID 或編號] {標題}`
> 狀態欄位：Backlog / To Do / In Progress / In Review / Done / Blocked

---

## Sprint 0 — 基礎建設（Week 1–2, 20 pt）

目標：骨架 + CI/CD 可運行；Render 看到 Hello World；ClickUp 與文件結構就緒。

| 編號 | 標題 | Epic | Tech Area | SP | Acceptance Criteria | 初始狀態 |
|------|------|------|-----------|----|----|----------|
| S0-T01 | 建立 GitHub Monorepo (backend + frontend) | Infra | 基礎設施 | 1 | repo 已建立、main 分支已 push | Done |
| S0-T02 | 安裝 Java 21 + Maven Wrapper | Infra | 後端 | 1 | `./mvnw -v` 成功；CI 使用 Java 21 | Done |
| S0-T03 | Spring Boot 3.3 骨架 + Health endpoint | Infra | 後端 | 2 | `/actuator/health` 回 200；`./mvnw test` 通過 | Done |
| S0-T04 | GitHub Actions CI（後端測試 + lint） | Infra | 基礎設施 | 2 | push 後 CI 綠燈 | Done |
| S0-T05 | Docker 化後端 + 本地 docker-compose（含 Postgres 16） | Infra | 基礎設施 | 3 | `docker compose up` 起得來、能連 DB | To Do |
| S0-T06 | Flyway 設定 + 第一支 migration（users 表骨架） | Infra | 後端 | 2 | `flyway:migrate` 通過 | To Do |
| S0-T07 | Render 帳號 + Web Service + Postgres 連線 | Infra | 基礎設施 | 3 | Render 部署成功；`/actuator/health` 可外連 | To Do |
| S0-T08 | Vite + React 18 + TS + Ant Design 骨架 | Infra | 前端 | 2 | `npm run dev` 啟動成功；Hello FinanceHub 頁可見 | To Do |
| S0-T09 | 前端 ESLint + Prettier + Vitest 工具鏈 | Infra | 前端 | 1 | `npm run lint`、`npm run test` 通過 | To Do |
| S0-T10 | ClickUp Workspace + Sprint Lists + 自訂欄位 | PM | 基礎設施 | 1 | 8 個 Sprint List 建好；自訂欄位齊全 | In Progress |
| S0-T11 | docs/ 文件補齊（README、架構圖、PR 模板） | PM | 文件 | 2 | README 有跑通指令；PR template 啟用 | To Do |

---

## Sprint 1 — 認證 + 帳戶（Week 3–4, 24 pt）

目標：JWT 登入/登出、帳戶 CRUD、Swagger UI、React 登入頁。

| 編號 | 標題 | Epic | Feature | Tech Area | SP | Acceptance Criteria |
|------|------|------|---------|-----------|----|----|
| S1-T01 | User Entity + UserRepository + Flyway migration | 系統功能 | F-050 | 後端 | 2 | 建表成功；單元測試覆蓋 Repository |
| S1-T02 | Spring Security 設定 + PasswordEncoder | 系統功能 | F-050 | 後端 | 3 | SecurityFilterChain 設定通過；bcrypt 編碼可用 |
| S1-T03 | JWT 簽發/驗證模組（access + refresh） | 系統功能 | F-050 | 後端 | 3 | token 簽發、解碼、過期測試通過 |
| S1-T04 | POST /api/auth/register API | 系統功能 | F-050 | 後端 | 2 | 整合測試：註冊成功、重複 email 回 409 |
| S1-T05 | POST /api/auth/login API（回 access+refresh token） | 系統功能 | F-050 | 後端 | 2 | 帳密正確 200、錯誤 401；測試通過 |
| S1-T06 | POST /api/auth/refresh API | 系統功能 | F-050 | 後端 | 1 | refresh token 換新 access token |
| S1-T07 | Account Entity + Flyway migration | 交易管理 | — | 後端 | 1 | accounts 表建立；FK to users |
| S1-T08 | Account CRUD API（含 user_id 隔離） | 交易管理 | — | 後端 | 3 | 5 個端點 + 整合測試；他人帳戶 404 |
| S1-T09 | Springdoc OpenAPI 設定 + /swagger-ui | 系統功能 | — | 後端 | 1 | Swagger UI 看得到所有 Auth/Account 端點 |
| S1-T10 | 前端 Login 頁 + JWT 儲存（zustand） | 系統功能 | F-050 | 前端 | 3 | 登入成功跳轉、token 存進 store |
| S1-T11 | 前端 Axios interceptor（自動加 Bearer + 401 處理） | 系統功能 | F-050 | 前端 | 2 | 401 自動嘗試 refresh 或登出 |
| S1-T12 | 前端 Account 列表頁 + 新增/編輯 Modal | 交易管理 | — | 前端 | 1 | 列表顯示、Modal 新增成功後刷新 |

---

## Sprint 2 — 交易核心（Week 5–6, 30 pt）

目標：交易 CRUD API、分類管理、交易列表頁（搜尋/篩選/分頁）。

| 編號 | 標題 | Epic | Feature | Tech Area | SP | Acceptance Criteria |
|------|------|------|---------|-----------|----|----|
| S2-T01 | Category Entity + 預設分類樹 seed | 分類與標籤 | F-010 | 後端 | 3 | 預設分類 seed migration 跑成功 |
| S2-T02 | Category CRUD API（含父子層級） | 分類與標籤 | F-010,F-011 | 後端 | 3 | 樹狀查詢端點；不可刪有交易的分類 |
| S2-T03 | Transaction Entity + Flyway migration（含 tags jsonb） | 交易管理 | F-001 | 後端 | 2 | 表建立；tags 用 jsonb |
| S2-T04 | Transaction CRUD API（含類別/帳戶/標籤校驗） | 交易管理 | F-001,F-002 | 後端 | 4 | 5 個端點；單元 + 整合測試通過 |
| S2-T05 | Transaction 查詢 API（分頁/排序/日期範圍/類別/標籤篩選） | 交易管理 | F-001 | 後端 | 4 | Pageable + Specification；測試覆蓋多組條件 |
| S2-T06 | 帳戶餘額即時更新（DomainEvent + Listener） | 交易管理 | — | 後端 | 3 | 新增/刪除交易後餘額正確；併發測試通過 |
| S2-T07 | 前端 Category 樹狀管理頁 | 分類與標籤 | F-010,F-011 | 前端 | 2 | 顯示樹、可新增子分類 |
| S2-T08 | 前端 Transaction 列表頁（Ant Design Table + 分頁） | 交易管理 | F-001 | 前端 | 3 | 列表載入、分頁切換正確 |
| S2-T09 | 前端 Transaction 篩選列（日期、類別、類型、標籤） | 交易管理 | F-001 | 前端 | 3 | URL query string 同步、F5 不丟篩選 |
| S2-T10 | 前端 Transaction 新增/編輯 Drawer | 交易管理 | F-001 | 前端 | 2 | 必填驗證、儲存後 invalidate React Query cache |
| S2-T11 | 前端 多標籤 input（自由輸入 + autocomplete） | 分類與標籤 | F-012 | 前端 | 1 | 可新增、刪除、autocomplete 列出已用標籤 |

---

## 對應 ClickUp 結構

```
Workspace: FinanceHub
└── Space: Development
    ├── Folder: Sprints
    │   ├── List: S0 基礎建設        ← S0-T01 ~ S0-T11
    │   ├── List: S1 認證+帳戶       ← S1-T01 ~ S1-T12
    │   └── List: S2 交易核心        ← S2-T01 ~ S2-T11
    ├── Folder: Backlog              ← (S3~S7 留空，後續細化)
    ├── Folder: Bugs
    └── Folder: Documentation
```

自訂欄位（每張 Task）：
- `Epic` (dropdown): 交易管理 / 分類與標籤 / 儀表板 / 預算管理 / 報表與匯出 / 系統功能 / Infra / PM
- `Feature ID` (text): F-001 等對應 PRD 編號
- `Story Points` (number)
- `Tech Area` (dropdown): 後端 / 前端 / 基礎設施 / 文件 / AI 協作
- `Sprint` (relationship → Sprint List)
- `GitHub PR` (URL)
- `Acceptance Criteria` (long text)

---

## 後續執行步驟

1. ClickUp Personal API Token 與 Team ID 設定到 shell 環境變數（見 README）。
2. 重啟 Claude Code 讓 `clickup` MCP 啟動。
3. 告知 Claude「依 `sprint-tasks-s0-s2.md` 建立 ClickUp 任務」，會依序：
   - 取得（或建立） Space `Development`
   - 建立 Folder `Sprints` 與 3 個 Sprint List
   - 建立自訂欄位
   - 依本檔表格批次建立 Task（含 Acceptance Criteria 寫進 Description）

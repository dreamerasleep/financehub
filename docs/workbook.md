# FinanceHub 工作手冊 (Workbook)

> 本檔為**單一狀態真相來源**。每完成一段工作後即時更新「進度日誌」與下方任務狀態。
> 詳細設計請見 [`plan.md`](./plan.md)；Sprint 任務藍圖請見 [`sprint-tasks-s0-s2.md`](./sprint-tasks-s0-s2.md)。

- 最近更新：2026-06-22
- 目前位置：**Sprint 3.5（Import 預覽頁強化完成，前後端皆綠；MCP E2E 5 情境保留手動驗證）**
- GitHub Repo：[dreamerasleep/financehub](https://github.com/dreamerasleep/financehub)（public）

---

## 1. 總覽（Status at a glance）

| Sprint | 期程 | 主題 | 狀態 |
| ------ | ---- | ---- | ---- |
| S0 | W1 | 基礎建設 / Bootstrap | ✅ 完成 |
| S1 | W2–3 | 使用者驗證 + 帳戶 CRUD | ✅ 完成（前後端） |
| S2 | W4–5 | 交易紀錄 + 手動輸入 | ✅ 完成（前後端） |
| S2.5 | W5 尾 | 轉帳交易（同幣別） | ✅ 完成（前後端） |
| S3 | W6–7 | CSV / Excel 匯入 | ✅ 完成 |
| S3.5 | W7 尾 | Import 預覽頁強化（filter / bulk / inline edit） | ✅ 完成（前後端，E2E 待手動） |
| S4 | W8–9 | 收據 OCR | ⏳ 規劃中 |
| S5 | W10–11 | 公開 API 整合（匯率 / 股價） | ⏳ 規劃中 |
| S6 | W12–13 | Dashboard + ECharts 視覺化 | ⏳ 規劃中 |
| S7 | W14–16 | 上線部署（Render）+ 強化 | ⏳ 規劃中 |

圖例：✅ 完成 · 🟢 進行中 · 🟡 部分完成 · ⏳ 未開始 · ⛔ 受阻

---

## 2. 已完成工作（Done）

### Sprint 0 — 基礎建設

- ✅ 安裝 Java 21（Homebrew openjdk@21）、Maven Wrapper、`gh` CLI
- ✅ 建立 Monorepo 結構：`backend/`、`frontend/`、`docs/`、`scripts/`、`docker-compose.yml`、`setenv.sh`
- ✅ Spring Boot 3.3.4 後端骨架（`com.financehub`）
  - `FinanceHubApplication.java` + `HealthController` (`GET /api/v1/health`)
  - `FinanceHubApplicationTests`、`HealthControllerTest` 兩個基礎測試
- ✅ Git 初始化 + 第一次 commit
- ✅ 建立 GitHub private repo `dreamerasleep/financehub`、設定 git credential helper
- ✅ GitHub Actions CI：`backend-ci.yml`（actions/checkout@v5、setup-java@v5、JDK 21、Maven）

### Sprint 1 — 使用者驗證 + 帳戶 CRUD（後端）

- ✅ Docker Compose 起 PostgreSQL 16（user/pwd：financehub/financehub_dev，db：financehub，port 5432）
- ✅ Flyway 第一支 migration：`V1__init_users_and_accounts.sql`
  - `users` table：id / email(unique) / name / password_hash / created_at / updated_at
  - `accounts` table：id / user_id(FK) / name / type(CHECK) / currency / current_balance / 時間戳
- ✅ JPA 實體：`User`、`Account`（含 `@PrePersist`/`@PreUpdate` 自動時間戳）
- ✅ Repository：`UserRepository`（findByEmail / existsByEmail）、`AccountRepository`（按 user_id 篩選）
- ✅ Spring Security 6 設定：
  - 無狀態 session、CORS 對 `http://localhost:5173` 開啟
  - 公開端點：register / login / health / swagger
  - `JwtAuthFilter` 接在 `UsernamePasswordAuthenticationFilter` 之前
  - 401 用 `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)` 替換預設 403
- ✅ JWT：`JwtProperties`（secret + expiration PT24H）、`JwtService`（HMAC-SHA）、`AuthenticatedUser` principal
- ✅ AuthService：register（重複 email 擲 IllegalArgumentException）、login（密碼錯擲 BadCredentialsException）、BCrypt 雜湊
- ✅ REST：
  - `POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`GET /api/v1/auth/me`
  - `GET/POST/PUT/DELETE /api/v1/accounts`（user 範圍隔離）
- ✅ 測試（含 Testcontainers，postgres:16-alpine）：**8/8 全綠**
  - `AuthFlowIT`：register+login+me、未驗證 401、密碼錯 401
  - `AccountCrudIT`：完整 CRUD、跨使用者隔離 404、未驗證 401
- ✅ 端對端 smoke test（Python urllib）：10/10 endpoints 全通
- ✅ Surefire 設定 `*IT.java` 納入測試掃描
- ✅ Commit `33983b8`「feat(backend): add JWT auth and account CRUD (Sprint 1)」並 push 至 GitHub

### 其他

- ✅ 修正 `.claude/settings.json` 三個無效 permission rule
- ✅ 撰寫 `docs/plan.md`（BRD / MRD / PRD / 16 週 Sprint plan / Render 部署）
- ✅ 撰寫 `docs/sprint-tasks-s0-s2.md`（34 task / 74 點）
- ✅ ClickUp 同步腳本 `scripts/clickup_sync.py` + `clickup_tasks.yml`（freemium 限制下走 YAML+REST）

---

## 3. 進行中（In progress）

目前**無**進行中項目。下一步：Sprint 4（收據 OCR）。

---

## 4. 待處理工作（Backlog，依優先序）

### 立即下一步（Sprint 1 收尾）

- [x] **F-01**：建立 React + Vite + TypeScript 前端骨架（`frontend/`）
- [x] **F-02**：安裝 Ant Design 5、React Router、axios、Zustand、React Query
- [x] **F-03**：axios interceptor（自動帶 JWT、401 導向登入）
- [x] **F-04**：登入頁、註冊頁
- [x] **F-05**：帳戶列表頁（受保護路由 + AntD Table + 新增/編輯/刪除 modal）
- [x] **F-06**：`.github/workflows/frontend-ci.yml`（lint + typecheck + build）
- [x] **F-07**：前端 README 與啟動指令（`frontend/README.md` 含 Quickstart、scripts、目錄結構、proxy 說明、已知限制）

### Sprint 2 — 交易紀錄 + 手動輸入

- [x] **T-01**：`V2__transactions_and_categories.sql`（INCOME/EXPENSE + 11 個系統分類）
- [x] **T-02**：JPA 實體 + Repository + 餘額自動更新（`TransactionService` @Transactional）
- [x] **T-03**：交易 CRUD API（`/api/v1/transactions`，`from`/`to` 日期過濾）
- [x] **T-04**：交易 IT 測試 6 個（CRUD、餘額同步、編輯/刪除回滾、kind 不符 400、跨使用者 404、未驗證 401）
- [x] **T-05**：前端 `/transactions` 頁（列表 + 區間 filter + CRUD modal + Popconfirm）
- [x] **T-06**：分類 API（`GET /api/v1/categories`，種子 4 INCOME + 7 EXPENSE）
- [x] **T-07**：轉帳交易（單筆 + `to_account_id`、同幣別、雙邊餘額同步）→ Sprint 2.5 完成；跨幣別待 Sprint 5 匯率服務

### Sprint 3 — CSV / Excel 匯入

- [x] CSV 解析（Apache Commons CSV）+ 預覽 + 確認入庫
- [x] Excel 解析（Apache POI）
- [x] 匯入錯誤回報（哪一列、什麼錯）
- [x] 前端拖放上傳與預覽 UI

### Sprint 4 — 收據 OCR

- [ ] OCR 服務選型（Google Vision / Tesseract local）
- [ ] 圖片上傳 + 暫存
- [ ] 解析金額 / 商家 / 日期 → 預填交易表單
- [ ] 前端拍照 / 拖曳上傳介面

### Sprint 5 — 公開 API 整合

- [ ] 匯率（exchangerate.host / 央行）每日排程
- [ ] 股價（Yahoo Finance / Alpha Vantage）
- [ ] 投資帳戶持倉 + 自動估值

### Sprint 6 — Dashboard + 視覺化

- [ ] ECharts 整合
- [ ] 月度收支折線、分類甜甜圈、淨值趨勢
- [ ] 預算 vs 實際對比
- [ ] 自訂日期區間

### Sprint 7 — 上線

- [ ] Render Postgres（managed）provisioning
- [ ] Render Backend service（Docker / Native build）
- [ ] Render Static site（前端）
- [ ] 環境變數管理（DB、JWT secret、CORS origin）
- [ ] 監控 / log（Render dashboard + 自訂 health check）
- [ ] 自動化資料備份排程

### 跨 Sprint 雜項

- [ ] 將 `docs/sprint-tasks-s0-s2.md` 同步進 ClickUp 並維持同步
- [ ] 寫一份「本機開發 Quickstart」README（含 `docker compose up`、`./mvnw spring-boot:run`、`npm run dev`）
- [ ] CI 加上覆蓋率報告（JaCoCo + Codecov）
- [ ] 把 OpenAPI spec 匯出為靜態檔放進 docs
- [x] 建立 GitBook 風格文件站（`docs-site/`，MkDocs Material）
- [ ] 在 GitHub repo Settings → Pages → Source 設為 GitHub Actions（首次部署需手動）
- [ ] 設定 docs-site 自訂網域（選用）

---

## 5. 風險 / 待決議事項

| # | 項目 | 描述 | 備註 |
| - | ---- | ---- | ---- |
| R1 | OCR 供應商 | Google Vision（精準、需金鑰）vs Tesseract（本機、需訓練）| Sprint 4 前需決定 |
| R2 | 股價 API | Yahoo 非官方易壞 / Alpha Vantage 限額嚴 | Sprint 5 前需決定 |
| R3 | Render 等級 | Free tier 會 sleep；$7 Starter 才常駐 | 部署前確認預算 |
| R4 | OAuth / SSO | 是否導入 Google 登入？| 視個人使用便利性 |
| R5 | JWT 存 localStorage | XSS 可竊（push 後 security review MEDIUM）| Sprint 7 上線前換 httpOnly Cookie + refresh token |

---

## 6. 進度日誌（Changelog）

> 每次告一段落時新增一筆條目（最新在最上方）。

### 2026-06-22 — Sprint 3.5 完成（Import 預覽頁強化）

- 後端：新增 `PATCH /api/v1/imports/{jobId}/rows/{rowId}`；`ImportJobService.patchRow` 重跑 RowResolver、recompute job counters；兩個新例外（`OkRowNotEditableException`、`JobNotPendingException`）對應 403 / 409，body 用 `error` 欄帶 lowercase snake code
- 測試：`ImportRowPatchIT` 11 案綠（含 ERROR→OK、ERROR→DUP、DUP→OK、403、404、409、counters、不 retroactively flip），總 IT 60/60 綠
- 前端：拆出 `ImportToolbar`（filter + bulk 按鈕）、`ImportRowEditDrawer`（AntD Form + PATCH mutation）；移除 `effectiveSelection` fallback bug（使用者「清空」/「反選」現在實際生效）；裝 vitest 跑 selection helpers 7 案綠；`tsc -b` / `eslint` 皆綠
- 分支：`feature/sprint-3.5-import-preview`，C1 `5c4dc5d`（backend PATCH + IT）、C2 `b45078c`（toolbar + helpers + vitest）、C3 `e6985d2`（drawer + ImportPage 整合）、C4 `<本 commit>`（docs sync）
- E2E：MCP 探索式 5 情境清單列於 `docs/superpowers/plans/2026-06-20-import-preview-enhancements.md` §Task 06，本回合**未自動執行**，留待後續手動驗證
- 文件：`user-guide/import.md` 新增「修正錯誤列」「篩選與批次選取」；`api-reference/imports.md` 新增 PATCH 段落；`changelog.md` 新增 Sprint 3.5 unreleased
- **下一步**：T06 手動 MCP 驗證 → Sprint 4（收據 OCR）或 R1 風險決議（Google Vision vs Tesseract）

### 2026-06-19 — Sprint 3 完成（CSV / Excel 匯入）

- 後端：Flyway V4 加 `import_jobs`、`import_job_rows` 雙表（JSONB raw、SHA-256 dedup hash、`ON DELETE SET NULL` FK 設計），`expires_at` 預設 +24h
- `TransactionFileParser` 抽象 + `CsvTransactionFileParser`（Apache Commons CSV）/ `XlsxTransactionFileParser`（Apache POI XSSFWorkbook）；UTF-8 BOM/charset 自動處理
- `RowResolver` 把每列 raw 解成 `parsed_*` + `dedup_hash`；錯誤訊息對應 user-facing 表（帳戶不存在、分類不符、跨幣別轉帳等）
- `ImportJobService` 上傳 → 解析 → 全列入 staging；`ImportCommitter` 在 `@Transactional` 內呼叫既有 `TransactionService`，餘額同步沿用、無重複邏輯
- 確認 commit 前以 `PESSIMISTIC_WRITE` 鎖 OK 列重跑 resolver，期間若有資料變動會把列改回 ERROR 並回 409
- `ExpiryScheduler`（`@Scheduled`）每小時把 24h 未確認的 job 標 `EXPIRED`
- 13 MB / 10000 列上限：multipart 413 與 row-count 400 handler 一併補上
- 後端測試擴充至 **39/39 IT**（+19）+ 17 個 parser/resolver 單元測試全綠
- 前端：`/import` 頁（AntD Dragger + Table + Statistic + Popconfirm）；OK 預設全勾、ERROR/DUPLICATE checkbox 強制 disabled；確認鈕顯示選取列數；commit 後 `invalidateQueries(['transactions','accounts'])` 並導向 `/transactions`
- 導覽列新增「匯入」入口
- Playwright E2E（單檔 6 列：OK 2 / ERROR 2 / DUPLICATE 2）：預覽計數、選取規則、部分 commit、餘額更新（10000 → 8549.50）、重上傳 dedup 全部驗證通過；E2E 不需修 code → 不開額外 commit
- 文件：新增 `user-guide/import.md`、`api-reference/imports.md`；`architecture/database.md` 補 V4 兩表；`changelog.md`、`index.md`、`mkdocs.yml` 同步
- **下一步**：Sprint 4（收據 OCR）或 R1 風險決議（Google Vision vs Tesseract）

### 2026-06-18 — Sprint 2.5 完成（TRANSFER 轉帳）

- 後端：Flyway V3 加 `transactions.to_account_id`、`category_id` 改 NULL、`type` CHECK 加 `TRANSFER`、新增 `chk_transactions_transfer_shape` 跨欄位約束
- `TransactionService` 重構支援 TRANSFER：`@Transactional` 內同步「來源 −amount、目標 +amount」，update / delete 會還原雙邊舊金額再套新值
- 形狀驗證：拒絕 from == to、跨幣別、誤帶 categoryId、TRANSFER 缺 toAccountId 等
- DTO 與 controller 加 `toAccountId` 欄位（INCOME/EXPENSE 仍向後相容）
- 新增 `TransactionTransferIT`（6 案）：成功、編輯切換目標雙邊同步、刪除回滾、same account 400、跨幣別 400、跨使用者 404
- 後端測試全綠：**20/20**（原 14 + 新 6）
- 前端：類型選單加「轉帳」；轉帳模式隱藏分類欄位、顯示「目標帳戶」下拉（自動過濾同幣別、排除來源帳戶）
- 列表「帳戶」欄位轉帳顯示「來源 → 目標」；金額不帶正負號、改藍色
- 帳戶頁兩端餘額即時反映（既有 `accounts` invalidate 已涵蓋）
- LoginPage 預設導向 `/accounts` → `/transactions`（保留 `from.pathname` fallback）
- F-07 `frontend/README.md` 補完（Quickstart、scripts、目錄結構、proxy、已知限制）
- 文件：`api-reference/transactions.md`、`architecture/database.md`、`user-guide/transactions.md`、`changelog.md`、`index.md` 同步更新；`mkdocs build --strict` 通過

### 2026-06-18 — Sprint 2 完成

- 後端：Flyway V2 加 `categories` + `transactions`，11 個系統分類種子
- 新增 `TransactionService`（@Transactional 同步 `accounts.current_balance`，編輯/刪除回滾舊金額）
- 新增 `CategoryService` 與 `CategoryController`（系統 + 自訂分類列表）
- `TransactionController` 提供 CRUD + `from`/`to` 日期區間過濾
- 整合測試 `TransactionCrudIT` 6 個案例：CRUD、餘額同步、編輯/刪除回滾、kind 不符 400、跨使用者 404、未驗證 401
- 後端測試全綠：**14/14**（原 8 + 新 6）
- 前端：`/transactions` 頁（列表 + 區間 filter + CRUD modal + Popconfirm）
- 表單 type 切換時自動清空不符 kind 的分類值
- 帳戶頁餘額即時反映交易變更（invalidate `accounts` query）
- 導覽列新增「交易」入口；預設首頁改 `/transactions`
- lint + tsc + vite build 全綠（bundle 1.31 MB，仍待 code-split）
- 新增 R5 風險：JWT 存 localStorage，計畫 Sprint 7 換 httpOnly Cookie
- 同步更新 `docs-site/`：新增 `user-guide/transactions.md` 與 `api-reference/transactions.md`、更新資料庫設計、API 索引、首頁、changelog、mkdocs nav
- **下一步**：F-07 README 或 Sprint 3（CSV / Excel 匯入）

### 2026-06-17（夜段） — Sprint 1 前端完成

- 用 `npm create vite@latest` 建立 React 19 + TS 6 + Vite 8 骨架
- 安裝 AntD 5、React Router 7、Zustand 5、TanStack Query 5、axios
- 完成：登入/註冊頁、帳戶 CRUD 頁、AppLayout、ProtectedRoute、axios interceptor
- Vite proxy 設定 `/api` → `localhost:8080` 避開本機 CORS
- 路徑別名 `@/*` → `src/*`
- 新增 `frontend-ci.yml`（lint + typecheck + build）
- E2E 用 Playwright 驗證：註冊→自動登入→新增→編輯→刪除→登出，全綠
- 同步更新 `docs-site/`：新增 `architecture/frontend.md`、更新 changelog 與首頁狀態表
- **下一步**：補 `frontend/README.md`（F-07），或進 Sprint 2（交易紀錄）

### 2026-06-17（晚段） — 建立文件站

- 新增 `docs-site/`（MkDocs Material）：首頁、開始使用、使用者手冊、系統架構、API 參考、維運手冊、名詞解釋、變更紀錄
- `mkdocs.yml`：繁中、Material 主題、深淺主題切換、搜尋多語
- 新增 `.github/workflows/docs.yml`：`mkdocs build --strict` + 部署 GitHub Pages
- workbook 補上「文件站自動同步規則」章節（每次改功能必改對應 .md）
- **下一步**：需在 GitHub Settings → Pages → Source 切到 GitHub Actions 啟用部署

### 2026-06-17 07:30 — Sprint 1 後端完成

- 完成 JWT 驗證 + 帳戶 CRUD，8/8 測試綠 + 10/10 smoke endpoints OK
- Commit `33983b8` 推上 GitHub
- 修正 `@PrePersist`/`@PreUpdate` 解決時間戳 null 問題
- Surefire 加上 `*IT.java` include
- 401 改為 `HttpStatusEntryPoint`（原本 Spring Security 6 預設回 403）
- **下一步**：等使用者確認後啟動前端骨架（F-01 ~ F-07）

### 2026-06-16 ~ 17 — Sprint 0 + Sprint 1 後端

- 完成 monorepo、Spring Boot 3.3、Docker PG、Flyway、GitHub repo + Actions CI v5
- 撰寫 plan.md、sprint-tasks blueprint、ClickUp 同步腳本
- 修正 `.claude/settings.json` 三個 permission rule 錯誤

---

## 7. 維護規則（給未來的我 / Claude）

### 本檔（workbook.md）

- 每完成一段工作 → 更新「2. 已完成」「6. 進度日誌」，並把該項目從「4. 待處理」移除或勾掉
- 開始新工作 → 在「3. 進行中」寫一行（含預計產出）
- 每個 Sprint 結束 → 在「1. 總覽」更新狀態欄
- 風險改變 → 更新「5. 風險」表
- **不要把這份檔案變成日記**：保留**目前狀態**與**最近 5 筆**進度日誌即可，更舊的搬到 `docs/archive/`

### 文件站（`docs-site/`）— 自動同步規則

任何**對使用者或開發者可感知的改動**都要連帶更新 `docs-site/` 對應頁：

| 改了什麼 | 必改文件 |
| -------- | -------- |
| 新 API 端點 / 改 request/response | `docs-site/docs/api-reference/<module>.md` |
| 新使用者可見功能 | `docs-site/docs/user-guide/<feature>.md` |
| 新 / 改 DB schema | `docs-site/docs/architecture/database.md` |
| 新環境變數 / 啟動方式變更 | `docs-site/docs/operations/local-dev.md` + `deployment.md` |
| 任何已上線功能變更 | `docs-site/docs/changelog.md` 加一筆 |
| 新增 / 移除 nav 頁 | `docs-site/mkdocs.yml` 的 `nav:` |

PR 內若改 `backend/` 或 `frontend/` 但沒改對應 docs，**視為未完成**。

CI（`.github/workflows/docs.yml`）會跑 `mkdocs build --strict`，死連結或孤兒檔案會 fail。

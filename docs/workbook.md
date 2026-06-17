# FinanceHub 工作手冊 (Workbook)

> 本檔為**單一狀態真相來源**。每完成一段工作後即時更新「進度日誌」與下方任務狀態。
> 詳細設計請見 [`plan.md`](./plan.md)；Sprint 任務藍圖請見 [`sprint-tasks-s0-s2.md`](./sprint-tasks-s0-s2.md)。

- 最近更新：2026-06-17
- 目前位置：**Sprint 1（後端完成，前端待開工）**
- GitHub Repo：[dreamerasleep/financehub](https://github.com/dreamerasleep/financehub)（private）

---

## 1. 總覽（Status at a glance）

| Sprint | 期程 | 主題 | 狀態 |
| ------ | ---- | ---- | ---- |
| S0 | W1 | 基礎建設 / Bootstrap | ✅ 完成 |
| S1 | W2–3 | 使用者驗證 + 帳戶 CRUD | 🟡 後端完成、前端未開工 |
| S2 | W4–5 | 交易紀錄 + 手動輸入 | ⏳ 規劃中 |
| S3 | W6–7 | CSV / Excel 匯入 | ⏳ 規劃中 |
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

目前**無**進行中項目（等待使用者裁示是否進入前端開發）。

---

## 4. 待處理工作（Backlog，依優先序）

### 立即下一步（Sprint 1 收尾）

- [ ] **F-01**：建立 React + Vite + TypeScript 前端骨架（`frontend/`）
- [ ] **F-02**：安裝 Ant Design 5、React Router、axios、Zustand、React Query
- [ ] **F-03**：axios interceptor（自動帶 JWT、401 導向登入）
- [ ] **F-04**：登入頁、註冊頁
- [ ] **F-05**：帳戶列表頁（受保護路由 + AntD Table + 新增/編輯/刪除 modal）
- [ ] **F-06**：`.github/workflows/frontend-ci.yml`（lint + typecheck + build）
- [ ] **F-07**：前端 README 與啟動指令

### Sprint 2 — 交易紀錄 + 手動輸入

- [ ] **T-01**：`transactions` table migration（amount / type / category / occurred_at / note / account_id FK）
- [ ] **T-02**：JPA 實體 + Repository + 餘額自動更新（事務性）
- [ ] **T-03**：交易 CRUD API（含分頁、日期範圍查詢、分類查詢）
- [ ] **T-04**：交易 IT 測試（含跨使用者隔離、餘額同步驗證）
- [ ] **T-05**：前端交易頁面（清單 + 新增 modal + 篩選）
- [ ] **T-06**：分類（categories）種子資料與管理 API

### Sprint 3 — CSV / Excel 匯入

- [ ] CSV 解析（Apache Commons CSV）+ 預覽 + 確認入庫
- [ ] Excel 解析（Apache POI）
- [ ] 匯入錯誤回報（哪一列、什麼錯）
- [ ] 前端拖放上傳與預覽 UI

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

---

## 6. 進度日誌（Changelog）

> 每次告一段落時新增一筆條目（最新在最上方）。

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

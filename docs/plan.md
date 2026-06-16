# FinanceHub — 個人財務管理系統 規劃方案

> 版本：v1.0 (草案)
> 建立日期：2026-06-16
> 部署目標：Render 全套 (Static + Web Service + PostgreSQL)
> 開發模式：本人 + Claude Code AI 協作

---

## Context（為何啟動本專案）

使用者為 45 歲、銀行退役工程師，具備 Java/Spring Boot/JavaScript/HTML/CSS/DB2/MSSQL 背景。當前以分散的 Excel 與紙本記錄管理個人財務，存在以下痛點：

- 帳單來源多（信用卡、現金、轉帳、發票），需手動彙整
- 缺乏視覺化儀表板，難以快速掌握收支全貌
- 月度結算耗時，無法持續追蹤預算
- 第三方記帳 App 涉及隱私與資料自主性疑慮

目標是打造一套自主部署、易維護、可擴展的個人財務中樞，並透過 AI 協作以最少人力完成。

---

## 一、需求文件

### 1.1 BRD — 商業需求文件

| 項目 | 內容 |
|------|------|
| 目標 | 取代分散 Excel 記帳，建立統一財務管理平台 |
| 使用者 | 單一主要使用者（本人），預留家庭帳號擴展 |
| 核心痛點 | 帳單分散、人工耗時、缺乏視覺化全貌 |
| 成功指標 | 每月記帳時間 < 30 分鐘；儀表板 < 5 秒看懂現況 |
| 隱私合規 | 資料存於個人雲端帳號，不分享第三方 |

**MoSCoW 優先順序：**

- **Must Have**：交易 CRUD、分類管理、月度報表、CSV 匯入
- **Should Have**：OCR 圖片辨識、儀表板圖表、預算管理
- **Could Have**：匯率/台股 API、AI 消費洞察
- **Won't Have (v1)**：行動 App、多幣別自動 P&L、家庭多帳號

---

### 1.2 MRD — 市場需求 / 競品分析

| 工具 | 痛點 | FinanceHub 差異化 |
|------|------|-------------------|
| 記帳城市 / Moze | 訂閱費、資料不自主 | 自主部署、資料完全自有 |
| Excel / Notion | 手動、無視覺化 | 自動匯入 + 智慧分析 |
| Mint / YNAB | 不支援台灣銀行、隱私疑慮 | 台灣在地化 + 隱私自主 |

**定位：** 工程師自主開發、資料完全自有、可客製化規則的個人財務中樞。

---

### 1.3 PRD — 產品功能需求

**Epic 1 — 交易管理**
- F-001：新增/編輯/刪除交易（金額、日期、類別、備註、標籤）
- F-002：支援收入、支出、轉帳三種類型
- F-003：CSV/Excel 匯入（自動欄位對應 + 重複偵測）
- F-004：OCR 圖片上傳 → 自動萃取金額/日期/商家
- F-005：公開 API 整合（匯率：Frankfurter；台股：TWSE 公開資料）

**Epic 2 — 分類與標籤**
- F-010：預設分類樹（餐飲、交通、醫療、娛樂、投資…）
- F-011：自訂子分類
- F-012：多標籤支援

**Epic 3 — 儀表板**
- F-020：月收支摘要卡片
- F-021：支出類別圓餅圖（本月 vs 上月）
- F-022：月度趨勢折線圖（近 12 個月）
- F-023：預算進度條
- F-024：近期交易列表

**Epic 4 — 預算管理**
- F-030：按類別設定月預算
- F-031：超支警示

**Epic 5 — 報表與匯出**
- F-040：月/季/年報表
- F-041：PDF / CSV 匯出
- F-042：資產負債快照

**Epic 6 — 系統功能**
- F-050：JWT 身份驗證（預留多帳號擴展）
- F-051：文件附件儲存（發票圖片 → Render Disk / S3）
- F-052：資料備份 / 還原

---

## 二、系統架構

### 2.1 整體架構圖

```
                ┌────────────────────────────┐
                │      End User (Browser)    │
                └──────────────┬─────────────┘
                               │ HTTPS
                               ▼
                ┌────────────────────────────┐
                │   Render Static Site       │
                │   React SPA (Vite Build)   │
                └──────────────┬─────────────┘
                               │ REST API (JSON)
                               ▼
                ┌────────────────────────────┐
                │   Render Web Service       │
                │   Spring Boot 3 (Docker)   │
                │   ┌────────────────────┐   │
                │   │ Controllers (API)  │   │
                │   │ Services (UseCase) │   │
                │   │ Domain Layer       │   │
                │   │ Infrastructure     │   │
                │   └────────────────────┘   │
                └──┬─────────┬──────────┬────┘
                   │         │          │
                   ▼         ▼          ▼
            ┌──────────┐ ┌─────────┐ ┌──────────────┐
            │ Render   │ │ Render  │ │ External APIs│
            │ Postgres │ │ Disk    │ │ - Google     │
            │ (Managed)│ │ (圖片)  │ │   Vision OCR │
            └──────────┘ └─────────┘ │ - Frankfurter│
                                     │ - TWSE       │
                                     └──────────────┘

      GitHub Repo ──► GitHub Actions (CI) ──► Render Auto Deploy (CD)
```

### 2.2 技術選型

| 層次 | 技術 | 理由 |
|------|------|------|
| 後端 | Spring Boot 3.x (Java 21) | 完全吻合您既有技能 |
| API 文件 | OpenAPI 3.0 (Springdoc) | 自動產生、AI 協作友善 |
| 前端 | React 18 + TypeScript + Vite | 主流生態、型別安全 |
| UI 庫 | Ant Design 5.x | 企業級表單/表格/圖表完整 |
| 圖表 | Apache ECharts (echarts-for-react) | 功能強大、中文友善 |
| DB | PostgreSQL 16 (Render Managed) | 雲端友善、JSONB 彈性 |
| ORM | Spring Data JPA + Hibernate | Spring 原生整合 |
| Migration | Flyway | DB schema 版本控制 |
| OCR | Google Cloud Vision API | 中文發票辨識準確 |
| 認證 | Spring Security + JWT | 標準做法 |
| 容器 | Docker + Docker Compose | 開發/生產一致 |
| 部署 | Render (Static + Web Service + Postgres) | 一站式、Docker 友善 |
| CI/CD | GitHub Actions + Render Auto Deploy | 免費、推送即部署 |
| 測試 | JUnit 5 + Mockito (後端) / Vitest (前端) | 標準框架 |
| 監控 | Render Logs + Sentry (前端錯誤追蹤) | 免費起跳 |

### 2.3 為何選 Render？

- **單一介面管理** 前端 + 後端 + DB，不需分別處理多家服務
- **Docker 原生支援**，與您後續可能搬遷到其他容器平台無縫銜接
- **PostgreSQL Managed**，自動備份、免管理
- **GitHub 整合**：push 即自動部署
- **免費方案** 可開發測試；正式約 $7–14/月，比 AWS ECS 便宜且簡單
- **可搬遷性高**：標準 Docker + PostgreSQL，未來想換 Fly.io / Railway / 自家 NAS 都可

### 2.4 核心資料模型

```
User (id, email, name, password_hash, created_at)
  │
  ├── Account (id, user_id, name, type[checking/saving/credit/investment],
  │           currency, current_balance)
  │
  ├── Category (id, user_id, name, parent_id, icon, color,
  │            type[income/expense])
  │
  ├── Transaction (id, user_id, account_id, category_id, amount, type,
  │               transaction_date, description, tags[], attachment_url,
  │               source[manual/csv/ocr/api], created_at)
  │
  ├── Budget (id, user_id, category_id, amount, period[monthly],
  │          year, month)
  │
  └── Document (id, user_id, transaction_id, storage_key,
                ocr_result_json, uploaded_at)
```

### 2.5 後端模組結構（Hexagonal Architecture）

```
src/main/java/com/financehub/
├── api/            # REST Controllers, DTOs, Request/Response mapping
├── application/    # Use Cases / Application Services
├── domain/         # Entities, Value Objects, Repository Interfaces
├── infrastructure/ # JPA Repositories, S3/Disk Client, OCR Client,
│                   # External API Clients
└── config/         # Security, CORS, OpenAPI, Application Config
```

### 2.6 前端模組結構

```
src/
├── api/            # Axios clients, API hooks (React Query)
├── components/     # 可重用 UI 元件
├── features/       # 按功能分資料夾 (transactions, budget, dashboard...)
│   └── transactions/
│       ├── pages/
│       ├── components/
│       ├── hooks/
│       └── types.ts
├── layouts/        # AppLayout, AuthLayout
├── stores/         # Zustand 全域狀態
├── utils/          # 工具函式
└── App.tsx
```

---

## 三、Agile Sprint 計畫（16 週 / 8 Sprints）

**節奏：** 2 週 1 Sprint | Story Point 1pt ≈ 1 小時實作時間

| Sprint | 週次 | 主要交付 | 預估點數 |
|--------|------|---------|---------|
| **S0** 基礎建設 | 1–2 | GitHub repo、Docker、CI/CD、Render 環境、ClickUp 設定 | 20 |
| **S1** 認證 + 帳戶 | 3–4 | JWT 登入/登出、帳戶 CRUD、Swagger UI、React 登入頁 | 24 |
| **S2** 交易核心 | 5–6 | 交易 CRUD API、分類管理、交易列表頁（搜尋/篩選/分頁） | 30 |
| **S3** 資料匯入 | 7–8 | CSV/Excel 匯入 + OCR 圖片流程 + 重複偵測 | 32 |
| **S4** 儀表板 | 9–10 | 收支卡片、圓餅圖、趨勢圖、Dashboard 聚合 API | 28 |
| **S5** 預算 + 報表 | 11–12 | 預算 CRUD + 超支警示、月/年報表、PDF/CSV 匯出 | 26 |
| **S6** 外部 API | 13–14 | 匯率（Frankfurter）+ 台股（TWSE）整合、資產快照 | 22 |
| **S7** 測試 + 上線 | 15–16 | E2E 測試、安全稽核、備份/還原、生產環境上線 | 24 |

**總點數估算：** 約 206 點 ≈ 200 小時實作（每週 12–15 小時 × 16 週）

---

## 四、時程與成本

### 4.1 里程碑

| 階段 | 時程 | 交付物 |
|------|------|--------|
| Sprint 0 完成 | Week 2 | 骨架 + CI/CD 可運行，可在 Render 看到 Hello World |
| MVP Beta | Week 8 | 手動輸入 + CSV 匯入可用，可開始實際記帳 |
| MVP Release | Week 12 | 儀表板 + 預算完整，日常使用功能齊備 |
| v1.0 Production | Week 16 | 全功能上線，含 OCR、外部 API、安全稽核 |

### 4.2 雲端月費估算（USD）

| 服務 | 規格 | 月費 |
|------|------|------|
| Render Static Site | 100GB 流量 | **$0**（免費方案） |
| Render Web Service | Starter (512MB RAM) | $7 |
| Render PostgreSQL | Starter (1GB DB) | $7 |
| Render Disk（圖片儲存） | 1GB | $0.25 |
| Google Cloud Vision OCR | 1,000 次/月 | $1.5 |
| 網域名稱（選用） | .com 年費 | ~$1/月攤提 |
| **合計** | | **~$16–17/月** |

**節省方案：** 開發期可全用 Render 免費方案（會 sleep，但用於開發測試足夠），$0/月。

### 4.3 開發時間成本

- 每週投入 12–15 小時 × 16 週 = **約 200 小時**
- 若以業界中階工程師 $50/hr 換算機會成本：約 $10,000
- 實際支出：僅雲端費用，**約 $0–17/月**

---

## 五、專案管理（ClickUp 設定）

### 5.1 ClickUp 結構

```
Workspace: FinanceHub
└── Space: Development
    ├── Folder: Sprints
    │   ├── List: S0 基礎建設
    │   ├── List: S1 認證+帳戶
    │   ├── List: S2 交易核心
    │   ├── List: S3 資料匯入
    │   ├── List: S4 儀表板
    │   ├── List: S5 預算+報表
    │   ├── List: S6 外部 API
    │   └── List: S7 測試+上線
    ├── Folder: Backlog
    ├── Folder: Bugs
    └── Folder: Documentation
        ├── BRD / PRD / MRD
        ├── Architecture
        ├── API Spec
        └── Sprint Retrospectives
```

### 5.2 Task 自訂欄位

| 欄位 | 類型 | 用途 |
|------|------|------|
| Epic | Dropdown | 對應 6 個 Epic |
| Story Points | Number | 估算工時 |
| Sprint | Relationship | 關聯到 Sprint List |
| Tech Area | Dropdown | 後端/前端/基礎設施/AI 協作 |
| GitHub PR | URL | 對應的 Pull Request |
| Acceptance Criteria | Long Text | 驗收條件 |

### 5.3 每週節奏

| 時間 | 活動 |
|------|------|
| 週一 早上 | Sprint Review（上週成果） + 本週 Backlog Refinement |
| 每日 30 分鐘 | Daily Standup（自我 Check-in）+ 更新 ClickUp 狀態 |
| 週五 下午 | Retrospective（什麼做得好 / 改進） + 寫入 Documentation Folder |
| 雙週交界 | Sprint Planning：選定下 Sprint 的 Task |

### 5.4 Task 狀態流轉

```
Backlog → To Do → In Progress → In Review → Done
                    ↓ (受阻)
                  Blocked
```

---

## 六、AI 協作工作流程

### 6.1 每個 Sprint 起始

1. 在 Claude Code 中開啟對應 Sprint 的 ClickUp Task 列表
2. 將本計畫文件 `docs/plan.md` 與當 Sprint 的 Task 描述貼給 Claude
3. 用 `/feature-dev` 啟動架構討論
4. 拆解 Task → 撰寫 Acceptance Criteria → 估算 Story Points

### 6.2 每個 Task 實作流程

1. **建立分支**：`feature/F-XXX-description`
2. **TDD 流程**：先寫測試 → Red → Green → Refactor
3. **Claude 協作**：用 `/code-review` 在 PR 前自我審查
4. **PR 流程**：推送 → GitHub Actions 跑測試 → 自我 review → Merge
5. **更新 ClickUp**：Task 移到 Done + 關聯 PR

### 6.3 開發規範

- **後端**：Hexagonal Architecture，Service 層測試覆蓋率 ≥ 80%
- **前端**：TypeScript strict mode；不用 `any`，改用 `unknown` 或明確型別
- **API**：RESTful，回傳統一 `ApiResponse<T>` 包裝；錯誤用 `@ControllerAdvice` 集中處理
- **Commit**：`feat(transaction): add CSV bulk import with duplicate detection`
- **PR Template**：包含「變更目的、實作摘要、測試項目、截圖」

---

## 七、驗證計畫

每 Sprint 結束的 Definition of Done：

1. **後端測試**：`./mvnw test` 全部通過，Service 層覆蓋率 ≥ 80%
2. **前端測試**：`npm run test && npm run typecheck` 通過
3. **手動 E2E**（核心流程）：
   - 登入 → 新增交易 → CSV 匯入 → 儀表板數據正確
4. **Sprint 3+ 額外**：上傳發票圖片 → OCR 萃取金額正確顯示在預填表單
5. **Sprint 7（上線前）**：
   - OWASP ZAP 掃描，修復 Critical / High 項目
   - 備份還原演練成功
   - Render 健康檢查與監控告警設定完成

---

## 八、風險與緩解

| 風險 | 影響 | 緩解措施 |
|------|------|---------|
| OCR 中文辨識準確率不足 | F-004 體驗差 | Vision API 不夠用時改用 Azure / 開源 PaddleOCR |
| Render 免費方案 cold start 慢 | 開發期體驗 | 開發期可接受；上線後升級 Starter $7/月 |
| 個人時間不穩定 | Sprint 進度延遲 | Sprint Review 時可調整 Backlog 優先順序，砍 Could Have |
| 第三方 API 限額 | 外部資料中斷 | TWSE/Frankfurter 都免費且寬鬆；OCR 設配額警示 |
| 資料遺失 | 嚴重 | Render 自動每日備份 + Sprint 7 加入額外備份/還原功能 |

---

## 九、立即行動清單

1. **建立 ClickUp Workspace** 與 Sprint 0 Tasks
2. **建立 GitHub Repos**：
   - `financehub-backend`（Spring Boot）
   - `financehub-frontend`（React + Vite）
3. **建立 Render 帳號**，連結 GitHub
4. **申請 Google Cloud 帳號**（為 Vision API 預備）
5. **執行 Sprint 0 第一個 Task**：用 Spring Initializr 產生骨架並推送 GitHub

---

## 十、後續可延伸功能（v2 路線圖）

- AI 消費洞察：用 Claude API 分析消費模式，提供建議
- 行動 PWA：讓現有 Web 可安裝到手機
- 多幣別 P&L 計算
- 投資組合追蹤（股票/ETF/加密貨幣）
- 家庭多帳號 + 共同記帳
- 整合台灣銀行 Open Banking（待法規成熟）

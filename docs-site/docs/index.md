# FinanceHub — 個人財務管理系統

> 一套**自己擁有、自己掌控**的個人財務管理系統。
> 適合想要管帳、整理收支、追蹤資產淨值、又不想把資料交給第三方 app 的人。

## 這個系統能做什麼？

- 📒 **手動記帳**：新增/編輯/刪除收入、支出、轉帳
- 🏦 **多帳戶管理**：支票、儲蓄、信用卡、投資、現金，多幣別
- 📂 **批次匯入**：CSV / Excel / 銀行月結單（規劃中）
- 📸 **收據 OCR**：拍照即自動填表（規劃中）
- 📊 **儀表板**：月度收支、分類佔比、淨值趨勢（規劃中）
- 🌐 **外部資料**：自動更新匯率、股價（規劃中）

## 我是誰？我該從哪裡開始看？

=== "我是使用者，想用這個系統"
    1. 先看 [安裝與環境準備](getting-started/installation.md)
    2. 跟著 [5 分鐘快速上手](getting-started/quickstart.md) 把系統跑起來
    3. 想知道每個功能怎麼用 → [使用者手冊](user-guide/index.md)

=== "我是開發者，想理解系統怎麼運作"
    1. 先看 [系統整體架構](architecture/overview.md)
    2. 想知道 API 細節 → [API 參考](api-reference/index.md)
    3. 想動手改 → [本機開發](operations/local-dev.md)

=== "我接手維運，想知道怎麼運行"
    1. [本機開發環境](operations/local-dev.md)
    2. [測試與品質](operations/testing.md)
    3. [部署到 Render](operations/deployment.md)
    4. 遇到問題 → [疑難排解](operations/troubleshooting.md)

## 目前進度

| 模組 | 狀態 |
| ---- | ---- |
| 使用者註冊 / 登入（JWT） | ✅ 上線 |
| 帳戶 CRUD | ✅ 上線 |
| 前端介面（React + AntD） | ✅ 上線 |
| 交易紀錄（手動 CRUD + 餘額同步） | ✅ 上線 |
| 轉帳交易（同幣別、雙邊餘額同步） | ✅ 上線 |
| CSV / Excel 匯入 | ✅ 上線 |
| 收據 OCR | ⏳ 規劃中 |
| Dashboard / 視覺化 | ⏳ 規劃中 |

詳細任務狀態請見 [變更紀錄](changelog.md) 與 repo 內 `docs/workbook.md`。

## 技術棧速覽

- **後端**：Java 21 · Spring Boot 3.3 · Spring Security 6 · JPA · Flyway
- **資料庫**：PostgreSQL 16
- **前端**：React 18 · TypeScript · Vite · Ant Design 5（規劃中）
- **部署**：Render（Postgres Managed + Web Service + Static Site）
- **CI/CD**：GitHub Actions

---

!!! info "想直接玩玩看？"
    跳到 [5 分鐘快速上手](getting-started/quickstart.md) — 用 Docker 把整套系統跑起來，不到 10 個指令。

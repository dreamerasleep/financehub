# 變更紀錄

採 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/) 格式。版本號採 [Semantic Versioning](https://semver.org/lang/zh-TW/)。

> 細部任務狀態以 repo 內 `docs/workbook.md` 為準；本檔聚焦**對使用者可感知的變化**。

---

## [Unreleased] — Sprint 1 前端進行中

### 計畫
- React + Vite + TypeScript 前端骨架
- 登入 / 註冊頁
- 帳戶列表頁（受保護路由）

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

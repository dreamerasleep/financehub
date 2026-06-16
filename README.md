# FinanceHub

個人財務管理系統 — Monorepo

## 結構

```
financehub/
├── backend/          # Spring Boot 3 (Java 21)
├── frontend/         # React 18 + TypeScript + Vite
├── docs/             # 規劃文件、架構圖
└── .github/workflows # CI/CD
```

## 開發環境需求

- Java 21 (`openjdk@21`)
- Maven 3.6+
- Node.js 20+
- Docker
- gh CLI

## 環境變數設定

```bash
source ./setenv.sh
```

## 後端啟動

```bash
cd backend
./mvnw spring-boot:run
```

訪問 `http://localhost:8080/api/v1/health` 應回 `{"status":"UP"}`。
Swagger UI: `http://localhost:8080/swagger-ui.html`

## 前端啟動（Sprint 1 之後）

```bash
cd frontend
npm install
npm run dev
```

## 文件

- [規劃方案](docs/plan.md)

## 部署

採用 Render 全套（Static + Web Service + PostgreSQL）。

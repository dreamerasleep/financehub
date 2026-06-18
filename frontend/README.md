# FinanceHub Frontend

React 19 + TypeScript + Vite + Ant Design 5 的單頁應用，對接 `../backend` 提供的 REST API。

## 技術棧

- React 19 / TypeScript 6（strict）
- Vite 8（dev server + build）
- Ant Design 5（UI）
- TanStack Query 5（server state）
- Zustand 5（auth state，persist 至 localStorage）
- React Router 7（受保護路由）
- axios（攔截器自動帶入 JWT、401 自動導回登入）
- dayjs（日期）

## 前置需求

- Node.js 20+
- 後端服務已啟動（預設 `http://localhost:8080`，由 `vite.config.ts` proxy `/api` 轉發）

## Quickstart

```bash
npm install
npm run dev          # http://localhost:5173
```

第一次使用：到 `/login` → 「註冊」分頁建立帳號 → 登入後自動導向 `/transactions`。

## 可用指令

| 指令              | 用途                                  |
| ----------------- | ------------------------------------- |
| `npm run dev`     | 啟動 Vite dev server（含 HMR + proxy）|
| `npm run build`   | `tsc -b` 型別檢查 + Vite production build |
| `npm run lint`    | ESLint 全專案檢查                     |
| `npm run preview` | 預覽 `dist/` build 產物               |

## 目錄結構

```
src/
├── api/          # axios client + 各模組 API 函式 (auth, accounts, categories, transactions)
├── components/   # 共用元件（AppLayout, ProtectedRoute）
├── pages/        # 路由頁面（LoginPage, AccountsPage, TransactionsPage）
├── store/        # Zustand stores（auth）
├── types/        # TypeScript 型別定義
├── App.tsx       # 路由設定
└── main.tsx      # 進入點
```

別名：`@/*` → `src/*`（見 `vite.config.ts`）。

## 與後端的串接

- 開發環境：所有 `/api/*` 請求由 Vite 轉發到 `http://localhost:8080`
- JWT 流程：登入後 token 存於 Zustand persist → axios `Authorization: Bearer <token>` → 401 攔截後清空 store 並導回 `/login`
- 完整 API 規格見 `../docs-site/docs/api-reference/`

## 部署 build

```bash
npm run build
# dist/ 為靜態檔案，可直接交給任何靜態主機（Render Static Site / Nginx / S3）
```

production 環境需改寫 API base URL（目前由 dev proxy 解決，prod 走相對路徑或環境變數，預計 Sprint 7 上線時補上）。

## 已知限制

- AntD v5 與 React 19 仍有相容性 console warning（不影響功能）
- 主 bundle 約 1.3 MB，尚未做 code splitting（Sprint 7 處理）

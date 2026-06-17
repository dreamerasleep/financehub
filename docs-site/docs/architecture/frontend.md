# 前端設計

## 技術版本

| 元件 | 版本 |
| ---- | ---- |
| React | 19 |
| TypeScript | 6 |
| Vite | 8 |
| Ant Design | 5 |
| React Router | 7 |
| Zustand | 5（含 persist middleware）|
| TanStack Query | 5 |
| axios | 1.x |

## 套件結構

```
frontend/src/
├── main.tsx                 # 入口 + AntD 全域樣式
├── App.tsx                  # Providers + Router 設定
│
├── api/                     # 與後端對話
│   ├── client.ts            # axios instance + JWT interceptor + 401 攔截
│   ├── auth.ts              # register / login / me
│   └── accounts.ts          # 帳戶 CRUD
│
├── store/                   # 狀態管理
│   └── auth.ts              # zustand persist → localStorage
│
├── types/                   # 對應後端 DTO
│   ├── auth.ts
│   └── account.ts
│
├── components/              # 共用元件
│   ├── ProtectedRoute.tsx   # 未登入導向 /login
│   └── AppLayout.tsx        # 上方 nav + 內容區
│
└── pages/                   # 路由頁
    ├── LoginPage.tsx        # 登入 / 註冊（tab 切換）
    └── AccountsPage.tsx     # 帳戶列表 + Modal CRUD
```

## 設計決策

| 主題 | 決策 | 理由 |
| ---- | ---- | ---- |
| 狀態 | Zustand + localStorage persist | 比 Redux 輕；JWT 需跨 refresh 保留 |
| Server state | TanStack Query | 自動 cache、refetch、loading 狀態 |
| 樣式 | AntD + minimal index.css | 一次到位；自寫 CSS 減到最少 |
| 路徑別名 | `@/*` → `src/*` | 避免 `../../../` 巨型相對路徑 |
| 開發 proxy | Vite proxy `/api` → `:8080` | 避免本機 CORS 設定 |
| 表單 | AntD Form + validateFields | 內建錯誤訊息 + 中文化 |

## JWT 流程

```
1. 使用者送出登入表單
   → POST /api/v1/auth/login
   → 回傳 { token, userId, email, name }
   → useAuthStore.setAuth(...)
   → token 存進 localStorage("financehub-auth")
   → 導向 /accounts

2. 後續每個 API 呼叫
   → apiClient 的 request interceptor 自動加 Authorization: Bearer <token>

3. 後端回 401
   → apiClient 的 response interceptor 捕捉
   → useAuthStore.clear()
   → window.location.assign('/login')

4. 使用者點登出
   → useAuthStore.clear()
   → navigate('/login')
```

## 環境變數

| Key | 用途 |
| --- | ---- |
| `VITE_API_BASE_URL` | 後端 base URL；本機留空走 vite proxy；prod 填 Render API 網址 |

## 啟動

```bash
cd frontend
npm install
npm run dev    # http://localhost:5173
```

## CI

`.github/workflows/frontend-ci.yml` 在 push / PR 改 `frontend/` 時跑：

1. `npm ci`
2. `npm run lint`
3. `npx tsc -b`
4. `npm run build`

## 未來

- Sprint 2：交易頁面、餘額自動更新
- Sprint 4：受 OCR 表單預填
- Sprint 6：Dashboard 用 Apache ECharts
- Sprint 7：production build 部署 Render Static Site

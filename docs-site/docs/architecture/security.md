# 安全性與驗證

## 整體流程

```
1. 註冊      → POST /auth/register → 用 BCrypt 雜湊密碼 → 寫 users → 簽發 JWT
2. 登入      → POST /auth/login    → 驗 BCrypt → 簽發新 JWT
3. 後續呼叫  → Header: Authorization: Bearer <JWT>
              → JwtAuthFilter 驗 token → 設 SecurityContext (AuthenticatedUser)
              → Controller 用 @AuthenticationPrincipal 取得 userId
```

## 密碼

- 演算法：**BCrypt**（Spring `BCryptPasswordEncoder` 預設 strength 10）
- 儲存：只存雜湊後字串於 `users.password_hash`
- 原始密碼**永不**進 log、永不回傳

## JWT

- 函式庫：[jjwt 0.12.6](https://github.com/jwtk/jjwt)
- 演算法：HMAC-SHA（HS256/HS384/HS512 依 secret 長度）
- Payload：
  - `sub`：userId（字串）
  - `email`：使用者 email
  - `iat`、`exp`
- 有效期：預設 **24 小時**（`PT24H`），可由 `financehub.security.jwt.expiration` 調整
- Secret：由 `financehub.security.jwt.secret` 注入；**生產環境一律走環境變數**，最少 32 字元

### Token 範例 payload

```json
{
  "sub": "1",
  "email": "you@example.com",
  "iat": 1734400000,
  "exp": 1734486400
}
```

## 過濾鏈

```
HTTP 請求
  │
  ▼
SecurityFilterChain
  │
  ├─ CORS (允許 http://localhost:5173)
  ├─ CSRF disabled (無狀態 + JWT 不需要)
  ├─ Session policy: STATELESS
  ├─ Authorize:
  │     OPTIONS /** → permit (preflight)
  │     PUBLIC_ENDPOINTS → permit
  │       /api/v1/auth/register
  │       /api/v1/auth/login
  │       /api/v1/health
  │       /actuator/health
  │       /v3/api-docs/**
  │       /swagger-ui/**
  │     anyRequest → authenticated
  │
  ├─ JwtAuthFilter (在 UsernamePasswordAuthenticationFilter 之前)
  │     - 讀 Authorization header
  │     - 驗 token
  │     - 設 Principal = AuthenticatedUser(id, email)
  │
  └─ AuthenticationEntryPoint
        → 401 (HttpStatusEntryPoint)
```

!!! note "為什麼預設 401 而非 403？"
    Spring Security 6 對「沒 token」預設回 403，但業界慣例「未驗證」應該回 401。
    用 `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)` 強制回 401。

## CORS

| 設定 | 值 |
| ---- | -- |
| Allowed origins | `http://localhost:5173`（本機 Vite） |
| Allowed methods | GET / POST / PUT / DELETE / PATCH / OPTIONS |
| Allowed headers | `*` |
| Allow credentials | `true` |

部署到 Render 時改為前端 static site 的網址。

## 資料隔離

- **所有 service 方法都帶 `userId`**（從 `AuthenticatedUser` 取得，不信任前端）
- 查詢一律 `WHERE user_id = ?`
- 「查不到自己的」與「查到別人的」一律回 `404`，不洩漏存在性

## 還沒做的

- [ ] Refresh token / token 自動續期
- [ ] 角色 / 權限（目前只有 `ROLE_USER`）
- [ ] Rate limit（防暴力登入）
- [ ] Email 驗證 / 忘記密碼
- [ ] 2FA / OAuth (Google) — 由 backlog 決定是否導入

## 已知限制 / 待加固

- JWT 一旦發出，**無法撤銷**（要支援需引入黑名單表或改用 refresh token）
- 密碼強度檢查目前**只在前端**做（後端只擋空字串）
- 沒有登入失敗次數限制

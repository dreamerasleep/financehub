# 驗證 API

## POST `/api/v1/auth/register`

註冊新使用者並回傳 JWT。

### Request

```http
POST /api/v1/auth/register
Content-Type: application/json
```

```json
{
  "email": "you@example.com",
  "name": "Your Name",
  "password": "P@ssw0rd!"
}
```

| 欄位 | 型別 | 必填 | 規則 |
| ---- | ---- | ---- | ---- |
| `email` | string | ✓ | 合法 email、整個系統唯一 |
| `name` | string | ✓ | 非空 |
| `password` | string | ✓ | 非空（強度檢查目前由前端負責）|

### Response

**201 Created**

```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "userId": 1,
  "email": "you@example.com",
  "name": "Your Name"
}
```

**400 Bad Request**

- email 格式錯
- email 已被註冊（`detail: "Email already registered"`）

---

## POST `/api/v1/auth/login`

用 email + password 換 JWT。

### Request

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "email": "you@example.com",
  "password": "P@ssw0rd!"
}
```

### Response

**200 OK**

```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "userId": 1,
  "email": "you@example.com",
  "name": "Your Name"
}
```

**401 Unauthorized**

- 帳號不存在 / 密碼錯（不區分，避免帳號列舉）

---

## GET `/api/v1/auth/me`

取得目前登入者資訊。

### Request

```http
GET /api/v1/auth/me
Authorization: Bearer <JWT>
```

### Response

**200 OK**

```json
{
  "userId": 1,
  "email": "you@example.com"
}
```

**401 Unauthorized**

- 沒帶 Authorization header
- Token 過期 / 無效

# 5 分鐘快速上手

假設你已經跟著 [安裝指南](installation.md) 把後端跑起來了。這一頁只示範 API 操作，前端 UI 完成後會補上點按流程。

## 1. 註冊帳號

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "you@example.com",
    "name": "Your Name",
    "password": "P@ssw0rd!"
  }'
```

成功會回：

```json
{
  "token": "eyJhbGciOi...（很長的 JWT）",
  "userId": 1,
  "email": "you@example.com",
  "name": "Your Name"
}
```

把 `token` 存起來，後續每個 API 都要帶。

## 2. 登入（之後用這支）

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"P@ssw0rd!"}'
```

## 3. 確認自己是誰

```bash
TOKEN="貼上剛拿到的 token"

curl http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

## 4. 建立第一個帳戶

```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "玉山活存",
    "type": "SAVING",
    "currency": "TWD",
    "currentBalance": 50000
  }'
```

回傳 `201 Created`，body 內含這個帳戶的 `id`、時間戳等。

## 5. 列出所有帳戶

```bash
curl http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN"
```

## 完成！

接下來你可以：

- 用 [帳戶管理](../user-guide/accounts.md) 文件了解每個欄位的意義
- 翻 [API 參考](../api-reference/index.md) 看完整端點清單
- 想知道資料怎麼存的 → [資料庫設計](../architecture/database.md)

!!! tip "覺得 curl 麻煩？"
    用 [Postman](https://www.postman.com/) 或 [Insomnia](https://insomnia.rest/) 匯入 Swagger spec：
    <http://localhost:8080/v3/api-docs>

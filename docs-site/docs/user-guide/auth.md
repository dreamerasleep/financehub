# 註冊與登入

## 為什麼要登入？

FinanceHub 是**多使用者**系統——每個人只能看到自己的帳戶與交易。所有資料都用你的帳號隔離儲存。

## 註冊

| 欄位 | 規則 |
| ---- | ---- |
| `email` | 必填、符合 email 格式、整個系統內唯一 |
| `name` | 必填、顯示用名稱 |
| `password` | 必填，建議 8 字以上、含大小寫與符號 |

成功註冊後，系統會：

1. 用 BCrypt 雜湊你的密碼（**原始密碼不會存進資料庫**）
2. 立刻發一張 **JWT token**（有效期 24 小時）
3. 回傳 token、userId、email、name

!!! warning "Email 衝突"
    若 email 已被註冊，會回 `400 Bad Request`，訊息「Email already registered」。

## 登入

用 email + password 換一張新的 JWT token。

| 情境 | 回應 |
| ---- | ---- |
| 成功 | `200 OK` + token |
| 帳號不存在 / 密碼錯 | `401 Unauthorized` |

!!! info "為什麼帳號錯與密碼錯都回 401？"
    避免攻擊者透過錯誤訊息差異去猜哪些 email 已註冊。

## Token 怎麼用？

每次呼叫**需驗證**的 API 時，加上 HTTP header：

```
Authorization: Bearer <你的 token>
```

Token 失效後（24 小時），需要重新 `login` 換新的。

## 查詢自己的資訊

```
GET /api/v1/auth/me
Authorization: Bearer <token>
```

回傳目前登入者的 userId、email、name。

## 常見錯誤

| HTTP code | 意思 | 怎麼解 |
| --------- | ---- | ------ |
| 400 | 欄位不合法 / email 重複 | 看 response body 訊息 |
| 401 | 沒帶 token / token 過期 / 密碼錯 | 重新 login |
| 403 | 已驗證但無權限 | 通常不會發生，回報問題 |

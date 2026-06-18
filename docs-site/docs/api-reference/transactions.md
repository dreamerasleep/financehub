# 交易 API

base path：`/api/v1`

所有端點都需要在 `Authorization: Bearer <token>` 帶 JWT。資料只回傳當前使用者擁有的紀錄。

---

## `GET /api/v1/categories`

列出此使用者可用的分類（系統 11 個 + 未來自訂）。

回應範例：

```json
[
  { "id": 1, "name": "薪資",  "kind": "INCOME",  "system": true },
  { "id": 5, "name": "飲食",  "kind": "EXPENSE", "system": true }
]
```

---

## `GET /api/v1/transactions`

依日期由近到遠列出交易。

Query 參數（可選）：

| 名稱 | 型別 | 說明 |
| ---- | ---- | ---- |
| `from` | `YYYY-MM-DD` | 起始日（含） |
| `to`   | `YYYY-MM-DD` | 結束日（含） |

兩個都帶才會過濾，只帶其一會被忽略。

回應範例：

```json
[
  {
    "id": 12,
    "accountId": 3,
    "toAccountId": null,
    "categoryId": 5,
    "type": "EXPENSE",
    "amount": 350.00,
    "txnDate": "2026-06-18",
    "note": "午餐",
    "createdAt": "2026-06-18T07:31:48+08:00",
    "updatedAt": "2026-06-18T07:31:48+08:00"
  },
  {
    "id": 18,
    "accountId": 3,
    "toAccountId": 4,
    "categoryId": null,
    "type": "TRANSFER",
    "amount": 1500.00,
    "txnDate": "2026-06-18",
    "note": "月初轉帳",
    "createdAt": "2026-06-18T07:42:01+08:00",
    "updatedAt": "2026-06-18T07:42:01+08:00"
  }
]
```

`toAccountId` / `categoryId` 互斥：

- `INCOME` / `EXPENSE`：`categoryId` 必填、`toAccountId` 為 `null`
- `TRANSFER`：`toAccountId` 必填、`categoryId` 為 `null`

---

## `GET /api/v1/transactions/{id}`

取得單筆。不存在或不屬於本人皆回 `404 not_found`。

---

## `POST /api/v1/transactions`

新增一筆。

### INCOME / EXPENSE 請求

```json
{
  "accountId": 3,
  "categoryId": 5,
  "type": "EXPENSE",
  "amount": 350.00,
  "txnDate": "2026-06-18",
  "note": "午餐"
}
```

### TRANSFER 請求

```json
{
  "accountId": 3,
  "toAccountId": 4,
  "type": "TRANSFER",
  "amount": 1500.00,
  "txnDate": "2026-06-18",
  "note": "月初轉帳"
}
```

回應：`201 Created` + 與 `GET` 同樣 schema。

驗證：

- `amount` 必須 > 0
- `INCOME` / `EXPENSE`：分類的 `kind` 必須與 `type` 相符；不可帶 `toAccountId`
- `TRANSFER`：`toAccountId` 必填且 ≠ `accountId`；兩個帳戶幣別必須相同；不可帶 `categoryId`
- 帳戶的 `currentBalance` 會在同一個交易內同步調整（轉帳同時調整兩個）

---

## `PUT /api/v1/transactions/{id}`

完全覆寫一筆。後端會先把舊金額還原（轉帳會還原兩邊），再套用新金額。

請求 / 回應 schema 與 `POST` 相同。

---

## `DELETE /api/v1/transactions/{id}`

刪除。對應帳戶餘額會還原舊金額（轉帳會還原兩邊）。回 `204 No Content`。

---

## 錯誤碼速查

| HTTP | code | 情境 |
| ---- | ---- | ---- |
| 400  | `validation_error` | 欄位驗證失敗（金額、日期、必填等） |
| 400  | `invalid_request` | 分類 kind 與交易 type 不符；轉帳缺 / 多欄位；轉入相同帳戶；跨幣別轉帳 |
| 401  | —    | 缺 JWT 或 JWT 過期 |
| 404  | `not_found` | 交易、帳戶或分類不存在 / 不屬於本人 |

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
    "categoryId": 5,
    "type": "EXPENSE",
    "amount": 350.00,
    "txnDate": "2026-06-18",
    "note": "午餐",
    "createdAt": "2026-06-18T07:31:48+08:00",
    "updatedAt": "2026-06-18T07:31:48+08:00"
  }
]
```

---

## `GET /api/v1/transactions/{id}`

取得單筆。不存在或不屬於本人皆回 `404 not_found`。

---

## `POST /api/v1/transactions`

新增一筆。

請求：

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

回應：`201 Created` + 與 `GET` 同樣 schema。

驗證：

- `amount` 必須 > 0
- 分類的 `kind` 必須與 `type` 相符
- 對應帳戶的 `currentBalance` 會在同一個交易內同步調整

---

## `PUT /api/v1/transactions/{id}`

完全覆寫一筆。後端會先把舊金額還原回原帳戶，再把新金額套用到新帳戶。

請求 / 回應與 `POST` 相同。

---

## `DELETE /api/v1/transactions/{id}`

刪除。對應帳戶餘額會還原舊金額。回 `204 No Content`。

---

## 錯誤碼速查

| HTTP | code | 情境 |
| ---- | ---- | ---- |
| 400  | `validation_error` | 欄位驗證失敗（金額、日期、必填等） |
| 400  | `invalid_request` | 分類 kind 與交易 type 不符 |
| 401  | —    | 缺 JWT 或 JWT 過期 |
| 404  | `not_found` | 交易、帳戶或分類不存在 / 不屬於本人 |

# 帳戶 API

所有端點皆需 `Authorization: Bearer <JWT>`。回傳資料僅限**自己**的帳戶。

## GET `/api/v1/accounts`

列出我所有的帳戶（依 id 升序）。

### Response — 200 OK

```json
[
  {
    "id": 1,
    "name": "玉山活存",
    "type": "SAVING",
    "currency": "TWD",
    "currentBalance": 50000.00,
    "createdAt": "2026-06-17T07:30:00+08:00",
    "updatedAt": "2026-06-17T07:30:00+08:00"
  }
]
```

---

## GET `/api/v1/accounts/{id}`

取得單一帳戶。

### Response

| Code | 情況 |
| ---- | ---- |
| 200 | 成功 |
| 404 | 不存在 / 不屬於你 |

---

## POST `/api/v1/accounts`

新增帳戶。

### Request

```json
{
  "name": "玉山活存",
  "type": "SAVING",
  "currency": "TWD",
  "currentBalance": 50000.00
}
```

| 欄位 | 型別 | 必填 | 規則 |
| ---- | ---- | ---- | ---- |
| `name` | string | ✓ | 非空 |
| `type` | enum | ✓ | `CHECKING`/`SAVING`/`CREDIT`/`INVESTMENT`/`CASH` |
| `currency` | string | ✓ | ISO 4217（3 字大寫） |
| `currentBalance` | number | ✓ | 小數 2 位（信用卡可為負） |

### Response

**201 Created** — 回傳整個建好的帳戶物件

**400 Bad Request** — 欄位驗證失敗

---

## PUT `/api/v1/accounts/{id}`

更新帳戶。Body 同 POST。

### Response

| Code | 情況 |
| ---- | ---- |
| 200 | 成功 |
| 400 | 欄位驗證失敗 |
| 404 | 不存在 / 不屬於你 |

---

## DELETE `/api/v1/accounts/{id}`

刪除帳戶。

### Response

| Code | 情況 |
| ---- | ---- |
| 204 | 成功（無 body） |
| 404 | 不存在 / 不屬於你 |

!!! warning "Sprint 2 之後會改"
    加入交易紀錄後，刪除有交易的帳戶會擋下（回 `409 Conflict`），避免孤兒交易。

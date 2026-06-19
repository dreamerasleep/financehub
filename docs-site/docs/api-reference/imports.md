# 匯入 API

> Base path：`/api/v1/imports`，所有端點需 `Authorization: Bearer <token>`

## 建立匯入工作

`POST /imports` — `multipart/form-data`，欄位 `file`

回應 `201 Created`：

```json
{
  "id": 12,
  "filename": "june.csv",
  "format": "CSV",
  "status": "PENDING",
  "rowCount": 6,
  "okCount": 4,
  "errorCount": 1,
  "dupCount": 1,
  "createdAt": "2026-06-18T08:00:00Z",
  "committedAt": null,
  "expiresAt": "2026-06-19T08:00:00Z"
}
```

## 列出最近匯入工作

`GET /imports` — 回 20 筆，依 `id` desc

## 取得工作細節（含每列）

`GET /imports/{id}`

```json
{
  "job": { "...": "同上" },
  "rows": [
    {
      "id": 101,
      "rowIndex": 1,
      "status": "OK",
      "errorMessage": null,
      "rawJson": "{\"date\":\"2026-06-01\",...}",
      "parsedType": "INCOME",
      "parsedAmount": "30000.00",
      "parsedDate": "2026-06-01",
      "parsedAccountId": 5,
      "parsedToAccountId": null,
      "parsedCategoryId": 12,
      "parsedNote": "六月薪水"
    }
  ]
}
```

## 確認匯入

`POST /imports/{id}/commit`

```json
{ "rowIds": [101, 102] }
```

省略 `rowIds` 或設為 `null` 等同「全部 OK 列」。

回應 `200`：

```json
{ "jobId": 12, "committedCount": 2, "transactionIds": [201, 202] }
```

## 取消

`POST /imports/{id}/cancel` → `204`

## 錯誤碼

| Status | 何時 |
| ------ | ---- |
| 400 | 檔案缺 header / 空檔 / 列數 > 10000 |
| 401 | 未登入 |
| 404 | 工作不存在或非本人 |
| 409 | 工作不在 PENDING，或 commit 時 re-resolve 發現 ERROR 列 |
| 413 | 檔案 > 5 MB |
| 415 | 副檔名非 `.csv` / `.xlsx` |

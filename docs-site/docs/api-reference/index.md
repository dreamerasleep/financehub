# API 參考

## 基本資訊

| 項目 | 值 |
| ---- | -- |
| Base URL（本機） | `http://localhost:8080` |
| 內容格式 | `application/json` |
| 驗證方式 | `Authorization: Bearer <JWT>` |
| 字元編碼 | UTF-8 |

## 端點總覽

| 方法 | 路徑 | 公開 / 須驗證 | 說明 |
| ---- | ---- | -------- | ---- |
| GET  | `/api/v1/health` | 公開 | 健康檢查 |
| POST | `/api/v1/auth/register` | 公開 | 註冊新使用者 |
| POST | `/api/v1/auth/login` | 公開 | 登入並取得 JWT |
| GET  | `/api/v1/auth/me` | 須驗證 | 目前登入者資訊 |
| GET  | `/api/v1/accounts` | 須驗證 | 列出我的帳戶 |
| GET  | `/api/v1/accounts/{id}` | 須驗證 | 取得單一帳戶 |
| POST | `/api/v1/accounts` | 須驗證 | 新增帳戶 |
| PUT  | `/api/v1/accounts/{id}` | 須驗證 | 更新帳戶 |
| DELETE | `/api/v1/accounts/{id}` | 須驗證 | 刪除帳戶 |

## 互動式文件

啟動後端後：

- Swagger UI：<http://localhost:8080/swagger-ui.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>

## 詳細說明

- [驗證 API](auth.md)
- [帳戶 API](accounts.md)

## HTTP 狀態碼慣例

| Code | 用途 |
| ---- | ---- |
| 200 | 成功（GET / PUT） |
| 201 | 成功建立（POST） |
| 204 | 成功刪除（DELETE） |
| 400 | 欄位驗證失敗、資料衝突 |
| 401 | 未驗證 / token 無效 / 密碼錯 |
| 403 | 已驗證但無權限（目前極少觸發） |
| 404 | 資源不存在 / 不屬於你 |
| 500 | 伺服器錯誤（看 log） |

## 錯誤回應格式

目前用 Spring Boot 預設 `ProblemDetail`：

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Email already registered",
  "instance": "/api/v1/auth/register"
}
```

Sprint 2 會加全域 `@ControllerAdvice` 統一格式。

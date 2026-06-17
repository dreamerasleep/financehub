# 後端設計

## 技術版本

| 元件 | 版本 |
| ---- | ---- |
| Java | 21 (LTS) |
| Spring Boot | 3.3.4 |
| Spring Security | 6 |
| Spring Data JPA | 3.x |
| Hibernate | 6.x |
| Flyway | 10.x |
| JJWT | 0.12.6 |
| Springdoc OpenAPI | 2.6.0 |
| Testcontainers | 1.20.4 |

## 套件結構（六角架構分層）

```
com.financehub
├── FinanceHubApplication.java       # 入口
│
├── api/                              # 控制器層（HTTP 邊界）
│   ├── auth/AuthController.java
│   ├── account/AccountController.java
│   └── health/HealthController.java
│
├── application/                      # 應用服務層（用例編排）
│   ├── auth/AuthService.java
│   └── account/AccountService.java
│
├── domain/                           # 領域層（實體 + Repository 介面）
│   ├── user/{User, UserRepository}
│   └── account/{Account, AccountType, AccountRepository}
│
├── infrastructure/                   # 基礎設施（外部整合，目前還很少）
│
├── security/                         # 驗證機制
│   ├── JwtService.java
│   ├── JwtAuthFilter.java
│   └── AuthenticatedUser.java
│
└── config/                           # 設定
    ├── SecurityConfig.java
    └── JwtProperties.java
```

## 每一層的責任

| 層 | 該做的事 | 不該做的事 |
| -- | -------- | ---------- |
| `api` | 接 HTTP、驗 DTO、轉呼叫 application | 不寫商業邏輯、不直接碰 Repository |
| `application` | 用例編排、事務邊界、權限檢查 | 不關心 HTTP 細節、不直接呼叫外部服務 |
| `domain` | 實體、值物件、Repository 介面 | 不依賴 Spring 以外的框架（目前 JPA 例外）|
| `infrastructure` | 第三方串接、檔案 IO、Cron 排程 | 不暴露給 api 層 |
| `security` | 驗 token、設 SecurityContext | 不寫業務邏輯 |
| `config` | Bean 註冊、屬性綁定 | 不寫任何邏輯 |

## 命名慣例

- 實體：`User`, `Account`（單數、PascalCase）
- DTO：`AuthResult`、`AccountResponse`、`CreateAccountRequest`
- Service：`AuthService`、`AccountService`
- Controller：`AuthController`、`AccountController`
- 包名稱：全小寫，按業務領域分組（`auth`、`account`、`health`）

## 例外處理規範

- **領域錯誤**：擲對應例外（如 `EntityNotFoundException`、`IllegalArgumentException`）；交由 Spring 預設處理回 400/404
- **驗證錯誤**：用 `@Valid` + Bean Validation；自動回 400
- **驗證/授權**：交給 `SecurityConfig` 的 `AuthenticationEntryPoint` 回 401
- 自訂全域 `@ControllerAdvice` 等 Sprint 2 再加

## 測試策略

| 測試類型 | 副檔名 | 工具 | 範圍 |
| -------- | ------ | ---- | ---- |
| 單元測試 | `*Test.java` | JUnit 5 | 純邏輯 |
| 整合測試 | `*IT.java` | Spring Boot + Testcontainers | 完整 HTTP + DB |

CI 與本機都跑 `mvn verify`，會把 `*Test` 與 `*IT` 都跑過。

## 設定檔層級

```
application.yml          # 預設值（本機用，沒密碼）
application-prod.yml     # 生產環境（Render 注入環境變數覆蓋）
```

敏感資訊（DB 密碼、JWT secret）一律走環境變數：

```
DB_URL=jdbc:postgresql://...
DB_USERNAME=...
DB_PASSWORD=...
FINANCEHUB_SECURITY_JWT_SECRET=...
```

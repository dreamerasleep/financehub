# 測試

## 跑全部測試

```bash
cd backend
./mvnw verify
```

`verify` 會跑 `*Test.java`、`*Tests.java`、`*IT.java` 三類。

目前 8 個測試（截至 Sprint 1）：

| 類別 | 數量 |
| ---- | ---- |
| `FinanceHubApplicationTests` | 1 |
| `HealthControllerTest` | 1 |
| `AuthFlowIT` | 3 |
| `AccountCrudIT` | 3 |

## 跑單一測試

```bash
./mvnw test -Dtest=AuthFlowIT
./mvnw test -Dtest=AuthFlowIT#shouldRegisterLoginAndFetchMe
```

## 測試命名規則

| 後綴 | 目的 | 範圍 |
| ---- | ---- | ---- |
| `*Test.java` | 單元測試 | 純邏輯，不啟動 Spring |
| `*Tests.java` | 同上（Spring 慣例命名） | |
| `*IT.java` | 整合測試 | 啟動完整 Spring + DB（Testcontainers） |

## 整合測試怎麼跑 DB？

用 **Testcontainers + PostgreSQL 16-alpine**：

- 每次啟動測試 context 時跑一個 docker container
- 同一個 JVM 內共用同一個 container（`reusable`）
- 測試結束自動清掉

`PostgresTestcontainer.java` 內：

```java
public class PostgresTestcontainer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("financehub_test")
                .withUsername("test")
                .withPassword("test");
    static { POSTGRES.start(); }
    ...
}
```

整合測試類別上加：

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class AuthFlowIT { ... }
```

## 先決條件

- Docker Desktop 正在跑（沒跑會看到 `Could not find a valid Docker environment`）
- 第一次跑會下載 `postgres:16-alpine` image（約 100MB）

## CI 跑測試

GitHub Actions（`backend-ci.yml`）每次 push / PR 跑：

1. Checkout (actions/checkout@v5)
2. Setup JDK 21 (actions/setup-java@v5)
3. Cache Maven 依賴
4. `./mvnw verify`

Docker 在 GHA `ubuntu-latest` runner 內建可用，Testcontainers 直接跑得起來。

## 撰寫新測試時的習慣

- **每個整合測試獨立**：用 unique email、不依賴前一個測試的資料
- **不要 mock DB**：信任 Testcontainers
- **斷言 HTTP 狀態與關鍵欄位**，不要照抄整個 JSON
- 失敗訊息要能讓「半年後看」的人知道哪裡錯

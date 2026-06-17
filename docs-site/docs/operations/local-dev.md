# 本機開發

## 第一次設定

請先看 [安裝指南](../getting-started/installation.md)。

## 日常啟動順序

```bash
# 1. DB
cd financehub
docker compose up -d

# 2. 後端
cd backend
./mvnw spring-boot:run

# 3. 前端（規劃中）
cd ../frontend
npm install   # 第一次
npm run dev
```

| 服務 | 預設 port |
| ---- | --------- |
| PostgreSQL | 5432 |
| 後端 | 8080 |
| 前端 (Vite) | 5173 |

## 關閉

```bash
# 後端 / 前端：在 terminal 按 Ctrl+C
# DB（保留資料）：
docker compose stop

# DB（清空資料、重來）：
docker compose down -v
```

## 連到 DB 看資料

```bash
docker compose exec postgres psql -U financehub -d financehub
```

常用：

```sql
\dt                              -- 列出所有表
SELECT * FROM users;
SELECT * FROM accounts;
SELECT * FROM flyway_schema_history;   -- 看 migration 紀錄
```

## 重設整個本機環境

```bash
docker compose down -v    # 砍 DB 含資料
docker compose up -d      # 重起 DB
# 後端啟動時 Flyway 會自動跑 migration
./mvnw spring-boot:run
```

## 環境變數

本機預設不用設。要覆蓋的話：

```bash
export DB_URL=jdbc:postgresql://localhost:5432/financehub
export DB_USERNAME=financehub
export DB_PASSWORD=financehub_dev
export FINANCEHUB_SECURITY_JWT_SECRET=your_long_random_secret_at_least_32_chars
```

或寫進 `backend/.env`（**不要 commit**）並用 IDE 自動載入。

## 啟用 IDE

### IntelliJ IDEA

1. Open → 選 `backend/pom.xml`
2. SDK 選 Java 21
3. 啟動 `FinanceHubApplication`

### VS Code

裝 Extension Pack for Java + Spring Boot Extension Pack，會自動識別。

## 常用 Maven 指令

```bash
./mvnw spring-boot:run                # 啟動
./mvnw test                           # 跑單元測試
./mvnw verify                         # 跑所有測試（含 *IT）
./mvnw clean package                  # 打包成 jar
./mvnw dependency:tree                # 看依賴樹
./mvnw versions:display-dependency-updates  # 看哪些套件有新版
```

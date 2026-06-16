# FinanceHub Backend

Spring Boot 3.3 + Java 21

## 開發

```bash
# 需先 source ../setenv.sh 設定 Java 21
source ../setenv.sh

# 啟動
./mvnw spring-boot:run

# 測試
./mvnw test

# 打包
./mvnw clean package
```

## 端點

- `GET /api/v1/health` — 健康檢查
- `GET /actuator/health` — Spring Actuator 健康檢查
- `GET /swagger-ui.html` — API 文件 UI
- `GET /v3/api-docs` — OpenAPI JSON

## Docker

```bash
docker build -t financehub-backend .
docker run -p 8080:8080 financehub-backend
```

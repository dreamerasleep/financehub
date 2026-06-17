# 部署 (Render)

!!! note "狀態：規劃中"
    Sprint 7 才會正式上線。本頁先描述計畫好的步驟，實際操作時可能再調整。

## 為什麼選 Render？

- 一站打包：PostgreSQL Managed + Web Service + Static Site
- 免費 tier 可玩、$7/月 Starter 後常駐
- 設定簡單：`render.yaml` blueprint 一鍵建環境
- HTTPS 自動、域名免費（`*.onrender.com`）

預估月費：

| 元件 | 方案 | 月費 |
| ---- | ---- | ---- |
| PostgreSQL | Starter | $7 |
| Backend Web Service | Starter | $7 |
| Frontend Static Site | Free | $0 |
| **總計** | | **$14** |

## 部署步驟（規劃）

### 1. 建立 Render 帳號並連 GitHub

到 <https://render.com> 註冊，授權 access `dreamerasleep/financehub`。

### 2. 建立 PostgreSQL

- Type：PostgreSQL
- Name：`financehub-db`
- Region：Singapore（離台灣最近）
- Plan：Starter
- 記下 `Internal Database URL`（之後給後端用）

### 3. 建立後端 Web Service

- Type：Web Service
- Connect GitHub repo
- Root Directory：`backend`
- Environment：Docker（或 Native Java）
- Build Command：`./mvnw -DskipTests clean package`
- Start Command：`java -jar target/financehub-backend-0.0.1-SNAPSHOT.jar`

環境變數：

| Key | Value |
| --- | ----- |
| `DB_URL` | 上一步的 Internal Database URL（改成 `jdbc:postgresql://...`）|
| `DB_USERNAME` | DB user |
| `DB_PASSWORD` | DB password |
| `FINANCEHUB_SECURITY_JWT_SECRET` | 隨機 32 字元以上字串 |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` |

### 4. 建立前端 Static Site

- Type：Static Site
- Root Directory：`frontend`
- Build Command：`npm ci && npm run build`
- Publish Directory：`dist`

環境變數：

| Key | Value |
| --- | ----- |
| `VITE_API_BASE_URL` | `https://financehub-api.onrender.com` |

### 5. 更新後端 CORS

在 `application-prod.yml` 把允許的 origin 改為 Static Site 的 URL。

### 6. 設定自訂網域（選用）

Render → Custom Domain → 填網域、按指示加 CNAME。

## render.yaml（理想最終版）

```yaml
databases:
  - name: financehub-db
    plan: starter
    region: singapore

services:
  - type: web
    name: financehub-api
    env: docker
    plan: starter
    region: singapore
    rootDir: backend
    envVars:
      - key: DB_URL
        fromDatabase: { name: financehub-db, property: connectionString }
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: FINANCEHUB_SECURITY_JWT_SECRET
        generateValue: true

  - type: static
    name: financehub-web
    rootDir: frontend
    buildCommand: npm ci && npm run build
    staticPublishPath: ./dist
    envVars:
      - key: VITE_API_BASE_URL
        value: https://financehub-api.onrender.com
```

## 監控

- Render Dashboard 內建 Logs / Metrics
- `/actuator/health` 給 Render 健康檢查 polling
- Sprint 後期可考慮接 [UptimeRobot](https://uptimerobot.com/) 免費監控

## 備份

Render PostgreSQL Starter 每日自動備份，保留 7 天。
需要更長保留時間：

```bash
pg_dump $DATABASE_URL | gzip > backup-$(date +%F).sql.gz
```

可寫成 GitHub Actions cron 排程 push 到 S3 / GCS。

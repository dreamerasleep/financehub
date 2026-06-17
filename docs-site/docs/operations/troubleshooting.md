# 疑難排解

## 後端啟動

### `Port 8080 is already in use`

別的程式佔用。找出來：

```bash
lsof -nP -i :8080 | grep LISTEN
kill <PID>
```

或改其他 port：

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

### `Could not create connection to database server`

通常是 Docker PG 沒起來。

```bash
docker compose ps
# 沒看到 healthy → docker compose up -d
docker compose logs postgres   # 看錯誤訊息
```

### `Schema validation: missing column ...`

Flyway 與 JPA 對不上。常見原因：

1. 改了 JPA 實體但沒新增 migration
2. 已 release 的 migration 被改了（檢查 `flyway_schema_history.checksum`）

修法：新增一支 `V<N+1>__...sql`，**不要改舊的**。

如果本機可清空：

```bash
docker compose down -v && docker compose up -d
```

### `Application failed to start: JwtService bean ...`

Secret 沒設或太短。

```bash
export FINANCEHUB_SECURITY_JWT_SECRET=$(openssl rand -base64 48)
```

## 測試

### `Could not find a valid Docker environment`

Testcontainers 找不到 Docker。

- 確認 Docker Desktop 正在跑（macOS 任務列有鯨魚 icon）
- 重啟 Docker Desktop
- 若用 colima / podman，設環境變數 `DOCKER_HOST`

### 測試掛在「拉 postgres image」

第一次跑會下載約 100MB，網路慢可能會超時。先手動拉：

```bash
docker pull postgres:16-alpine
```

### `*IT.java` 沒被跑

Surefire 預設只跑 `*Test.java`。確認 `pom.xml` 有：

```xml
<plugin>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <includes>
      <include>**/*Test.java</include>
      <include>**/*Tests.java</include>
      <include>**/*IT.java</include>
    </includes>
  </configuration>
</plugin>
```

## API 呼叫

### 401「未授權」

- 沒帶 `Authorization: Bearer <token>` header
- token 過期（24 小時）
- token 拼錯（例如多餘空白）
- secret 不一致（換了 secret 後舊 token 就壞了）

### 403「拒絕」

理論上現在不太會發生。若發生，多半是 `SecurityConfig` 設定錯——回報 issue。

### 404 但「我明明剛建好」

可能你拿錯 `id`：

- `id` 屬於別人 → 一律回 404
- 重啟 PG 容器**沒 `-v`** 也清資料 → 重建

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/accounts
# 確認帳戶真的還在
```

## CORS

### 前端 fetch 出現 `CORS error`

- 前端跑的 origin 不在 `SecurityConfig.corsConfigurationSource` 的 allow list
- 本機是 `http://localhost:5173`，部署到 Render 後要改 Static Site 的網址
- 開瀏覽器 devtools → Network 看 OPTIONS preflight 的 response header

## GitHub

### `permission to ... denied to user`

```bash
gh auth status
gh auth login
gh auth setup-git
```

### push 被擋：`workflow scope`

改 `.github/workflows/` 的 push 需要 `workflow` scope。

```bash
gh auth refresh -h github.com -s workflow
```

## Render（部署後）

### Web Service 健康檢查失敗

- 確認 Render 設定的 Health Check Path 是 `/actuator/health`
- 確認 DB URL 用的是**Internal** URL（不是 External）
- 看 Logs：常見原因 OOM（Free tier 512MB 太小，跑 Spring 會吃緊）

### Free tier 第一次請求很慢

Free tier 15 分鐘沒流量會 sleep。升級 Starter ($7/月) 或接 cron-job.org 每 14 分鐘打一次 `/actuator/health`。

## 通用 debug 流程

1. 先看 log（後端 console / `docker compose logs postgres`）
2. 重現步驟最小化：用 curl 重試一次
3. 比對 git diff：最近改了什麼
4. 還是不行 → 把錯誤訊息整段、重現步驟、預期結果寫成 issue

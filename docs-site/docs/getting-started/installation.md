# 安裝與環境準備

## 你需要先裝這些工具

| 工具 | 用途 | 建議版本 | 確認指令 |
| ---- | ---- | -------- | -------- |
| **Java** | 後端執行環境 | 21（LTS） | `java -version` |
| **Docker Desktop** | 跑 PostgreSQL | 最新版 | `docker --version` |
| **Git** | 版本控制 | 2.30+ | `git --version` |
| **Node.js** | 前端開發 | 20+ | `node -v` |
| **gh CLI**（選用）| 操作 GitHub | 最新版 | `gh --version` |

### macOS 一鍵安裝

```bash
# 安裝 Homebrew（若沒裝）
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 一次安裝完
brew install openjdk@21 git node gh
brew install --cask docker
```

裝完 Java 21 後，設定 `JAVA_HOME`（加進 `~/.zshrc`）：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH=$JAVA_HOME/bin:$PATH
```

### Windows / Linux

- Java 21：[Adoptium Temurin](https://adoptium.net/temurin/releases/?version=21)
- Docker Desktop：[Get Docker](https://www.docker.com/products/docker-desktop/)
- 其他工具請依各 OS 套件管理員安裝

## 取得原始碼

```bash
git clone https://github.com/dreamerasleep/financehub.git
cd financehub
```

!!! warning "這是 private repo"
    需要先用 `gh auth login` 登入 GitHub，並確認你被加入 collaborator 後才能 clone。

## 啟動本機資料庫

專案根目錄已備好 `docker-compose.yml`：

```bash
docker compose up -d
```

預設值（可在 `docker-compose.yml` 修改）：

| 項目 | 值 |
| ---- | -- |
| Host | `localhost` |
| Port | `5432` |
| DB | `financehub` |
| User | `financehub` |
| Password | `financehub_dev` |

確認資料庫起來：

```bash
docker compose ps
# 應看到 postgres 狀態 healthy
```

## 啟動後端

```bash
cd backend
./mvnw spring-boot:run
```

第一次會下載依賴（約 2–5 分鐘）。看到下面這行表示成功：

```
Tomcat started on port 8080 (http)
```

打開瀏覽器確認：

- API 健康檢查：<http://localhost:8080/api/v1/health>
- Swagger UI：<http://localhost:8080/swagger-ui.html>

## 下一步

→ [5 分鐘快速上手](quickstart.md)：建立帳號、登入、新增第一個帳戶

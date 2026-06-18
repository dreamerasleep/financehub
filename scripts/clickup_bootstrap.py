#!/usr/bin/env python3
"""
FinanceHub ClickUp Bootstrap
用法：
  export CLICKUP_API_KEY="pk_xxx"
  export CLICKUP_TEAM_ID="xxxxxxxx"
  python3 scripts/clickup_bootstrap.py

功能：
  - 建立 Space "Development"（已存在則跳過）
  - 建立 Folders: Sprints / Backlog / Bugs / Documentation
  - 建立 Sprint Lists: S0 基礎建設 / S1 認證+帳戶 / S2 交易核心
  - 在每個 List 建立 custom fields: Epic, Feature ID, Story Points, Tech Area, Acceptance Criteria
  - 批次建立 34 張 Tasks（含狀態、SP、Acceptance Criteria）

冪等性：已存在的 Space / Folder / List 不重複建立。
"""

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API_BASE = "https://api.clickup.com/api/v2"


def get_env(key: str) -> str:
    val = os.environ.get(key, "")
    if not val:
        print(f"❌  環境變數 {key} 未設定。請先 export {key}=xxx")
        sys.exit(1)
    return val


API_KEY = get_env("CLICKUP_API_KEY")
TEAM_ID = get_env("CLICKUP_TEAM_ID")
HEADERS = {
    "Authorization": API_KEY,
    "Content-Type": "application/json",
}


# ── HTTP helpers ──────────────────────────────────────────────────────────────

def _request(method: str, path: str, body: dict | None = None) -> dict:
    url = f"{API_BASE}{path}"
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(url, data=data, headers=HEADERS, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        msg = e.read().decode()
        print(f"❌  HTTP {e.code} {method} {path}\n    {msg}")
        sys.exit(1)


def get(path: str) -> dict:
    return _request("GET", path)


def post(path: str, body: dict) -> dict:
    time.sleep(0.3)   # ClickUp rate limit: 100 req/min
    return _request("POST", path, body)


# ── Space ─────────────────────────────────────────────────────────────────────

def get_or_create_space(name: str) -> str:
    spaces = get(f"/team/{TEAM_ID}/space?archived=false").get("spaces", [])
    for s in spaces:
        if s["name"] == name:
            print(f"✓  Space '{name}' 已存在 (id={s['id']})")
            return s["id"]
    resp = post(f"/team/{TEAM_ID}/space", {
        "name": name,
        "multiple_assignees": False,
        "features": {
            "due_dates": {"enabled": True, "start_date": True},
            "tags": {"enabled": True},
            "time_estimates": {"enabled": True},
            "custom_fields": {"enabled": True},
        },
    })
    sid = resp["id"]
    print(f"✅  Space '{name}' 建立完成 (id={sid})")
    return sid


# ── Folder ────────────────────────────────────────────────────────────────────

def get_or_create_folder(space_id: str, name: str) -> str:
    folders = get(f"/space/{space_id}/folder?archived=false").get("folders", [])
    for f in folders:
        if f["name"] == name:
            print(f"  ✓  Folder '{name}' 已存在 (id={f['id']})")
            return f["id"]
    resp = post(f"/space/{space_id}/folder", {"name": name})
    fid = resp["id"]
    print(f"  ✅  Folder '{name}' 建立完成 (id={fid})")
    return fid


# ── List ──────────────────────────────────────────────────────────────────────

def get_or_create_list(folder_id: str, name: str) -> str:
    lists = get(f"/folder/{folder_id}/list?archived=false").get("lists", [])
    for lst in lists:
        if lst["name"] == name:
            print(f"    ✓  List '{name}' 已存在 (id={lst['id']})")
            return lst["id"]
    resp = post(f"/folder/{folder_id}/list", {"name": name})
    lid = resp["id"]
    print(f"    ✅  List '{name}' 建立完成 (id={lid})")
    return lid


# ── Custom Fields ─────────────────────────────────────────────────────────────

def get_existing_field_names(list_id: str) -> set[str]:
    fields = get(f"/list/{list_id}/field").get("fields", [])
    return {f["name"] for f in fields}


def create_custom_fields(list_id: str) -> None:
    existing = get_existing_field_names(list_id)

    fields_to_create = [
        {
            "name": "Epic",
            "type": "drop_down",
            "type_config": {
                "options": [
                    {"name": "Infra"},
                    {"name": "PM"},
                    {"name": "系統功能"},
                    {"name": "交易管理"},
                    {"name": "分類與標籤"},
                    {"name": "儀表板"},
                    {"name": "預算管理"},
                    {"name": "報表與匯出"},
                ]
            },
        },
        {"name": "Feature ID", "type": "short_text"},
        {"name": "Story Points", "type": "number"},
        {
            "name": "Tech Area",
            "type": "drop_down",
            "type_config": {
                "options": [
                    {"name": "後端"},
                    {"name": "前端"},
                    {"name": "基礎設施"},
                    {"name": "文件"},
                    {"name": "AI 協作"},
                ]
            },
        },
        {"name": "Acceptance Criteria", "type": "text"},
    ]

    for field_def in fields_to_create:
        if field_def["name"] in existing:
            print(f"      ✓  Custom field '{field_def['name']}' 已存在")
            continue
        payload = {"name": field_def["name"], "type": field_def["type"]}
        if "type_config" in field_def:
            payload["type_config"] = field_def["type_config"]
        post(f"/list/{list_id}/field", payload)
        print(f"      ✅  Custom field '{field_def['name']}' 建立完成")


# ── Tasks ─────────────────────────────────────────────────────────────────────

STATUS_MAP = {
    "Done": "complete",
    "In Progress": "in progress",
    "To Do": "Open",
    "Backlog": "Open",
    "In Review": "in review",
}

SPRINT_0_TASKS = [
    {
        "name": "S0-T01 建立 GitHub Monorepo (backend + frontend)",
        "epic": "Infra", "tech_area": "基礎設施", "sp": 1, "status": "Done",
        "feature_id": "",
        "ac": "repo 已建立、main 分支已 push",
    },
    {
        "name": "S0-T02 安裝 Java 21 + Maven Wrapper",
        "epic": "Infra", "tech_area": "後端", "sp": 1, "status": "Done",
        "feature_id": "",
        "ac": "./mvnw -v 成功；CI 使用 Java 21",
    },
    {
        "name": "S0-T03 Spring Boot 3.3 骨架 + Health endpoint",
        "epic": "Infra", "tech_area": "後端", "sp": 2, "status": "Done",
        "feature_id": "",
        "ac": "/actuator/health 回 200；./mvnw test 通過",
    },
    {
        "name": "S0-T04 GitHub Actions CI（後端測試 + lint）",
        "epic": "Infra", "tech_area": "基礎設施", "sp": 2, "status": "Done",
        "feature_id": "",
        "ac": "push 後 CI 綠燈",
    },
    {
        "name": "S0-T05 Docker 化後端 + 本地 docker-compose（含 Postgres 16）",
        "epic": "Infra", "tech_area": "基礎設施", "sp": 3, "status": "To Do",
        "feature_id": "",
        "ac": "docker compose up 起得來、能連 DB",
    },
    {
        "name": "S0-T06 Flyway 設定 + 第一支 migration（users 表骨架）",
        "epic": "Infra", "tech_area": "後端", "sp": 2, "status": "To Do",
        "feature_id": "",
        "ac": "flyway:migrate 通過",
    },
    {
        "name": "S0-T07 Render 帳號 + Web Service + Postgres 連線",
        "epic": "Infra", "tech_area": "基礎設施", "sp": 3, "status": "To Do",
        "feature_id": "",
        "ac": "Render 部署成功；/actuator/health 可外連",
    },
    {
        "name": "S0-T08 Vite + React 18 + TS + Ant Design 骨架",
        "epic": "Infra", "tech_area": "前端", "sp": 2, "status": "To Do",
        "feature_id": "",
        "ac": "npm run dev 啟動成功；Hello FinanceHub 頁可見",
    },
    {
        "name": "S0-T09 前端 ESLint + Prettier + Vitest 工具鏈",
        "epic": "Infra", "tech_area": "前端", "sp": 1, "status": "To Do",
        "feature_id": "",
        "ac": "npm run lint、npm run test 通過",
    },
    {
        "name": "S0-T10 ClickUp Workspace + Sprint Lists + 自訂欄位",
        "epic": "PM", "tech_area": "基礎設施", "sp": 1, "status": "In Progress",
        "feature_id": "",
        "ac": "8 個 Sprint List 建好；自訂欄位齊全",
    },
    {
        "name": "S0-T11 docs/ 文件補齊（README、架構圖、PR 模板）",
        "epic": "PM", "tech_area": "文件", "sp": 2, "status": "To Do",
        "feature_id": "",
        "ac": "README 有跑通指令；PR template 啟用",
    },
]

SPRINT_1_TASKS = [
    {
        "name": "S1-T01 User Entity + UserRepository + Flyway migration",
        "epic": "系統功能", "tech_area": "後端", "sp": 2, "status": "To Do",
        "feature_id": "F-050",
        "ac": "建表成功；單元測試覆蓋 Repository",
    },
    {
        "name": "S1-T02 Spring Security 設定 + PasswordEncoder",
        "epic": "系統功能", "tech_area": "後端", "sp": 3, "status": "To Do",
        "feature_id": "F-050",
        "ac": "SecurityFilterChain 設定通過；bcrypt 編碼可用",
    },
    {
        "name": "S1-T03 JWT 簽發/驗證模組（access + refresh）",
        "epic": "系統功能", "tech_area": "後端", "sp": 3, "status": "To Do",
        "feature_id": "F-050",
        "ac": "token 簽發、解碼、過期測試通過",
    },
    {
        "name": "S1-T04 POST /api/auth/register API",
        "epic": "系統功能", "tech_area": "後端", "sp": 2, "status": "To Do",
        "feature_id": "F-050",
        "ac": "整合測試：註冊成功、重複 email 回 409",
    },
    {
        "name": "S1-T05 POST /api/auth/login API（回 access+refresh token）",
        "epic": "系統功能", "tech_area": "後端", "sp": 2, "status": "To Do",
        "feature_id": "F-050",
        "ac": "帳密正確 200、錯誤 401；測試通過",
    },
    {
        "name": "S1-T06 POST /api/auth/refresh API",
        "epic": "系統功能", "tech_area": "後端", "sp": 1, "status": "To Do",
        "feature_id": "F-050",
        "ac": "refresh token 換新 access token",
    },
    {
        "name": "S1-T07 Account Entity + Flyway migration",
        "epic": "交易管理", "tech_area": "後端", "sp": 1, "status": "To Do",
        "feature_id": "",
        "ac": "accounts 表建立；FK to users",
    },
    {
        "name": "S1-T08 Account CRUD API（含 user_id 隔離）",
        "epic": "交易管理", "tech_area": "後端", "sp": 3, "status": "To Do",
        "feature_id": "",
        "ac": "5 個端點 + 整合測試；他人帳戶 404",
    },
    {
        "name": "S1-T09 Springdoc OpenAPI 設定 + /swagger-ui",
        "epic": "系統功能", "tech_area": "後端", "sp": 1, "status": "To Do",
        "feature_id": "",
        "ac": "Swagger UI 看得到所有 Auth/Account 端點",
    },
    {
        "name": "S1-T10 前端 Login 頁 + JWT 儲存（zustand）",
        "epic": "系統功能", "tech_area": "前端", "sp": 3, "status": "To Do",
        "feature_id": "F-050",
        "ac": "登入成功跳轉、token 存進 store",
    },
    {
        "name": "S1-T11 前端 Axios interceptor（自動加 Bearer + 401 處理）",
        "epic": "系統功能", "tech_area": "前端", "sp": 2, "status": "To Do",
        "feature_id": "F-050",
        "ac": "401 自動嘗試 refresh 或登出",
    },
    {
        "name": "S1-T12 前端 Account 列表頁 + 新增/編輯 Modal",
        "epic": "交易管理", "tech_area": "前端", "sp": 1, "status": "To Do",
        "feature_id": "",
        "ac": "列表顯示、Modal 新增成功後刷新",
    },
]

SPRINT_2_TASKS = [
    {
        "name": "S2-T01 Category Entity + 預設分類樹 seed",
        "epic": "分類與標籤", "tech_area": "後端", "sp": 3, "status": "To Do",
        "feature_id": "F-010",
        "ac": "預設分類 seed migration 跑成功",
    },
    {
        "name": "S2-T02 Category CRUD API（含父子層級）",
        "epic": "分類與標籤", "tech_area": "後端", "sp": 3, "status": "To Do",
        "feature_id": "F-010,F-011",
        "ac": "樹狀查詢端點；不可刪有交易的分類",
    },
    {
        "name": "S2-T03 Transaction Entity + Flyway migration（含 tags jsonb）",
        "epic": "交易管理", "tech_area": "後端", "sp": 2, "status": "To Do",
        "feature_id": "F-001",
        "ac": "表建立；tags 用 jsonb",
    },
    {
        "name": "S2-T04 Transaction CRUD API（含類別/帳戶/標籤校驗）",
        "epic": "交易管理", "tech_area": "後端", "sp": 4, "status": "To Do",
        "feature_id": "F-001,F-002",
        "ac": "5 個端點；單元 + 整合測試通過",
    },
    {
        "name": "S2-T05 Transaction 查詢 API（分頁/排序/日期範圍/類別/標籤篩選）",
        "epic": "交易管理", "tech_area": "後端", "sp": 4, "status": "To Do",
        "feature_id": "F-001",
        "ac": "Pageable + Specification；測試覆蓋多組條件",
    },
    {
        "name": "S2-T06 帳戶餘額即時更新（DomainEvent + Listener）",
        "epic": "交易管理", "tech_area": "後端", "sp": 3, "status": "To Do",
        "feature_id": "",
        "ac": "新增/刪除交易後餘額正確；併發測試通過",
    },
    {
        "name": "S2-T07 前端 Category 樹狀管理頁",
        "epic": "分類與標籤", "tech_area": "前端", "sp": 2, "status": "To Do",
        "feature_id": "F-010,F-011",
        "ac": "顯示樹、可新增子分類",
    },
    {
        "name": "S2-T08 前端 Transaction 列表頁（Ant Design Table + 分頁）",
        "epic": "交易管理", "tech_area": "前端", "sp": 3, "status": "To Do",
        "feature_id": "F-001",
        "ac": "列表載入、分頁切換正確",
    },
    {
        "name": "S2-T09 前端 Transaction 篩選列（日期、類別、類型、標籤）",
        "epic": "交易管理", "tech_area": "前端", "sp": 3, "status": "To Do",
        "feature_id": "F-001",
        "ac": "URL query string 同步、F5 不丟篩選",
    },
    {
        "name": "S2-T10 前端 Transaction 新增/編輯 Drawer",
        "epic": "交易管理", "tech_area": "前端", "sp": 2, "status": "To Do",
        "feature_id": "F-001",
        "ac": "必填驗證、儲存後 invalidate React Query cache",
    },
    {
        "name": "S2-T11 前端 多標籤 input（自由輸入 + autocomplete）",
        "epic": "分類與標籤", "tech_area": "前端", "sp": 1, "status": "To Do",
        "feature_id": "F-012",
        "ac": "可新增、刪除、autocomplete 列出已用標籤",
    },
]


def get_field_id_map(list_id: str) -> dict[str, str]:
    fields = get(f"/list/{list_id}/field").get("fields", [])
    return {f["name"]: f["id"] for f in fields}


def get_field_option_id(fields_raw: list, field_name: str, option_name: str) -> str | None:
    for f in fields_raw:
        if f["name"] == field_name:
            for opt in f.get("type_config", {}).get("options", []):
                if opt["name"] == option_name:
                    return opt["id"]
    return None


def create_tasks(list_id: str, tasks: list[dict]) -> list[str]:
    fields_raw = get(f"/list/{list_id}/field").get("fields", [])
    field_ids = {f["name"]: f["id"] for f in fields_raw}

    created_urls = []
    for t in tasks:
        custom_fields = []

        # Story Points
        if "Story Points" in field_ids:
            custom_fields.append({"id": field_ids["Story Points"], "value": t["sp"]})

        # Acceptance Criteria
        if "Acceptance Criteria" in field_ids and t.get("ac"):
            custom_fields.append({"id": field_ids["Acceptance Criteria"], "value": t["ac"]})

        # Epic (dropdown — need option id)
        if "Epic" in field_ids and t.get("epic"):
            opt_id = get_field_option_id(fields_raw, "Epic", t["epic"])
            if opt_id:
                custom_fields.append({"id": field_ids["Epic"], "value": opt_id})

        # Tech Area (dropdown)
        if "Tech Area" in field_ids and t.get("tech_area"):
            opt_id = get_field_option_id(fields_raw, "Tech Area", t["tech_area"])
            if opt_id:
                custom_fields.append({"id": field_ids["Tech Area"], "value": opt_id})

        # Feature ID (short_text)
        if "Feature ID" in field_ids and t.get("feature_id"):
            custom_fields.append({"id": field_ids["Feature ID"], "value": t["feature_id"]})

        payload: dict = {
            "name": t["name"],
            "markdown_description": f"**Acceptance Criteria**\n\n{t['ac']}",
        }
        if custom_fields:
            payload["custom_fields"] = custom_fields

        # Map status
        status_val = STATUS_MAP.get(t.get("status", "To Do"), "Open")
        payload["status"] = status_val

        resp = post(f"/list/{list_id}/task", payload)
        url = resp.get("url", f"https://app.clickup.com/t/{resp.get('id','?')}")
        print(f"      ✅  {t['name'][:60]}  →  {url}")
        created_urls.append(url)

    return created_urls


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    print("=" * 60)
    print("FinanceHub ClickUp Bootstrap")
    print(f"Workspace Team ID: {TEAM_ID}")
    print("=" * 60)

    # 1. Space
    print("\n[1/5] Space")
    space_id = get_or_create_space("Development")

    # 2. Folders
    print("\n[2/5] Folders")
    sprints_folder_id = get_or_create_folder(space_id, "Sprints")
    get_or_create_folder(space_id, "Backlog")
    get_or_create_folder(space_id, "Bugs")
    get_or_create_folder(space_id, "Documentation")

    # 3. Sprint Lists
    print("\n[3/5] Sprint Lists")
    list_s0 = get_or_create_list(sprints_folder_id, "S0 基礎建設")
    list_s1 = get_or_create_list(sprints_folder_id, "S1 認證+帳戶")
    list_s2 = get_or_create_list(sprints_folder_id, "S2 交易核心")

    # 4. Custom Fields
    print("\n[4/5] Custom Fields")
    for name, lid in [("S0", list_s0), ("S1", list_s1), ("S2", list_s2)]:
        print(f"  List {name}:")
        create_custom_fields(lid)

    # 5. Tasks
    print("\n[5/5] Tasks")

    print("  Sprint 0:")
    urls_s0 = create_tasks(list_s0, SPRINT_0_TASKS)

    print("  Sprint 1:")
    urls_s1 = create_tasks(list_s1, SPRINT_1_TASKS)

    print("  Sprint 2:")
    urls_s2 = create_tasks(list_s2, SPRINT_2_TASKS)

    # Summary
    print("\n" + "=" * 60)
    print("完成！")
    print(f"  S0 基礎建設:  {len(urls_s0)} tasks  → {urls_s0[0] if urls_s0 else '-'}")
    print(f"  S1 認證+帳戶:  {len(urls_s1)} tasks  → {urls_s1[0] if urls_s1 else '-'}")
    print(f"  S2 交易核心:  {len(urls_s2)} tasks  → {urls_s2[0] if urls_s2 else '-'}")
    print(f"  合計: {len(urls_s0) + len(urls_s1) + len(urls_s2)} tasks")
    print("=" * 60)


if __name__ == "__main__":
    main()

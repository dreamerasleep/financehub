#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["pyyaml"]
# ///
"""
FinanceHub ClickUp Sync
讀取 clickup_tasks.yml，冪等同步到 ClickUp。

用法：
  export CLICKUP_API_KEY="pk_xxx"
  export CLICKUP_TEAM_ID="xxxxxxxx"
  python3 scripts/clickup_sync.py           # 完整同步
  python3 scripts/clickup_sync.py --dry-run # 預覽，不實際寫入

進度更新流程：
  1. 編輯 clickup_tasks.yml 裡的 status 欄位
  2. python3 scripts/clickup_sync.py
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

try:
    import yaml
except ImportError:
    print("❌  需要 PyYAML：pip install pyyaml")
    sys.exit(1)

# ── 設定 ─────────────────────────────────────────────────────────────────────

SCRIPT_DIR = Path(__file__).parent
TASKS_YML = SCRIPT_DIR / "clickup_tasks.yml"
STATE_FILE = SCRIPT_DIR / "clickup_state.json"   # gitignored
API_BASE = "https://api.clickup.com/api/v2"

AGILE_STATUSES = [
    {"status": "Backlog",     "color": "#87909e", "type": "open"},
    {"status": "To Do",       "color": "#4194f6", "type": "open"},
    {"status": "In Progress", "color": "#f9c90f", "type": "custom"},
    {"status": "In Review",   "color": "#ff7800", "type": "custom"},
    {"status": "Blocked",     "color": "#e50000", "type": "custom"},
    {"status": "Done",        "color": "#00c875", "type": "closed"},
]


def get_env(key: str) -> str:
    val = os.environ.get(key, "")
    if not val:
        print(f"❌  環境變數 {key} 未設定")
        sys.exit(1)
    return val


API_KEY = get_env("CLICKUP_API_KEY")
TEAM_ID = get_env("CLICKUP_TEAM_ID")
HEADERS = {"Authorization": API_KEY, "Content-Type": "application/json"}

# ── HTTP ──────────────────────────────────────────────────────────────────────

def _req(method: str, path: str, body: dict | None = None, dry_run: bool = False) -> dict:
    if dry_run and method != "GET":
        print(f"  [dry-run] {method} {path}")
        return {}
    url = f"{API_BASE}{path}"
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(url, data=data, headers=HEADERS, method=method)
    time.sleep(0.3)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        msg = e.read().decode()
        print(f"❌  HTTP {e.code} {method} {path}\n    {msg}")
        sys.exit(1)


def api_get(path: str) -> dict:
    return _req("GET", path)


def api_post(path: str, body: dict, dry_run: bool = False) -> dict:
    return _req("POST", path, body, dry_run)


def api_put(path: str, body: dict, dry_run: bool = False) -> dict:
    return _req("PUT", path, body, dry_run)


# ── State file ────────────────────────────────────────────────────────────────

def load_state() -> dict:
    if STATE_FILE.exists():
        return json.loads(STATE_FILE.read_text())
    return {"space_id": None, "folder_ids": {}, "list_ids": {}, "task_ids": {}}


def save_state(state: dict) -> None:
    STATE_FILE.write_text(json.dumps(state, indent=2, ensure_ascii=False))


# ── Date helpers ──────────────────────────────────────────────────────────────

def to_ms(date_str: str) -> int:
    dt = datetime.strptime(date_str, "%Y-%m-%d").replace(tzinfo=timezone.utc)
    return int(dt.timestamp() * 1000)


# ── Space ─────────────────────────────────────────────────────────────────────

def get_or_create_space(name: str, state: dict, dry_run: bool) -> str:
    if state["space_id"]:
        return state["space_id"]
    spaces = api_get(f"/team/{TEAM_ID}/space?archived=false").get("spaces", [])
    for s in spaces:
        if s["name"] == name:
            print(f"  ✓  Space '{name}' (id={s['id']})")
            state["space_id"] = s["id"]
            return s["id"]
    resp = api_post(f"/team/{TEAM_ID}/space", {"name": name}, dry_run)
    sid = resp.get("id", "DRY_RUN")
    print(f"  ✅  Space '{name}' 建立 (id={sid})")
    state["space_id"] = sid
    return sid


def set_space_agile_statuses(space_id: str, dry_run: bool) -> None:
    api_put(f"/space/{space_id}", {"statuses": AGILE_STATUSES}, dry_run)
    print("  ✅  Agile 狀態已套用至 Space")


# ── Folder ────────────────────────────────────────────────────────────────────

def get_or_create_folder(space_id: str, name: str, state: dict, dry_run: bool) -> str:
    if name in state["folder_ids"]:
        return state["folder_ids"][name]
    folders = api_get(f"/space/{space_id}/folder?archived=false").get("folders", [])
    for f in folders:
        if f["name"] == name:
            print(f"    ✓  Folder '{name}' (id={f['id']})")
            state["folder_ids"][name] = f["id"]
            return f["id"]
    resp = api_post(f"/space/{space_id}/folder", {"name": name}, dry_run)
    fid = resp.get("id", "DRY_RUN")
    print(f"    ✅  Folder '{name}' 建立 (id={fid})")
    state["folder_ids"][name] = fid
    return fid


# ── List ──────────────────────────────────────────────────────────────────────

def get_or_create_list(folder_id: str, name: str, sprint_key: str, state: dict, dry_run: bool) -> str:
    if sprint_key in state["list_ids"]:
        return state["list_ids"][sprint_key]
    lists = api_get(f"/folder/{folder_id}/list?archived=false").get("lists", [])
    for lst in lists:
        if lst["name"] == name:
            print(f"      ✓  List '{name}' (id={lst['id']})")
            state["list_ids"][sprint_key] = lst["id"]
            return lst["id"]
    resp = api_post(f"/folder/{folder_id}/list", {"name": name}, dry_run)
    lid = resp.get("id", "DRY_RUN")
    print(f"      ✅  List '{name}' 建立 (id={lid})")
    state["list_ids"][sprint_key] = lid
    return lid


# ── Custom Fields ─────────────────────────────────────────────────────────────

FIELD_DEFS = [
    {"name": "Epic", "type": "drop_down", "type_config": {"options": [
        {"name": "Infra"}, {"name": "PM"}, {"name": "系統功能"}, {"name": "交易管理"},
        {"name": "分類與標籤"}, {"name": "儀表板"}, {"name": "預算管理"}, {"name": "報表與匯出"},
    ]}},
    {"name": "Feature ID", "type": "short_text"},
    {"name": "Story Points", "type": "number"},
    {"name": "Tech Area", "type": "drop_down", "type_config": {"options": [
        {"name": "後端"}, {"name": "前端"}, {"name": "基礎設施"}, {"name": "文件"}, {"name": "AI 協作"},
    ]}},
    {"name": "Acceptance Criteria", "type": "text"},
]


def ensure_custom_fields(list_id: str, dry_run: bool) -> dict:
    existing = {f["name"]: f for f in api_get(f"/list/{list_id}/field").get("fields", [])}
    for fd in FIELD_DEFS:
        if fd["name"] not in existing:
            payload = {"name": fd["name"], "type": fd["type"]}
            if "type_config" in fd:
                payload["type_config"] = fd["type_config"]
            resp = api_post(f"/list/{list_id}/field", payload, dry_run)
            if not dry_run:
                existing[fd["name"]] = resp
                print(f"        ✅  Custom field '{fd['name']}' 建立")
        else:
            print(f"        ✓  Custom field '{fd['name']}'")
    # Re-fetch to get option IDs for dropdowns
    return {f["name"]: f for f in api_get(f"/list/{list_id}/field").get("fields", [])}


def build_custom_field_values(task: dict, fields_map: dict) -> list[dict]:
    values = []

    def dropdown_opt_id(field_name: str, option_name: str) -> str | None:
        f = fields_map.get(field_name, {})
        for opt in f.get("type_config", {}).get("options", []):
            if opt["name"] == option_name:
                return opt["id"]
        return None

    if "Story Points" in fields_map and task.get("story_points") is not None:
        values.append({"id": fields_map["Story Points"]["id"], "value": task["story_points"]})

    if "Acceptance Criteria" in fields_map and task.get("ac"):
        values.append({"id": fields_map["Acceptance Criteria"]["id"], "value": task["ac"]})

    if "Epic" in fields_map and task.get("epic"):
        opt_id = dropdown_opt_id("Epic", task["epic"])
        if opt_id:
            values.append({"id": fields_map["Epic"]["id"], "value": opt_id})

    if "Tech Area" in fields_map and task.get("tech_area"):
        opt_id = dropdown_opt_id("Tech Area", task["tech_area"])
        if opt_id:
            values.append({"id": fields_map["Tech Area"]["id"], "value": opt_id})

    if "Feature ID" in fields_map and task.get("feature_id"):
        values.append({"id": fields_map["Feature ID"]["id"], "value": task["feature_id"]})

    return values


# ── Existing task discovery ───────────────────────────────────────────────────

def fetch_existing_tasks(list_id: str) -> dict[str, str]:
    """Return {task_name: task_id} for all tasks in list."""
    resp = api_get(f"/list/{list_id}/task?include_closed=true")
    return {t["name"]: t["id"] for t in resp.get("tasks", [])}


# ── Task create / update ──────────────────────────────────────────────────────

def sync_task(task: dict, list_id: str, sprint: dict, fields_map: dict,
              state: dict, dry_run: bool) -> None:
    task_id_key = task["id"]
    existing_id = state["task_ids"].get(task_id_key)

    start_ms = to_ms(task.get("start") or sprint["start"])
    due_ms = to_ms(task.get("due") or sprint["end"])

    payload = {
        "name": task["name"],
        "status": task.get("status", "To Do"),
        "start_date": start_ms,
        "start_date_time": False,
        "due_date": due_ms,
        "due_date_time": False,
        "time_estimate": (task.get("story_points", 1) or 1) * 60 * 60 * 1000,  # SP → ms
        "markdown_description": f"**Acceptance Criteria**\n\n{task.get('ac', '')}",
        "custom_fields": build_custom_field_values(task, fields_map),
    }

    if existing_id:
        api_put(f"/task/{existing_id}", payload, dry_run)
        action = "更新"
    else:
        resp = api_post(f"/list/{list_id}/task", payload, dry_run)
        new_id = resp.get("id")
        if new_id:  # only persist real IDs, not dry-run placeholders
            state["task_ids"][task_id_key] = new_id
        existing_id = new_id or "DRY_RUN"
        action = "建立"

    url = f"https://app.clickup.com/t/{existing_id}" if existing_id != "DRY_RUN" else "(dry-run)"
    status_label = task.get("status", "To Do")
    sp = task.get("story_points", "?")
    print(f"          {action}: [{status_label}] {task['name'][:55]}  SP={sp}  → {url}")


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="不實際寫入 ClickUp")
    args = parser.parse_args()
    dry_run: bool = args.dry_run

    config = yaml.safe_load(TASKS_YML.read_text(encoding="utf-8"))
    state = load_state()

    if dry_run:
        print("⚠️   DRY RUN — 不會寫入 ClickUp\n")

    space_name = config["meta"]["space_name"]

    print("=" * 60)
    print(f"FinanceHub ClickUp Sync  (team={TEAM_ID})")
    print("=" * 60)

    # 1. Space
    print("\n[1] Space")
    space_id = get_or_create_space(space_name, state, dry_run)
    save_state(state)

    # 2. Agile statuses at Space level
    print("\n[2] Agile 狀態")
    set_space_agile_statuses(space_id, dry_run)

    # 3. Folders
    print("\n[3] Folders")
    sprints_fid = get_or_create_folder(space_id, "Sprints", state, dry_run)
    for fname in ("Backlog", "Bugs", "Documentation"):
        get_or_create_folder(space_id, fname, state, dry_run)
    save_state(state)

    # 4. Sprint lists + custom fields + tasks
    print("\n[4] Sprints → Lists → Tasks")
    total_created = 0
    total_updated = 0

    for sprint in config["sprints"]:
        key = sprint["key"]
        print(f"\n  ── {key}: {sprint['list_name']} ({sprint['start']} ~ {sprint['end']}) ──")

        list_id = get_or_create_list(sprints_fid, sprint["list_name"], key, state, dry_run)
        save_state(state)

        print("    Custom fields:")
        fields_map = ensure_custom_fields(list_id, dry_run)

        # Seed state from existing tasks (first run with no state)
        if not dry_run:
            existing = fetch_existing_tasks(list_id)
            for task in sprint.get("tasks", []):
                if task["id"] not in state["task_ids"] and task["name"] in existing:
                    state["task_ids"][task["id"]] = existing[task["name"]]
                    print(f"        🔗  匹配現有 task: {task['id']} → {existing[task['name']]}")
            save_state(state)

        print("    Tasks:")
        before_count = len(state["task_ids"])
        for task in sprint.get("tasks", []):
            sync_task(task, list_id, sprint, fields_map, state, dry_run)
        after_count = len(state["task_ids"])
        save_state(state)

        created = after_count - before_count
        updated = len(sprint.get("tasks", [])) - created
        total_created += created
        total_updated += updated

    # Summary
    print("\n" + "=" * 60)
    if dry_run:
        print("DRY RUN 完成（未實際寫入）")
    else:
        print("同步完成！")
        print(f"  建立: {total_created} tasks")
        print(f"  更新: {total_updated} tasks")
        print(f"  State 儲存至: {STATE_FILE}")
    print("=" * 60)


if __name__ == "__main__":
    main()

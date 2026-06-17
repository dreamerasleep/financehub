# FinanceHub Docs Site

GitBook-style 文件站，使用 [MkDocs Material](https://squidfunk.github.io/mkdocs-material/) 構建。

## 本機預覽

```bash
cd docs-site
pip install -r requirements.txt
mkdocs serve
```

開啟 <http://localhost:8000>。修改任何 `.md` 自動 reload。

## 嚴格建置（CI 模式）

```bash
mkdocs build --strict
```

`--strict` 會把死連結、未在 nav 的孤兒檔案視為錯誤。

## 部署

push 到 `main` 後 `.github/workflows/docs.yml` 會：

1. 跑 `mkdocs build --strict`
2. 上傳 artifact
3. 部署到 GitHub Pages

首次需要在 repo 設定打開 **Settings → Pages → Source: GitHub Actions**。

## 目錄結構

```
docs-site/
├── mkdocs.yml          # 主設定（navigation、theme、plugins）
├── requirements.txt    # CI 用相同版本
├── docs/
│   ├── index.md
│   ├── getting-started/
│   ├── user-guide/
│   ├── architecture/
│   ├── api-reference/
│   ├── operations/
│   ├── glossary.md
│   └── changelog.md
└── overrides/          # 自訂 template（暫無）
```

## 文件更新規則

**每次功能異動時，同步更新對應頁面**：

| 改了什麼 | 必改文件 |
| -------- | -------- |
| 新 API 端點 / 改 request/response | `api-reference/<module>.md` |
| 新使用者可見功能 | `user-guide/<feature>.md` |
| 新 / 改 DB schema | `architecture/database.md` |
| 新環境變數 | `operations/local-dev.md` + `operations/deployment.md` |
| 任何已上線功能變更 | `changelog.md` 加一筆 |

PR 內若改 `backend/` 或 `frontend/` 但沒改對應 docs，請補上。

## 寫作慣例

- 使用繁體中文；程式碼/指令保持英文
- 程式碼框使用語言標籤（` ```bash ` / ` ```java ` / ` ```sql `）
- 連結用相對路徑（`../user-guide/auth.md`）
- 圖示使用 [Material Admonitions](https://squidfunk.github.io/mkdocs-material/reference/admonitions/)：`!!! note`、`!!! warning`、`!!! tip`
- 避免主觀形容詞；用清單與表格代替長段落

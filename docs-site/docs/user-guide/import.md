# CSV / Excel 匯入

把一批整理好的交易一次匯入，避免逐筆手動建檔。

## 我能做什麼

- 上傳 `.csv` 或 `.xlsx`，每檔最多 10000 列、5 MB
- 在預覽頁逐列檢查狀態（OK / 錯誤 / 重複），勾選後再正式匯入
- 重複偵測：與既存交易 / 同檔先前列同 hash 的列會被擋下，不會重複入庫

## 期望欄位

| 欄位 | 必填 | 範例 | 說明 |
| ---- | ---- | ---- | ---- |
| `date` | ✅ | `2026-06-18` | ISO 字串；Excel 日期 cell 自動轉 |
| `type` | ✅ | `INCOME` / `EXPENSE` / `TRANSFER` | 不分大小寫 |
| `account` | ✅ | `主帳戶` | 比對你既有的帳戶名 |
| `amount` | ✅ | `1500.00` | 正數；逗號分隔接受 |
| `category` | 收入 / 支出必填 | `飲食` | 須與 type 一致；轉帳留空 |
| `to_account` | 轉帳必填 | `副帳戶` | 收入 / 支出留空 |
| `note` | ⛔ | `午餐` | ≤255 字 |

## 操作步驟

1. 上方選單點「匯入」
2. 拖放或點擊上傳 CSV / XLSX
3. 預覽頁檢查每列狀態，OK 預設已勾選；想跳過任何 OK 列就取消勾選
4. 按「確認匯入」
5. 系統會把選中的列轉成交易並更新帳戶餘額

## 常見訊息

| 訊息 | 原因 |
| ---- | ---- |
| `Account not found: <name>` | 帳戶名與你帳戶不符 |
| `Category not found or kind mismatch: <name>` | 分類不存在或與 type 不符（收入接到支出分類等） |
| `Cross-currency transfer not supported` | 跨幣別轉帳（規劃中：Sprint 5 匯率上線後解鎖） |
| `Duplicate of existing transaction` | 與既有交易（或同檔先前列）相同 |
| `File exceeds maximum row count of 10000` | 超過 10000 列 |

## 預覽會保留多久？

24 小時。逾時未確認會自動標為 `EXPIRED`。

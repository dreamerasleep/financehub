import type { TransactionType } from './transaction'

export type ImportFormat = 'CSV' | 'XLSX'
export type ImportJobStatus = 'PENDING' | 'COMMITTED' | 'CANCELLED' | 'EXPIRED'
export type ImportJobRowStatus = 'OK' | 'ERROR' | 'DUPLICATE'

export interface ImportJob {
  id: number
  filename: string
  format: ImportFormat
  status: ImportJobStatus
  rowCount: number
  okCount: number
  errorCount: number
  dupCount: number
  createdAt: string
  committedAt: string | null
  expiresAt: string
}

export interface ImportJobRow {
  id: number
  rowIndex: number
  status: ImportJobRowStatus
  errorMessage: string | null
  rawJson: string
  parsedType: TransactionType | null
  parsedAmount: number | null
  parsedDate: string | null
  parsedAccountId: number | null
  parsedToAccountId: number | null
  parsedCategoryId: number | null
  parsedNote: string | null
}

export interface ImportJobDetail {
  job: ImportJob
  rows: ImportJobRow[]
}

export interface ImportCommitResult {
  jobId: number
  committedCount: number
  transactionIds: number[]
}

export interface PatchRowRequest {
  date: string
  type: 'INCOME' | 'EXPENSE' | 'TRANSFER'
  account: string
  amount: string
  category: string
  to_account: string
  note: string
}

export interface PatchRowResponse {
  job: ImportJob
  row: ImportJobRow
}

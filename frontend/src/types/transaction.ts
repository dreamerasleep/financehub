export type TransactionType = 'INCOME' | 'EXPENSE' | 'TRANSFER'

export const TRANSACTION_TYPES: TransactionType[] = ['INCOME', 'EXPENSE', 'TRANSFER']

export const TRANSACTION_TYPE_LABEL: Record<TransactionType, string> = {
  INCOME: '收入',
  EXPENSE: '支出',
  TRANSFER: '轉帳',
}

export interface Transaction {
  id: number
  accountId: number
  toAccountId: number | null
  categoryId: number | null
  type: TransactionType
  amount: number
  txnDate: string
  note: string | null
  createdAt: string
  updatedAt: string
}

export interface TransactionInput {
  accountId: number
  toAccountId?: number | null
  categoryId?: number | null
  type: TransactionType
  amount: number
  txnDate: string
  note?: string
}

export type TransactionType = 'INCOME' | 'EXPENSE'

export const TRANSACTION_TYPES: TransactionType[] = ['INCOME', 'EXPENSE']

export const TRANSACTION_TYPE_LABEL: Record<TransactionType, string> = {
  INCOME: '收入',
  EXPENSE: '支出',
}

export interface Transaction {
  id: number
  accountId: number
  categoryId: number
  type: TransactionType
  amount: number
  txnDate: string
  note: string | null
  createdAt: string
  updatedAt: string
}

export interface TransactionInput {
  accountId: number
  categoryId: number
  type: TransactionType
  amount: number
  txnDate: string
  note?: string
}

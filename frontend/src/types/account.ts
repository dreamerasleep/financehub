export type AccountType = 'CHECKING' | 'SAVING' | 'CREDIT' | 'INVESTMENT' | 'CASH'

export const ACCOUNT_TYPES: AccountType[] = [
  'CHECKING',
  'SAVING',
  'CREDIT',
  'INVESTMENT',
  'CASH',
]

export const ACCOUNT_TYPE_LABEL: Record<AccountType, string> = {
  CHECKING: '支票 / 活存',
  SAVING: '儲蓄',
  CREDIT: '信用卡',
  INVESTMENT: '投資',
  CASH: '現金',
}

export interface Account {
  id: number
  name: string
  type: AccountType
  currency: string
  currentBalance: number
  createdAt: string
  updatedAt: string
}

export interface AccountInput {
  name: string
  type: AccountType
  currency: string
  currentBalance: number
}

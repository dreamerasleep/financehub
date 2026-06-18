import { apiClient } from './client'
import type { Transaction, TransactionInput } from '@/types/transaction'

export interface ListTransactionsParams {
  from?: string
  to?: string
}

export async function listTransactions(params?: ListTransactionsParams): Promise<Transaction[]> {
  const { data } = await apiClient.get<Transaction[]>('/api/v1/transactions', { params })
  return data
}

export async function createTransaction(body: TransactionInput): Promise<Transaction> {
  const { data } = await apiClient.post<Transaction>('/api/v1/transactions', body)
  return data
}

export async function updateTransaction(id: number, body: TransactionInput): Promise<Transaction> {
  const { data } = await apiClient.put<Transaction>(`/api/v1/transactions/${id}`, body)
  return data
}

export async function deleteTransaction(id: number): Promise<void> {
  await apiClient.delete(`/api/v1/transactions/${id}`)
}

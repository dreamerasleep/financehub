import { apiClient } from './client'
import type { Account, AccountInput } from '@/types/account'

export async function listAccounts(): Promise<Account[]> {
  const { data } = await apiClient.get<Account[]>('/api/v1/accounts')
  return data
}

export async function getAccount(id: number): Promise<Account> {
  const { data } = await apiClient.get<Account>(`/api/v1/accounts/${id}`)
  return data
}

export async function createAccount(body: AccountInput): Promise<Account> {
  const { data } = await apiClient.post<Account>('/api/v1/accounts', body)
  return data
}

export async function updateAccount(id: number, body: AccountInput): Promise<Account> {
  const { data } = await apiClient.put<Account>(`/api/v1/accounts/${id}`, body)
  return data
}

export async function deleteAccount(id: number): Promise<void> {
  await apiClient.delete(`/api/v1/accounts/${id}`)
}

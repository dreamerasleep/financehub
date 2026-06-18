import { apiClient } from './client'
import type { Category } from '@/types/category'

export async function listCategories(): Promise<Category[]> {
  const { data } = await apiClient.get<Category[]>('/api/v1/categories')
  return data
}

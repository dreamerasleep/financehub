import { apiClient } from './client'
import type {
  ImportCommitResult,
  ImportJob,
  ImportJobDetail,
} from '@/types/import'

export async function uploadImport(file: File): Promise<ImportJob> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.post<ImportJob>('/api/v1/imports', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function getImport(id: number): Promise<ImportJobDetail> {
  const { data } = await apiClient.get<ImportJobDetail>(`/api/v1/imports/${id}`)
  return data
}

export async function commitImport(
  id: number,
  rowIds?: number[],
): Promise<ImportCommitResult> {
  const { data } = await apiClient.post<ImportCommitResult>(
    `/api/v1/imports/${id}/commit`,
    { rowIds: rowIds && rowIds.length > 0 ? rowIds : null },
  )
  return data
}

export async function cancelImport(id: number): Promise<void> {
  await apiClient.post(`/api/v1/imports/${id}/cancel`)
}

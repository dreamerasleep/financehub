import type { ImportJobRow, ImportJobRowStatus } from '@/types/import'

export type StatusFilter = 'ALL' | ImportJobRowStatus

export function applyFilter(
  rows: ImportJobRow[],
  filter: StatusFilter,
): ImportJobRow[] {
  if (filter === 'ALL') return rows
  return rows.filter((r) => r.status === filter)
}

export function collectOkIds(rows: ImportJobRow[]): number[] {
  return rows.filter((r) => r.status === 'OK').map((r) => r.id)
}

export function invertOk(
  selectedIds: number[],
  allOkIds: number[],
): number[] {
  const selectedSet = new Set(selectedIds)
  const okSet = new Set(allOkIds)
  const result: number[] = []
  for (const id of allOkIds) {
    if (!selectedSet.has(id)) result.push(id)
  }
  for (const id of selectedIds) {
    if (!okSet.has(id)) result.push(id)
  }
  return result
}

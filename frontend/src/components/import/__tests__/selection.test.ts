import { describe, it, expect } from 'vitest'
import { applyFilter, collectOkIds, invertOk } from '../selection'
import type { ImportJobRow } from '@/types/import'

const row = (id: number, status: 'OK' | 'ERROR' | 'DUPLICATE'): ImportJobRow => ({
  id, rowIndex: id, status,
  errorMessage: status === 'OK' ? null : 'x',
  rawJson: '{}',
  parsedType: null, parsedAmount: null, parsedDate: null,
  parsedAccountId: null, parsedToAccountId: null,
  parsedCategoryId: null, parsedNote: null,
})

describe('applyFilter', () => {
  const rows = [row(1, 'OK'), row(2, 'ERROR'), row(3, 'DUPLICATE'), row(4, 'OK')]

  it('returns all rows when filter is ALL', () => {
    expect(applyFilter(rows, 'ALL')).toHaveLength(4)
  })

  it('returns only OK rows', () => {
    expect(applyFilter(rows, 'OK').map((r) => r.id)).toEqual([1, 4])
  })

  it('returns only ERROR rows', () => {
    expect(applyFilter(rows, 'ERROR').map((r) => r.id)).toEqual([2])
  })

  it('returns only DUPLICATE rows', () => {
    expect(applyFilter(rows, 'DUPLICATE').map((r) => r.id)).toEqual([3])
  })
})

describe('collectOkIds', () => {
  it('returns OK row ids in order', () => {
    expect(collectOkIds([row(2, 'ERROR'), row(1, 'OK'), row(3, 'OK')])).toEqual([1, 3])
  })
})

describe('invertOk', () => {
  it('toggles OK ids that are selected vs not', () => {
    const allOk = [1, 2, 3]
    expect(invertOk([1, 3], allOk).sort()).toEqual([2])
    expect(invertOk([], allOk).sort()).toEqual([1, 2, 3])
    expect(invertOk([1, 2, 3], allOk)).toEqual([])
  })

  it('preserves non-OK selections', () => {
    const allOk = [1, 2]
    expect(invertOk([99, 1], allOk).sort((a, b) => a - b)).toEqual([2, 99])
  })
})

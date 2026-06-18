export type CategoryKind = 'INCOME' | 'EXPENSE'

export interface Category {
  id: number
  name: string
  kind: CategoryKind
  system: boolean
}

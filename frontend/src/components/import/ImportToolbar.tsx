import { Button, Radio, Space, Typography } from 'antd'
import { collectOkIds, invertOk, type StatusFilter } from './selection'
import type { ImportJobRow } from '@/types/import'

const { Text } = Typography

interface Props {
  rows: ImportJobRow[]
  filter: StatusFilter
  onFilterChange: (f: StatusFilter) => void
  selectedIds: number[]
  onSelectionChange: (ids: number[]) => void
}

export function ImportToolbar({
  rows, filter, onFilterChange,
  selectedIds, onSelectionChange,
}: Props) {
  const allOkIds = collectOkIds(rows)

  return (
    <Space size={16} style={{ marginBottom: 12, flexWrap: 'wrap' }}>
      <Space size={8}>
        <Text>狀態：</Text>
        <Radio.Group
          data-testid="import-filter-radio"
          value={filter}
          onChange={(e) => onFilterChange(e.target.value as StatusFilter)}
          optionType="button"
          buttonStyle="solid"
          options={[
            { value: 'ALL', label: '全部' },
            { value: 'OK', label: 'OK' },
            { value: 'ERROR', label: 'ERROR' },
            { value: 'DUPLICATE', label: 'DUPLICATE' },
          ]}
        />
      </Space>
      <Space size={8}>
        <Button
          data-testid="import-bulk-select-ok"
          onClick={() => onSelectionChange(allOkIds)}
        >全選 OK 列</Button>
        <Button
          data-testid="import-bulk-invert"
          onClick={() => onSelectionChange(invertOk(selectedIds, allOkIds))}
        >反選</Button>
        <Button
          data-testid="import-bulk-clear"
          onClick={() => onSelectionChange([])}
        >清空</Button>
      </Space>
      <Text type="secondary">已選：{selectedIds.length}</Text>
    </Space>
  )
}

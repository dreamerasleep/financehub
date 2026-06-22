import { useEffect, useMemo, useState } from 'react'
import {
  Button, Card, Popconfirm, Space, Statistic, Table, Tag, Typography, Upload, message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { InboxOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  cancelImport, commitImport, getImport, uploadImport,
} from '@/api/imports'
import type {
  ImportJob, ImportJobDetail, ImportJobRow, ImportJobRowStatus, PatchRowResponse,
} from '@/types/import'
import { ImportToolbar } from '@/components/import/ImportToolbar'
import { ImportRowEditDrawer } from '@/components/import/ImportRowEditDrawer'
import { applyFilter, collectOkIds, type StatusFilter } from '@/components/import/selection'

const { Title, Paragraph, Text } = Typography
const { Dragger } = Upload

const STATUS_COLOR: Record<ImportJobRowStatus, string> = {
  OK: 'green',
  ERROR: 'red',
  DUPLICATE: 'orange',
}

export function ImportPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [activeJobId, setActiveJobId] = useState<number | null>(null)
  const [selectedRowIds, setSelectedRowIds] = useState<number[]>([])
  const [filter, setFilter] = useState<StatusFilter>('ALL')
  const [editRowId, setEditRowId] = useState<number | null>(null)

  const detailQuery = useQuery({
    queryKey: ['imports', activeJobId],
    queryFn: () => getImport(activeJobId as number),
    enabled: activeJobId != null,
  })

  useEffect(() => {
    if (detailQuery.data) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- one-shot init of sticky selection when async data arrives
      setSelectedRowIds((prev) => {
        if (prev.length === 0) return collectOkIds(detailQuery.data!.rows)
        return prev
      })
    }
  }, [detailQuery.data])

  const uploadMutation = useMutation({
    mutationFn: (file: File) => uploadImport(file),
    onSuccess: (job: ImportJob) => {
      message.success(`已建立匯入工作 #${job.id}`)
      setActiveJobId(job.id)
      setSelectedRowIds([])
      setFilter('ALL')
    },
    onError: (err) => message.error((err as Error).message ?? '上傳失敗'),
  })

  const commitMutation = useMutation({
    mutationFn: () => commitImport(activeJobId as number,
        selectedRowIds.length > 0 ? selectedRowIds : undefined),
    onSuccess: (result) => {
      message.success(`已匯入 ${result.committedCount} 筆`)
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      setActiveJobId(null)
      navigate('/transactions')
    },
    onError: (err) => message.error((err as Error).message ?? '匯入失敗'),
  })

  const cancelMutation = useMutation({
    mutationFn: () => cancelImport(activeJobId as number),
    onSuccess: () => {
      message.info('已取消匯入')
      setActiveJobId(null)
    },
  })

  const rows = useMemo<ImportJobRow[]>(
    () => detailQuery.data?.rows ?? [],
    [detailQuery.data],
  )
  const visibleRows = useMemo(() => applyFilter(rows, filter), [rows, filter])

  const handlePatched = (resp: PatchRowResponse) => {
    if (!activeJobId) return
    const cacheKey = ['imports', activeJobId] as const
    queryClient.setQueryData<ImportJobDetail>(cacheKey, (prev) => {
      if (!prev) return prev
      return {
        job: resp.job,
        rows: prev.rows.map((r) => (r.id === resp.row.id ? resp.row : r)),
      }
    })
    if (resp.row.status === 'OK' && !selectedRowIds.includes(resp.row.id)) {
      setSelectedRowIds((prev) => [...prev, resp.row.id])
    }
  }

  const columns: ColumnsType<ImportJobRow> = [
    { title: '#', dataIndex: 'rowIndex', width: 60 },
    { title: '狀態', dataIndex: 'status', width: 100, render: (s: ImportJobRowStatus) =>
        <Tag color={STATUS_COLOR[s]}>{s}</Tag> },
    { title: '日期', dataIndex: 'parsedDate', width: 120 },
    { title: '類型', dataIndex: 'parsedType', width: 90 },
    { title: '金額', dataIndex: 'parsedAmount', width: 110, align: 'right' as const,
      render: (v: number | null) => v?.toFixed(2) ?? '—' },
    { title: '備註', dataIndex: 'parsedNote', ellipsis: true },
    { title: '錯誤', dataIndex: 'errorMessage', ellipsis: true,
      render: (m: string | null) => m ?? '' },
    {
      title: '操作', width: 80,
      render: (_: unknown, r: ImportJobRow) => (
        r.status === 'OK' ? null : (
          <Button
            type="link"
            size="small"
            data-testid={`import-edit-row-${r.id}`}
            onClick={() => setEditRowId(r.id)}
          >編輯</Button>
        )
      ),
    },
  ]

  const editingRow = detailQuery.data?.rows.find((r) => r.id === editRowId) ?? null

  return (
    <Space direction="vertical" size={24} style={{ width: '100%' }}>
      <Card>
        <Title level={3}>匯入交易</Title>
        <Paragraph>
          支援 <Text code>.csv</Text> 與 <Text code>.xlsx</Text>,每檔最多 10000 列,5 MB 以內。
        </Paragraph>
        <Paragraph type="secondary">
          期望欄位:<Text code>date, type, account, amount, category, to_account, note</Text>
        </Paragraph>
        <Dragger
          multiple={false}
          accept=".csv,.xlsx"
          showUploadList={false}
          disabled={uploadMutation.isPending}
          customRequest={({ file }) => uploadMutation.mutate(file as File)}
        >
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">點擊或拖放檔案到此處上傳</p>
          <p className="ant-upload-hint">上傳後會進入預覽,再按確認才會匯入。</p>
        </Dragger>
      </Card>

      {detailQuery.data && (
        <Card
          title={`工作 #${detailQuery.data.job.id} — ${detailQuery.data.job.filename}`}
          extra={
            <Space>
              <Popconfirm title="取消這批匯入?" onConfirm={() => cancelMutation.mutate()}>
                <Button>取消</Button>
              </Popconfirm>
              <Button
                type="primary"
                disabled={selectedRowIds.length === 0}
                loading={commitMutation.isPending}
                onClick={() => commitMutation.mutate()}
              >
                確認匯入({selectedRowIds.length})
              </Button>
            </Space>
          }
        >
          <Space size={32} style={{ marginBottom: 16 }}>
            <Statistic title="總列數" value={detailQuery.data.job.rowCount} />
            <Statistic title="OK" value={detailQuery.data.job.okCount} valueStyle={{ color: '#52c41a' }} />
            <Statistic title="錯誤" value={detailQuery.data.job.errorCount} valueStyle={{ color: '#cf1322' }} />
            <Statistic title="重複" value={detailQuery.data.job.dupCount} valueStyle={{ color: '#fa8c16' }} />
          </Space>

          <ImportToolbar
            rows={rows}
            filter={filter}
            onFilterChange={setFilter}
            selectedIds={selectedRowIds}
            onSelectionChange={setSelectedRowIds}
          />

          <Table<ImportJobRow>
            rowKey="id"
            size="small"
            pagination={{ pageSize: 20 }}
            dataSource={visibleRows}
            columns={columns}
            rowSelection={{
              selectedRowKeys: selectedRowIds,
              onChange: (keys) => setSelectedRowIds(keys as number[]),
              getCheckboxProps: (r) => ({ disabled: r.status !== 'OK' }),
              preserveSelectedRowKeys: true,
            }}
          />

          <ImportRowEditDrawer
            jobId={detailQuery.data.job.id}
            row={editingRow}
            open={editRowId != null}
            onClose={() => setEditRowId(null)}
            onPatched={handlePatched}
          />
        </Card>
      )}
    </Space>
  )
}

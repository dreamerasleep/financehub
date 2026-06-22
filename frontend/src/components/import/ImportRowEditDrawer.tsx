import { useEffect, useMemo } from 'react'
import {
  Button, DatePicker, Drawer, Form, Input, InputNumber, Select, Space, message,
} from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { patchImportRow } from '@/api/imports'
import { listAccounts } from '@/api/accounts'
import { listCategories } from '@/api/categories'
import type { ImportJobRow, PatchRowRequest, PatchRowResponse } from '@/types/import'

interface Props {
  jobId: number
  row: ImportJobRow | null
  open: boolean
  onClose: () => void
  onPatched: (resp: PatchRowResponse) => void
}

type TxType = 'INCOME' | 'EXPENSE' | 'TRANSFER'

interface FormValues {
  date: Dayjs | null
  type: TxType
  account: string
  amount: number | null
  category: string
  to_account: string
  note: string
}

export function ImportRowEditDrawer({ jobId, row, open, onClose, onPatched }: Props) {
  const [form] = Form.useForm<FormValues>()
  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: listAccounts })
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: listCategories })

  const initial = useMemo<FormValues | null>(() => {
    if (!row) return null
    let parsed: Record<string, string> = {}
    try { parsed = JSON.parse(row.rawJson) } catch { /* keep empty */ }
    const dateRaw = parsed.date ?? ''
    return {
      date: dateRaw ? dayjs(dateRaw) : null,
      type: (parsed.type as TxType) ?? 'EXPENSE',
      account: parsed.account ?? '',
      amount: parsed.amount ? Number(parsed.amount) : null,
      category: parsed.category ?? '',
      to_account: parsed.to_account ?? '',
      note: parsed.note ?? '',
    }
  }, [row])

  useEffect(() => {
    if (open && initial) form.setFieldsValue(initial)
  }, [open, initial, form])

  const mutation = useMutation({
    mutationFn: (body: PatchRowRequest) =>
      patchImportRow(jobId, row!.id, body),
    onSuccess: (resp) => {
      const newStatus = resp.row.status
      if (newStatus === 'OK') {
        message.success(`列 #${resp.row.rowIndex} 現為 OK,已自動勾選`)
      } else if (newStatus === 'DUPLICATE') {
        message.warning(`列 #${resp.row.rowIndex} 仍為 DUPLICATE`)
      } else {
        message.error(`列 #${resp.row.rowIndex} 仍為 ERROR:${resp.row.errorMessage ?? ''}`)
      }
      onPatched(resp)
      onClose()
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? '更新失敗'
      message.error(msg)
    },
  })

  const onFinish = (values: FormValues) => {
    const body: PatchRowRequest = {
      date: values.date ? values.date.format('YYYY-MM-DD') : '',
      type: values.type,
      account: values.account,
      amount: values.amount != null ? String(values.amount) : '',
      category: values.type === 'TRANSFER' ? '' : values.category,
      to_account: values.type === 'TRANSFER' ? values.to_account : '',
      note: values.note ?? '',
    }
    mutation.mutate(body)
  }

  const type = Form.useWatch('type', form) as TxType | undefined
  const accountName = Form.useWatch('account', form) as string | undefined

  return (
    <Drawer
      title={row ? `編輯列 #${row.rowIndex}` : ''}
      open={open}
      onClose={onClose}
      width={480}
      data-testid="import-edit-drawer"
      destroyOnClose
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            loading={mutation.isPending}
            data-testid="import-edit-submit"
            onClick={() => form.submit()}
          >儲存並重新驗證</Button>
        </Space>
      }
    >
      <Form<FormValues> form={form} layout="vertical" onFinish={onFinish}>
        <Form.Item name="date" label="日期" rules={[{ required: true, message: '必填' }]}>
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="type" label="類型" rules={[{ required: true }]}>
          <Select options={[
            { value: 'INCOME', label: 'INCOME' },
            { value: 'EXPENSE', label: 'EXPENSE' },
            { value: 'TRANSFER', label: 'TRANSFER' },
          ]} />
        </Form.Item>
        <Form.Item name="account" label="帳戶" rules={[{ required: true }]}>
          <Select
            options={(accountsQuery.data ?? []).map((a) => ({ value: a.name, label: a.name }))}
            loading={accountsQuery.isLoading}
          />
        </Form.Item>
        <Form.Item name="amount" label="金額" rules={[{ required: true, type: 'number', min: 0.01 }]}>
          <InputNumber style={{ width: '100%' }} precision={2} min={0.01} />
        </Form.Item>
        <Form.Item
          name="category"
          label="分類"
          rules={type !== 'TRANSFER' ? [{ required: true }] : []}
        >
          <Select
            disabled={type === 'TRANSFER'}
            allowClear
            loading={categoriesQuery.isLoading}
            options={(categoriesQuery.data ?? [])
              .filter((c) => c.kind === type)
              .map((c) => ({ value: c.name, label: c.name }))}
          />
        </Form.Item>
        <Form.Item
          name="to_account"
          label="轉入帳戶"
          rules={type === 'TRANSFER' ? [{ required: true }] : []}
        >
          <Select
            disabled={type !== 'TRANSFER'}
            allowClear
            options={(accountsQuery.data ?? [])
              .filter((a) => a.name !== accountName)
              .map((a) => ({ value: a.name, label: a.name }))}
          />
        </Form.Item>
        <Form.Item name="note" label="備註">
          <Input maxLength={255} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}

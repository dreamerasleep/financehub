import { useMemo, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { Dayjs } from 'dayjs'
import {
  createTransaction,
  deleteTransaction,
  listTransactions,
  updateTransaction,
} from '@/api/transactions'
import { listAccounts } from '@/api/accounts'
import { listCategories } from '@/api/categories'
import {
  TRANSACTION_TYPES,
  TRANSACTION_TYPE_LABEL,
} from '@/types/transaction'
import type { Transaction, TransactionInput, TransactionType } from '@/types/transaction'
import type { Category, CategoryKind } from '@/types/category'

const { Title } = Typography
const { RangePicker } = DatePicker

interface FormValues {
  accountId: number
  toAccountId?: number
  categoryId?: number
  type: TransactionType
  amount: number
  txnDate: Dayjs
  note?: string
}

const typeTagColor: Record<TransactionType, string> = {
  INCOME: 'green',
  EXPENSE: 'red',
  TRANSFER: 'blue',
}

const amountColor: Record<TransactionType, string> = {
  INCOME: '#3f8600',
  EXPENSE: '#cf1322',
  TRANSFER: '#1677ff',
}

const amountSign: Record<TransactionType, string> = {
  INCOME: '+',
  EXPENSE: '-',
  TRANSFER: '',
}

export function TransactionsPage() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Transaction | null>(null)
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [form] = Form.useForm<FormValues>()
  const selectedType = Form.useWatch('type', form)
  const selectedAccountId = Form.useWatch('accountId', form)
  const isTransfer = selectedType === 'TRANSFER'

  const params = range
    ? { from: range[0].format('YYYY-MM-DD'), to: range[1].format('YYYY-MM-DD') }
    : undefined

  const { data: transactions = [], isLoading } = useQuery({
    queryKey: ['transactions', params],
    queryFn: () => listTransactions(params),
  })

  const { data: accounts = [] } = useQuery({
    queryKey: ['accounts'],
    queryFn: listAccounts,
  })

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: listCategories,
  })

  const accountMap = useMemo(
    () => new Map(accounts.map((a) => [a.id, a])),
    [accounts],
  )
  const categoryMap = useMemo(
    () => new Map(categories.map((c) => [c.id, c])),
    [categories],
  )

  const filteredCategories = useMemo<Category[]>(() => {
    if (!selectedType || selectedType === 'TRANSFER') return []
    const kind: CategoryKind = selectedType
    return categories.filter((c) => c.kind === kind)
  }, [categories, selectedType])

  const sourceCurrency = selectedAccountId
    ? accountMap.get(selectedAccountId)?.currency
    : undefined

  const transferTargetOptions = useMemo(() => {
    if (!isTransfer) return []
    return accounts
      .filter((a) => a.id !== selectedAccountId && a.currency === sourceCurrency)
      .map((a) => ({ value: a.id, label: `${a.name}（${a.currency}）` }))
  }, [accounts, isTransfer, selectedAccountId, sourceCurrency])

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['transactions'] })
    qc.invalidateQueries({ queryKey: ['accounts'] })
  }

  const createMutation = useMutation({
    mutationFn: createTransaction,
    onSuccess: () => {
      message.success('交易已新增')
      invalidate()
      closeModal()
    },
    onError: () => message.error('新增失敗'),
  })

  const updateMutation = useMutation({
    mutationFn: (vars: { id: number; body: TransactionInput }) =>
      updateTransaction(vars.id, vars.body),
    onSuccess: () => {
      message.success('交易已更新')
      invalidate()
      closeModal()
    },
    onError: () => message.error('更新失敗'),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteTransaction,
    onSuccess: () => {
      message.success('交易已刪除')
      invalidate()
    },
    onError: () => message.error('刪除失敗'),
  })

  const openCreate = () => {
    if (accounts.length === 0) {
      message.warning('請先建立帳戶')
      return
    }
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      type: 'EXPENSE',
      txnDate: dayjs(),
      accountId: accounts[0].id,
    })
    setOpen(true)
  }

  const openEdit = (t: Transaction) => {
    setEditing(t)
    form.setFieldsValue({
      accountId: t.accountId,
      toAccountId: t.toAccountId ?? undefined,
      categoryId: t.categoryId ?? undefined,
      type: t.type,
      amount: t.amount,
      txnDate: dayjs(t.txnDate),
      note: t.note ?? undefined,
    })
    setOpen(true)
  }

  const closeModal = () => {
    setOpen(false)
    setEditing(null)
    form.resetFields()
  }

  const onTypeChange = (next: TransactionType) => {
    if (next === 'TRANSFER') {
      form.setFieldsValue({ categoryId: undefined })
    } else {
      form.setFieldsValue({ toAccountId: undefined })
      const currentCatId = form.getFieldValue('categoryId') as number | undefined
      const cat = currentCatId ? categoryMap.get(currentCatId) : undefined
      if (cat && cat.kind !== next) {
        form.setFieldsValue({ categoryId: undefined })
      }
    }
  }

  const onAccountChange = () => {
    if (isTransfer) {
      form.setFieldsValue({ toAccountId: undefined })
    }
  }

  const onSubmit = async () => {
    const values = await form.validateFields()
    const isTransferSubmit = values.type === 'TRANSFER'
    const body: TransactionInput = {
      accountId: values.accountId,
      toAccountId: isTransferSubmit ? values.toAccountId : undefined,
      categoryId: isTransferSubmit ? undefined : values.categoryId,
      type: values.type,
      amount: values.amount,
      txnDate: values.txnDate.format('YYYY-MM-DD'),
      note: values.note?.trim() || undefined,
    }
    if (editing) {
      updateMutation.mutate({ id: editing.id, body })
    } else {
      createMutation.mutate(body)
    }
  }

  const columns: ColumnsType<Transaction> = [
    {
      title: '日期',
      dataIndex: 'txnDate',
      key: 'txnDate',
      width: 120,
    },
    {
      title: '類型',
      dataIndex: 'type',
      key: 'type',
      width: 80,
      render: (t: TransactionType) => (
        <Tag color={typeTagColor[t]}>{TRANSACTION_TYPE_LABEL[t]}</Tag>
      ),
    },
    {
      title: '帳戶',
      key: 'accountId',
      render: (_, row) => {
        const from = accountMap.get(row.accountId)?.name ?? `#${row.accountId}`
        if (row.type === 'TRANSFER' && row.toAccountId != null) {
          const to = accountMap.get(row.toAccountId)?.name ?? `#${row.toAccountId}`
          return `${from} → ${to}`
        }
        return from
      },
    },
    {
      title: '分類',
      dataIndex: 'categoryId',
      key: 'categoryId',
      render: (id: number | null) => (id == null ? '—' : categoryMap.get(id)?.name ?? `#${id}`),
    },
    {
      title: '金額',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      render: (v: number, row) => (
        <span style={{ color: amountColor[row.type] }}>
          {amountSign[row.type]}
          {Number(v).toLocaleString(undefined, { minimumFractionDigits: 2 })}
        </span>
      ),
    },
    {
      title: '備註',
      dataIndex: 'note',
      key: 'note',
      ellipsis: true,
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_, row) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => openEdit(row)}>
            編輯
          </Button>
          <Popconfirm
            title="確定刪除？"
            okText="刪除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => deleteMutation.mutate(row.id)}
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              刪除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Card>
      <Space
        style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}
      >
        <Title level={3} style={{ margin: 0 }}>
          交易紀錄
        </Title>
        <Space>
          <RangePicker
            value={range ?? undefined}
            onChange={(v) =>
              setRange(v && v[0] && v[1] ? [v[0], v[1]] : null)
            }
            allowClear
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增交易
          </Button>
        </Space>
      </Space>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={transactions}
        loading={isLoading}
        pagination={{ pageSize: 20, showSizeChanger: false }}
      />

      <Modal
        title={editing ? '編輯交易' : '新增交易'}
        open={open}
        onCancel={closeModal}
        onOk={onSubmit}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        okText="儲存"
        cancelText="取消"
        destroyOnHidden
      >
        <Form<FormValues> form={form} layout="vertical">
          <Form.Item name="type" label="類型" rules={[{ required: true }]}>
            <Select
              onChange={onTypeChange}
              options={TRANSACTION_TYPES.map((t) => ({
                value: t,
                label: TRANSACTION_TYPE_LABEL[t],
              }))}
            />
          </Form.Item>
          <Form.Item
            name="accountId"
            label={isTransfer ? '來源帳戶' : '帳戶'}
            rules={[{ required: true, message: '請選擇帳戶' }]}
          >
            <Select
              onChange={onAccountChange}
              options={accounts.map((a) => ({
                value: a.id,
                label: `${a.name}（${a.currency}）`,
              }))}
            />
          </Form.Item>
          {isTransfer ? (
            <Form.Item
              name="toAccountId"
              label="目標帳戶"
              rules={[{ required: true, message: '請選擇目標帳戶' }]}
              extra={sourceCurrency ? `僅顯示同幣別（${sourceCurrency}）帳戶` : undefined}
            >
              <Select
                options={transferTargetOptions}
                placeholder={selectedAccountId ? undefined : '請先選擇來源帳戶'}
                disabled={!selectedAccountId}
                notFoundContent="沒有可轉入的同幣別帳戶"
              />
            </Form.Item>
          ) : (
            <Form.Item
              name="categoryId"
              label="分類"
              rules={[{ required: true, message: '請選擇分類' }]}
            >
              <Select
                options={filteredCategories.map((c) => ({
                  value: c.id,
                  label: c.name,
                }))}
                placeholder={selectedType ? undefined : '請先選擇類型'}
                disabled={!selectedType}
              />
            </Form.Item>
          )}
          <Form.Item
            name="amount"
            label="金額"
            rules={[
              { required: true, message: '請輸入金額' },
              {
                validator: (_, v: number) =>
                  v !== undefined && v > 0
                    ? Promise.resolve()
                    : Promise.reject(new Error('須為正數')),
              },
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={0.01} precision={2} />
          </Form.Item>
          <Form.Item
            name="txnDate"
            label="日期"
            rules={[{ required: true, message: '請選擇日期' }]}
          >
            <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
          </Form.Item>
          <Form.Item name="note" label="備註">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}

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
  categoryId: number
  type: TransactionType
  amount: number
  txnDate: Dayjs
  note?: string
}

const typeTagColor: Record<TransactionType, string> = {
  INCOME: 'green',
  EXPENSE: 'red',
}

export function TransactionsPage() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Transaction | null>(null)
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [form] = Form.useForm<FormValues>()
  const selectedType = Form.useWatch('type', form)

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
    if (!selectedType) return categories
    const kind: CategoryKind = selectedType
    return categories.filter((c) => c.kind === kind)
  }, [categories, selectedType])

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
      categoryId: t.categoryId,
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

  const onTypeChange = () => {
    const currentCatId = form.getFieldValue('categoryId') as number | undefined
    if (!currentCatId) return
    const cat = categoryMap.get(currentCatId)
    const desiredKind = form.getFieldValue('type') as TransactionType | undefined
    if (cat && desiredKind && cat.kind !== desiredKind) {
      form.setFieldsValue({ categoryId: undefined })
    }
  }

  const onSubmit = async () => {
    const values = await form.validateFields()
    const body: TransactionInput = {
      accountId: values.accountId,
      categoryId: values.categoryId,
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
      dataIndex: 'accountId',
      key: 'accountId',
      render: (id: number) => accountMap.get(id)?.name ?? `#${id}`,
    },
    {
      title: '分類',
      dataIndex: 'categoryId',
      key: 'categoryId',
      render: (id: number) => categoryMap.get(id)?.name ?? `#${id}`,
    },
    {
      title: '金額',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      render: (v: number, row) => (
        <span style={{ color: row.type === 'EXPENSE' ? '#cf1322' : '#3f8600' }}>
          {row.type === 'EXPENSE' ? '-' : '+'}
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
            label="帳戶"
            rules={[{ required: true, message: '請選擇帳戶' }]}
          >
            <Select
              options={accounts.map((a) => ({ value: a.id, label: a.name }))}
            />
          </Form.Item>
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

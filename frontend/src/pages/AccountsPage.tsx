import { useState } from 'react'
import {
  Button,
  Card,
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
import {
  createAccount,
  deleteAccount,
  listAccounts,
  updateAccount,
} from '@/api/accounts'
import {
  ACCOUNT_TYPES,
  ACCOUNT_TYPE_LABEL,
} from '@/types/account'
import type { Account, AccountInput, AccountType } from '@/types/account'

const { Title } = Typography

const typeColor: Record<AccountType, string> = {
  CHECKING: 'blue',
  SAVING: 'green',
  CREDIT: 'volcano',
  INVESTMENT: 'purple',
  CASH: 'gold',
}

export function AccountsPage() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Account | null>(null)
  const [form] = Form.useForm<AccountInput>()

  const { data: accounts = [], isLoading } = useQuery({
    queryKey: ['accounts'],
    queryFn: listAccounts,
  })

  const createMutation = useMutation({
    mutationFn: createAccount,
    onSuccess: () => {
      message.success('帳戶已新增')
      qc.invalidateQueries({ queryKey: ['accounts'] })
      closeModal()
    },
    onError: () => message.error('新增失敗'),
  })

  const updateMutation = useMutation({
    mutationFn: (vars: { id: number; body: AccountInput }) =>
      updateAccount(vars.id, vars.body),
    onSuccess: () => {
      message.success('帳戶已更新')
      qc.invalidateQueries({ queryKey: ['accounts'] })
      closeModal()
    },
    onError: () => message.error('更新失敗'),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteAccount,
    onSuccess: () => {
      message.success('帳戶已刪除')
      qc.invalidateQueries({ queryKey: ['accounts'] })
    },
    onError: () => message.error('刪除失敗'),
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ type: 'SAVING', currency: 'TWD', currentBalance: 0 })
    setOpen(true)
  }

  const openEdit = (a: Account) => {
    setEditing(a)
    form.setFieldsValue({
      name: a.name,
      type: a.type,
      currency: a.currency,
      currentBalance: a.currentBalance,
    })
    setOpen(true)
  }

  const closeModal = () => {
    setOpen(false)
    setEditing(null)
    form.resetFields()
  }

  const onSubmit = async () => {
    const values = await form.validateFields()
    if (editing) {
      updateMutation.mutate({ id: editing.id, body: values })
    } else {
      createMutation.mutate(values)
    }
  }

  const columns: ColumnsType<Account> = [
    { title: '名稱', dataIndex: 'name', key: 'name' },
    {
      title: '類型',
      dataIndex: 'type',
      key: 'type',
      render: (t: AccountType) => (
        <Tag color={typeColor[t]}>{ACCOUNT_TYPE_LABEL[t]}</Tag>
      ),
    },
    { title: '幣別', dataIndex: 'currency', key: 'currency', width: 80 },
    {
      title: '當前餘額',
      dataIndex: 'currentBalance',
      key: 'currentBalance',
      align: 'right',
      render: (v: number) => v.toLocaleString(undefined, { minimumFractionDigits: 2 }),
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => openEdit(record)}
          >
            編輯
          </Button>
          <Popconfirm
            title="確定刪除？"
            okText="刪除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => deleteMutation.mutate(record.id)}
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
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>
          帳戶
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新增帳戶
        </Button>
      </Space>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={accounts}
        loading={isLoading}
        pagination={false}
      />

      <Modal
        title={editing ? '編輯帳戶' : '新增帳戶'}
        open={open}
        onCancel={closeModal}
        onOk={onSubmit}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        okText="儲存"
        cancelText="取消"
        destroyOnHidden
      >
        <Form<AccountInput> form={form} layout="vertical">
          <Form.Item
            name="name"
            label="名稱"
            rules={[{ required: true, message: '請輸入名稱' }]}
          >
            <Input placeholder="例：玉山活存" />
          </Form.Item>
          <Form.Item name="type" label="類型" rules={[{ required: true }]}>
            <Select
              options={ACCOUNT_TYPES.map((t) => ({
                value: t,
                label: ACCOUNT_TYPE_LABEL[t],
              }))}
            />
          </Form.Item>
          <Form.Item
            name="currency"
            label="幣別"
            rules={[
              { required: true, message: '請輸入幣別' },
              { len: 3, message: '須為 3 字 ISO 代碼' },
            ]}
          >
            <Input maxLength={3} style={{ textTransform: 'uppercase' }} />
          </Form.Item>
          <Form.Item
            name="currentBalance"
            label="當前餘額"
            rules={[{ required: true, message: '請輸入餘額' }]}
          >
            <InputNumber style={{ width: '100%' }} precision={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}

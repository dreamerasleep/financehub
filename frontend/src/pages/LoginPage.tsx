import { useState } from 'react'
import { Card, Form, Input, Button, Typography, message, Tabs } from 'antd'
import { useNavigate, useLocation } from 'react-router-dom'
import { login, register } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import type { AxiosError } from 'axios'

const { Title, Text } = Typography

type LoginValues = { email: string; password: string }
type RegisterValues = { email: string; name: string; password: string }

export function LoginPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [loading, setLoading] = useState(false)
  const setAuth = useAuthStore((s) => s.setAuth)
  const navigate = useNavigate()
  const location = useLocation()

  const redirectTo =
    (location.state as { from?: { pathname?: string } } | null)?.from?.pathname
    ?? '/accounts'

  const handleLogin = async (values: LoginValues) => {
    setLoading(true)
    try {
      const data = await login(values)
      setAuth(data)
      message.success('登入成功')
      navigate(redirectTo, { replace: true })
    } catch (e) {
      const err = e as AxiosError<{ detail?: string }>
      message.error(err.response?.data?.detail ?? '登入失敗，請檢查帳密')
    } finally {
      setLoading(false)
    }
  }

  const handleRegister = async (values: RegisterValues) => {
    setLoading(true)
    try {
      const data = await register(values)
      setAuth(data)
      message.success('註冊成功，已自動登入')
      navigate('/accounts', { replace: true })
    } catch (e) {
      const err = e as AxiosError<{ detail?: string }>
      message.error(err.response?.data?.detail ?? '註冊失敗')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f0f2f5',
        padding: 24,
      }}
    >
      <Card style={{ width: 400 }}>
        <Title level={3} style={{ textAlign: 'center', marginTop: 0 }}>
          FinanceHub
        </Title>
        <Text type="secondary" style={{ display: 'block', textAlign: 'center', marginBottom: 24 }}>
          個人財務管理
        </Text>
        <Tabs
          activeKey={mode}
          onChange={(k) => setMode(k as 'login' | 'register')}
          centered
          items={[
            {
              key: 'login',
              label: '登入',
              children: (
                <Form<LoginValues> layout="vertical" onFinish={handleLogin} disabled={loading}>
                  <Form.Item
                    name="email"
                    label="Email"
                    rules={[{ required: true, type: 'email', message: '請填入有效的 email' }]}
                  >
                    <Input autoComplete="email" />
                  </Form.Item>
                  <Form.Item
                    name="password"
                    label="密碼"
                    rules={[{ required: true, message: '請輸入密碼' }]}
                  >
                    <Input.Password autoComplete="current-password" />
                  </Form.Item>
                  <Button type="primary" htmlType="submit" loading={loading} block>
                    登入
                  </Button>
                </Form>
              ),
            },
            {
              key: 'register',
              label: '註冊',
              children: (
                <Form<RegisterValues> layout="vertical" onFinish={handleRegister} disabled={loading}>
                  <Form.Item
                    name="email"
                    label="Email"
                    rules={[{ required: true, type: 'email', message: '請填入有效的 email' }]}
                  >
                    <Input autoComplete="email" />
                  </Form.Item>
                  <Form.Item
                    name="name"
                    label="顯示名稱"
                    rules={[{ required: true, message: '請填入名稱' }]}
                  >
                    <Input autoComplete="name" />
                  </Form.Item>
                  <Form.Item
                    name="password"
                    label="密碼"
                    rules={[
                      { required: true, message: '請輸入密碼' },
                      { min: 8, message: '至少 8 字' },
                    ]}
                  >
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                  <Button type="primary" htmlType="submit" loading={loading} block>
                    註冊並登入
                  </Button>
                </Form>
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}

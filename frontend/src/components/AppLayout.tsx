import { Layout, Menu, Button, Space, Typography } from 'antd'
import { LogoutOutlined, BankOutlined, SwapOutlined } from '@ant-design/icons'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/auth'

const { Header, Content } = Layout
const { Text } = Typography

export function AppLayout() {
  const { name, email, clear } = useAuthStore()
  const navigate = useNavigate()
  const location = useLocation()

  const handleLogout = () => {
    clear()
    navigate('/login', { replace: true })
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
        <Text strong style={{ color: '#fff', fontSize: 18 }}>FinanceHub</Text>
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[location.pathname]}
          items={[
            {
              key: '/accounts',
              icon: <BankOutlined />,
              label: <Link to="/accounts">帳戶</Link>,
            },
            {
              key: '/transactions',
              icon: <SwapOutlined />,
              label: <Link to="/transactions">交易</Link>,
            },
          ]}
          style={{ flex: 1, minWidth: 0, background: 'transparent' }}
        />
        <Space>
          <Text style={{ color: 'rgba(255,255,255,0.85)' }}>
            {name ?? email}
          </Text>
          <Button
            type="text"
            icon={<LogoutOutlined />}
            onClick={handleLogout}
            style={{ color: '#fff' }}
          >
            登出
          </Button>
        </Space>
      </Header>
      <Content style={{ padding: '24px 48px' }}>
        <Outlet />
      </Content>
    </Layout>
  )
}

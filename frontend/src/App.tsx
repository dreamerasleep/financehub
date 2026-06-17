import { ConfigProvider } from 'antd'
import zhTW from 'antd/locale/zh_TW'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '@/pages/LoginPage'
import { AccountsPage } from '@/pages/AccountsPage'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { AppLayout } from '@/components/AppLayout'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

function App() {
  return (
    <ConfigProvider locale={zhTW}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />

            <Route element={<ProtectedRoute />}>
              <Route element={<AppLayout />}>
                <Route path="/accounts" element={<AccountsPage />} />
                <Route path="/" element={<Navigate to="/accounts" replace />} />
              </Route>
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </QueryClientProvider>
    </ConfigProvider>
  )
}

export default App

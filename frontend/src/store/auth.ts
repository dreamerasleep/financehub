import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface AuthState {
  token: string | null
  userId: number | null
  email: string | null
  name: string | null
  setAuth: (a: { token: string; userId: number; email: string; name: string }) => void
  clear: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      userId: null,
      email: null,
      name: null,
      setAuth: ({ token, userId, email, name }) =>
        set({ token, userId, email, name }),
      clear: () => set({ token: null, userId: null, email: null, name: null }),
    }),
    { name: 'financehub-auth' },
  ),
)

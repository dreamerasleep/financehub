import { apiClient } from './client'
import type {
  AuthResponse,
  LoginRequest,
  MeResponse,
  RegisterRequest,
} from '@/types/auth'

export async function register(body: RegisterRequest): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/api/v1/auth/register', body)
  return data
}

export async function login(body: LoginRequest): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/api/v1/auth/login', body)
  return data
}

export async function getMe(): Promise<MeResponse> {
  const { data } = await apiClient.get<MeResponse>('/api/v1/auth/me')
  return data
}

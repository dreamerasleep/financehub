export interface AuthResponse {
  token: string
  userId: number
  email: string
  name: string
}

export interface RegisterRequest {
  email: string
  name: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface MeResponse {
  userId: number
  email: string
}

import { apiClient } from '../../../shared/api/apiClient';
import type { AuthUser, LoginCredentials, RegisterCredentials } from '../types/auth';

export async function login(credentials: LoginCredentials): Promise<AuthUser> {
  const response = await apiClient.post<AuthUser>('/api/v1/auth/login', credentials);
  return response.data;
}

export async function register(credentials: RegisterCredentials): Promise<AuthUser> {
  const response = await apiClient.post<AuthUser>('/api/v1/auth/register', credentials);
  return response.data;
}

export async function getCurrentUser(): Promise<AuthUser> {
  const response = await apiClient.get<AuthUser>('/me');
  return response.data;
}

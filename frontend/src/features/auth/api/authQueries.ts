import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getCurrentUser, login, register } from './authApi';
import type { LoginCredentials, RegisterCredentials } from '../types/auth';

export const currentUserQueryKey = ['auth', 'current-user'] as const;

export function useCurrentUserQuery(enabled = false) {
  return useQuery({
    queryKey: currentUserQueryKey,
    queryFn: getCurrentUser,
    enabled,
  });
}

export function useLoginMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (credentials: LoginCredentials) => login(credentials),
    onSuccess: (user) => queryClient.setQueryData(currentUserQueryKey, user),
  });
}

export function useSignupMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (credentials: RegisterCredentials) => register(credentials),
    onSuccess: (user) => queryClient.setQueryData(currentUserQueryKey, user),
  });
}

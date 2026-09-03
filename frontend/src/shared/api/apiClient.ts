import axios from 'axios';

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  withCredentials: true,
});

export interface ApiError {
  kind: 'http' | 'network' | 'unknown';
  message: string;
  status?: number;
}

export function toApiError(error: unknown): ApiError {
  if (!axios.isAxiosError(error)) {
    return { kind: 'unknown', message: 'Something went wrong. Please try again.' };
  }

  if (!error.response) {
    return { kind: 'network', message: 'Unable to reach StudyMate. Check your connection and try again.' };
  }

  if (error.response.status >= 500) {
    return { kind: 'http', status: error.response.status, message: 'StudyMate is temporarily unavailable. Please try again.' };
  }

  const serverMessage = error.response.data?.message;
  if (typeof serverMessage === 'string' && serverMessage.trim()) {
    return { kind: 'http', status: error.response.status, message: serverMessage };
  }

  return { kind: 'http', status: error.response.status, message: 'We could not complete that request. Please check your details and try again.' };
}

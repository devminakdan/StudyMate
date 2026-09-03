import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/apiClient';

export interface Course {
  id: string;
  ownerId: string;
  name: string;
  code: string | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CourseInput { name: string; code: string | null; description: string | null; }

const coursesKey = ['courses'] as const;
export const courseKey = (courseId: string) => [...coursesKey, courseId] as const;

export function useCoursesQuery() {
  return useQuery({
    queryKey: coursesKey,
    queryFn: async () => (await apiClient.get<Course[]>('/api/v1/courses')).data });
}

export function useCourseQuery(courseId: string) {
  return useQuery({
    queryKey: courseKey(courseId),
    queryFn: async () => (await apiClient.get<Course>(`/api/v1/courses/${courseId}`)).data
  });
}

export function useCreateCourseMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: CourseInput) => (await apiClient.post<Course>('/api/v1/courses', input)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: coursesKey }),
  });
}

export function useDeleteCourseMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (courseId: string) => apiClient.delete(`/api/v1/courses/${courseId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: coursesKey }),
  });
}

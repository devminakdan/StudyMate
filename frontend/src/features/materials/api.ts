import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/apiClient';

export type MaterialStatus = 'PENDING' | 'PARSING' | 'CHUNKING' | 'EMBEDDING' | 'INDEXING' | 'READY' | 'FAILED';
export interface CourseMaterial {
  id: string;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  status: MaterialStatus;
  uploadedAt: string;
  errorMessage: string | null;
}

const materialsKey = (courseId: string) => ['courses', courseId, 'materials'] as const;

export function useMaterialsQuery(courseId: string) {
  return useQuery({
    queryKey: materialsKey(courseId),
    queryFn: async () => (await apiClient.get<CourseMaterial[]>(`/api/v1/courses/${courseId}/materials`)).data,
    refetchInterval: (query) => query.state.data?.some(({ status }) => !['READY', 'FAILED'].includes(status)) ? 5_000 : false,
  });
}

export function useUploadMaterialMutation(courseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (file: File) => {
      const data = new FormData();
      data.append('file', file);
      return (await apiClient.post(`/api/v1/courses/${courseId}/materials`, data)).data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: materialsKey(courseId) }),
  });
}

export function useDeleteMaterialMutation(courseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (materialId: string) => apiClient.delete(`/api/v1/courses/${courseId}/materials/${materialId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: materialsKey(courseId) }),
  });
}

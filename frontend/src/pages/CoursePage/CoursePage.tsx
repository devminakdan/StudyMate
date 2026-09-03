import { Alert, Box, Button, Chip, CircularProgress, Container, IconButton, List, ListItem, ListItemText, Stack, Typography } from '@mui/material';
import { useRef, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { useCourseQuery } from '@/features/courses/api';
import { useDeleteMaterialMutation, useMaterialsQuery, useUploadMaterialMutation, type MaterialStatus } from '@/features/materials/api';
import { toApiError } from '@/shared/api/apiClient';

const color: Record<MaterialStatus, 'default' | 'error' | 'success' | 'warning'> = { PENDING: 'default', PARSING: 'warning', CHUNKING: 'warning', EMBEDDING: 'warning', INDEXING: 'warning', READY: 'success', FAILED: 'error' };

export function CoursePage() {
  const { courseId } = useParams();
  const input = useRef<HTMLInputElement>(null);
  const [uploadError, setUploadError] = useState<string>();
  const course = useCourseQuery(courseId!);
  const materials = useMaterialsQuery(courseId!);
  const upload = useUploadMaterialMutation(courseId!);
  const remove = useDeleteMaterialMutation(courseId!);
  if (!courseId) return null;
  const chooseFile = async (file?: File) => {
    if (!file) return;
    if (!['application/pdf', 'application/vnd.openxmlformats-officedocument.presentationml.presentation'].includes(file.type) || file.size > 20 * 1024 * 1024) { setUploadError('Choose a PDF or PPTX file no larger than 20 MB.'); return; }
    try { setUploadError(undefined); await upload.mutateAsync(file); } catch (error) { setUploadError(toApiError(error).message); } finally { if (input.current) input.current.value = ''; }
  };
  return <AppShell><Container maxWidth="md" sx={{ pt: { xs: 4, md: 7 } }}>
    <Button component={RouterLink} to="/courses" variant="text">← All courses</Button>
    {course.isLoading && <Box sx={{ py: 8, textAlign: 'center' }}><CircularProgress /></Box>}
    {course.isError && <Alert severity="error" sx={{ mt: 3 }}>{toApiError(course.error).message}</Alert>}
    {course.data && <><Box sx={{ mb: 4, mt: 3 }}><Typography color="primary" sx={{ fontWeight: 700 }} variant="overline">{course.data.code || 'Course'}</Typography><Typography component="h1" variant="h3">{course.data.name}</Typography>{course.data.description && <Typography color="text.secondary" sx={{ mt: 1 }}>{course.data.description}</Typography>}</Box><Stack direction={{ xs: 'column', md: 'row' }} spacing={3}><Box sx={{ flex: 1 }}><Typography component="h2" variant="h5">Materials</Typography>{materials.isLoading && <CircularProgress sx={{ mt: 3 }} />}{materials.isError && <Alert severity="error" sx={{ mt: 2 }}>{toApiError(materials.error).message}</Alert>}{!materials.isLoading && !materials.data?.length && <Typography color="text.secondary" sx={{ mt: 2 }}>No materials yet.</Typography>}<List disablePadding>{materials.data?.map((material) => <ListItem divider key={material.id} secondaryAction={<IconButton aria-label={`Delete ${material.originalFilename}`} onClick={() => remove.mutate(material.id)}>⌫</IconButton>} sx={{ px: 0 }}><ListItemText primary={<Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><Typography sx={{ fontWeight: 600 }}>{material.originalFilename}</Typography><Chip color={color[material.status]} label={material.status.toLowerCase()} size="small" /></Stack>} secondary={material.errorMessage || `${Math.ceil(material.sizeBytes / 1024)} KB`} /></ListItem>)}</List></Box><Box sx={{ flexBasis: 270 }}><Box sx={{ border: '1px dashed', borderColor: 'primary.main', borderRadius: 2, p: 3, textAlign: 'center' }}><input accept=".pdf,.pptx" hidden onChange={(event) => chooseFile(event.target.files?.[0])} ref={input} type="file" /><Typography sx={{ fontWeight: 700 }}>Add study material</Typography><Typography color="text.secondary" sx={{ my: 1 }} variant="body2">PDF or PPTX · max 20 MB</Typography>{uploadError && <Alert severity="error" sx={{ mb: 1 }}>{uploadError}</Alert>}<Button disabled={upload.isPending} onClick={() => input.current?.click()} variant="outlined">{upload.isPending ? 'Uploading…' : 'Choose a file'}</Button></Box></Box></Stack></>}
  </Container></AppShell>;
}

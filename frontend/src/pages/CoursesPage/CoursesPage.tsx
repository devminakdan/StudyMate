import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Box, Button, Card, CardActionArea, CardContent, CircularProgress, Container, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, Stack, Typography } from '@mui/material';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link as RouterLink } from 'react-router-dom';
import { z } from 'zod';
import { AppShell } from '@/components/layout/AppShell';
import { TextInput } from '@/components/ui/TextInput';
import { useCoursesQuery, useCreateCourseMutation, useDeleteCourseMutation, type Course } from '@/features/courses/api';
import { toApiError } from '@/shared/api/apiClient';

const schema = z.object({ name: z.string().trim().min(1, 'Course name is required.').max(200), code: z.string().max(50).optional(), description: z.string().optional() });
type Values = z.infer<typeof schema>;

export function CoursesPage() {
  const [open, setOpen] = useState(false);
  const courses = useCoursesQuery();
  const create = useCreateCourseMutation();
  const remove = useDeleteCourseMutation();
  const { formState: { errors }, handleSubmit, register, reset, setError } = useForm<Values>({ defaultValues: { name: '', code: '', description: '' }, resolver: zodResolver(schema) });
  const submit = async (values: Values) => {
    try { await create.mutateAsync({ name: values.name.trim(), code: values.code?.trim() || null, description: values.description?.trim() || null }); reset(); setOpen(false); }
    catch (error) { setError('root', { message: toApiError(error).message }); }
  };
  const deleteCourse = (course: Course) => { if (window.confirm(`Delete ${course.name} and all its materials?`)) remove.mutate(course.id); };
  return <AppShell><Container maxWidth="lg" sx={{ pt: { xs: 4, md: 7 } }}>
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'flex-end' }, justifyContent: 'space-between' }}>
      <Box><Typography component="h1" variant="h3">My courses</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>Organize notes and materials by subject.</Typography></Box>
      <Button onClick={() => setOpen(true)} variant="contained">+ New course</Button>
    </Stack>
    <Box sx={{ mt: 5 }}>
      {courses.isLoading && <Box sx={{ py: 8, textAlign: 'center' }}><CircularProgress /></Box>}
      {courses.isError && <Alert severity="error">{toApiError(courses.error).message}</Alert>}
      {!courses.isLoading && !courses.isError && courses.data?.length === 0 && <Box sx={{ border: '1px dashed #c9c5b9', borderRadius: 2, py: 8, textAlign: 'center' }}><Typography variant="h6">Your first course starts here</Typography><Button onClick={() => setOpen(true)} sx={{ mt: 2 }} variant="outlined">Create a course</Button></Box>}
      <Grid container spacing={2.5}>{courses.data?.map((course) => <Grid key={course.id} size={{ xs: 12, sm: 6, md: 4 }}><Card sx={{ position: 'relative' }} variant="outlined"><CardActionArea component={RouterLink} to={`/courses/${course.id}`}><CardContent sx={{ minHeight: 160 }}><Typography color="primary" sx={{ fontWeight: 700 }} variant="overline">{course.code || 'Course'}</Typography><Typography component="h2" sx={{ mt: 1 }} variant="h6">{course.name}</Typography><Typography color="text.secondary" sx={{ mt: 1 }} variant="body2">{course.description || 'No description yet.'}</Typography></CardContent></CardActionArea><IconButton aria-label={`Delete ${course.name}`} onClick={() => deleteCourse(course)} size="small" sx={{ bottom: 12, position: 'absolute', right: 12 }}>⌫</IconButton></Card></Grid>)}</Grid>
    </Box>
    <Dialog fullWidth maxWidth="sm" onClose={() => setOpen(false)} open={open}><DialogTitle>Create a course</DialogTitle><Stack component="form" noValidate onSubmit={handleSubmit(submit)}><DialogContent><Stack spacing={2}>{errors.root?.message && <Alert severity="error">{errors.root.message}</Alert>}<TextInput autoFocus error={Boolean(errors.name)} helperText={errors.name?.message} label="Course name" required {...register('name')} /><TextInput label="Course code" placeholder="CS101" {...register('code')} /><TextInput label="Description" minRows={3} multiline {...register('description')} /></Stack></DialogContent><DialogActions sx={{ p: 3 }}><Button onClick={() => setOpen(false)} type="button">Cancel</Button><Button disabled={create.isPending} type="submit" variant="contained">Create course</Button></DialogActions></Stack></Dialog>
  </Container></AppShell>;
}

import { Alert, Divider, Link, Stack } from '@mui/material';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useLocation, useNavigate, type Location } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { TextInput } from '@/components/ui/TextInput';
import { toApiError } from '@/shared/api/apiClient';
import { useLoginMutation } from '../api/authQueries';
import { loginSchema } from '../schemas/loginSchema';
import type { LoginCredentials } from '../types/auth';

export function LoginForm() {
  const loginMutation = useLoginMutation();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: Location } | null)?.from;
  const {
    formState: { errors },
    handleSubmit,
    register,
    setError,
  } = useForm<LoginCredentials>({
    defaultValues: { email: '', password: '' },
    resolver: zodResolver(loginSchema),
  });
  const { ref: emailRef, ...emailRegistration } = register('email');
  const { ref: passwordRef, ...passwordRegistration } = register('password');

  const onSubmit = async (credentials: LoginCredentials) => {
    try {
      await loginMutation.mutateAsync(credentials);
      navigate(from ?? '/courses');
    } catch (error) {
      const apiError = toApiError(error);
      setError('root', {
        type: 'server',
        message: apiError.status === 401
          ? 'Email or password is incorrect.'
          : apiError.message,
      });
    }
  };

  return (
    <Stack component="form" noValidate spacing={2.25} onSubmit={handleSubmit(onSubmit)}>
      {errors.root?.message && <Alert severity="error">{errors.root.message}</Alert>}
      {loginMutation.isSuccess && <Alert severity="success">You are signed in successfully.</Alert>}
      <TextInput
        autoComplete="email"
        error={Boolean(errors.email)}
        helperText={errors.email?.message}
        inputRef={emailRef}
        label="Email"
        placeholder="you@school.edu"
        required
        type="email"
        {...emailRegistration}
      />
      <TextInput
        autoComplete="current-password"
        auxiliary={<Link component="button" type="button" underline="hover" variant="caption">Forgot password?</Link>}
        error={Boolean(errors.password)}
        helperText={errors.password?.message}
        inputRef={passwordRef}
        label="Password"
        placeholder="••••••••"
        required
        type="password"
        {...passwordRegistration}
      />
      <Button disabled={loginMutation.isPending} loading={loginMutation.isPending} type="submit">
        Sign in
      </Button>
      <Divider aria-hidden="true">OR</Divider>
      <Button type="button" variant="secondary">Continue with Google</Button>
    </Stack>
  );
}

import { Alert, Stack } from '@mui/material';
import { useForm } from 'react-hook-form';
import { Button } from '@/components/ui/Button';
import { TextInput } from '@/components/ui/TextInput';
import { toApiError } from '@/shared/api/apiClient';
import { useSignupMutation } from '../api/authQueries';
import { signupSchema } from '../schemas/signupSchema';
import type { RegisterCredentials } from '../types/auth';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router-dom';

export function SignupForm() {
  const signupMutation = useSignupMutation();
  const navigate = useNavigate();
  const {
    formState: { errors },
    handleSubmit,
    register,
  } = useForm<RegisterCredentials>({
    resolver: zodResolver(signupSchema),
  });

  const { ref: usernameRef, ...usernameRegistration } = register('username');
  const { ref: emailRef, ...emailRegistration } = register('email');
  const { ref: passwordRef, ...passwordRegistration } = register('password');

  const rootError = signupMutation.isError
    ? toApiError(signupMutation.error).message
    : undefined;

  return (
    <Stack
      component="form"
      noValidate
      spacing={2.25}
      onSubmit={handleSubmit(async (credentials) => {
        await signupMutation.mutateAsync(credentials);
        navigate('/courses');
      })}
    >
      {rootError && <Alert severity="error">{rootError}</Alert>}
      {signupMutation.isSuccess && (
        <Alert severity="success">Your account has been created. You’re signed in.</Alert>
      )}
      <TextInput
        autoComplete="name"
        error={Boolean(errors.username)}
        helperText={errors.username?.message}
        label="Name"
        inputRef={usernameRef}
        placeholder="Maya Chen"
        required
        {...usernameRegistration}
      />
      <TextInput
        autoComplete="email"
        error={Boolean(errors.email)}
        helperText={errors.email?.message}
        label="Email"
        inputRef={emailRef}
        placeholder="you@school.edu"
        type="email"
        required
        {...emailRegistration}
      />
      <TextInput
        autoComplete="new-password"
        error={Boolean(errors.password)}
        helperText={errors.password?.message}
        label="Password"
        inputRef={passwordRef}
        placeholder="••••••••"
        type="password"
        required
        {...passwordRegistration}
      />
      <Button disabled={signupMutation.isPending} loading={signupMutation.isPending} type="submit">
        Create account
      </Button>
    </Stack>
  );
}

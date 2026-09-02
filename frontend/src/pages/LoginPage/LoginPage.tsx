import { Link, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { AuthLayout } from '../../components/layout/AuthLayout';
import { LoginForm } from '../../features/auth/components/LoginForm';

export function LoginPage() {
  return (
    <AuthLayout
      title="Welcome back"
      description="Pick up right where you left off."
      footer={
        <Typography component="p" variant="body2">
          New to StudyMate? <Link component={RouterLink} to="/signup" underline="hover">Create an account</Link>
        </Typography>
      }
    >
      <LoginForm />
    </AuthLayout>
  );
}

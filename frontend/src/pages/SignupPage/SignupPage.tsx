import { Link, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { AuthLayout } from '../../components/layout/AuthLayout';
import { SignupForm } from '../../features/auth/components/SignupForm';

export function SignupPage() {
  return (
    <AuthLayout
      description="Start turning your notes into study sessions."
      footer={
        <Typography component="p" variant="body2">
          Already have an account? <Link component={RouterLink} to="/login" underline="hover">Log in</Link>
        </Typography>
      }
      title="Create your account"
    >
      <SignupForm />
    </AuthLayout>
  );
}

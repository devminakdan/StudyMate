import { Box, CircularProgress } from '@mui/material';
import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { useCurrentUserQuery } from '@/features/auth/api/authQueries';
import { LoginPage } from '../pages/LoginPage/LoginPage';
import { SignupPage } from '../pages/SignupPage/SignupPage';
import { CoursesPage } from '../pages/CoursesPage/CoursesPage';
import { CoursePage } from '../pages/CoursePage/CoursePage';

export function AuthRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route element={<RequireAuth />}>
          <Route path="/courses" element={<CoursesPage />} />
          <Route path="/courses/:courseId" element={<CoursePage />} />
        </Route>
        <Route path="*" element={<Navigate replace to="/courses" />} />
      </Routes>
    </BrowserRouter>
  );
}

function RequireAuth() {
  const location = useLocation();
  const currentUserQuery = useCurrentUserQuery(true);

  if (currentUserQuery.isPending) {
    return <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>;
  }

  if (!currentUserQuery.data) {
    return <Navigate replace state={{ from: location }} to="/login" />;
  }

  return <Outlet />;
}

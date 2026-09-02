import { Box, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import { Brand } from '../ui/Brand';

interface AuthLayoutProps {
  children: ReactNode;
  description: string;
  footer: ReactNode;
  title: string;
}

export function AuthLayout({ children, description, footer, title }: AuthLayoutProps) {
  return (
    <Box component="main" className="auth-layout">
      <Box component="aside" className="auth-layout__hero">
        <Brand inverse />
        <Box className="auth-layout__hero-content">
          <Typography component="h1" variant="h1">Study smarter,<br />not longer.</Typography>
          <Typography component="p">Upload your notes, ask questions, and let StudyMate turn them into quizzes built around what you actually need to review.</Typography>
          <Box className="auth-layout__illustration" aria-hidden="true">
            <Box className="auth-layout__paper auth-layout__paper--left" />
            <Box className="auth-layout__paper auth-layout__paper--right" />
            <Box className="auth-layout__orb" />
          </Box>
        </Box>
        <Typography component="small">© 2026 StudyMate</Typography>
      </Box>
      <Box component="section" className="auth-layout__content">
        <Box className="auth-layout__card">
          <Box component="header" className="auth-layout__header">
            <Typography component="h2" variant="h2">{title}</Typography>
            <Typography component="p">{description}</Typography>
          </Box>
          {children}
          <Box component="footer" className="auth-layout__footer">{footer}</Box>
        </Box>
      </Box>
    </Box>
  );
}

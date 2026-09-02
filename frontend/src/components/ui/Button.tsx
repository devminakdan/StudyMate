import { Button as MuiButton, type ButtonProps as MuiButtonProps } from '@mui/material';
import type { ReactNode } from 'react';

type ButtonVariant = 'primary' | 'secondary';

interface ButtonProps extends Omit<MuiButtonProps, 'variant'> {
  children: ReactNode;
  fullWidth?: boolean;
  variant?: ButtonVariant;
}

export function Button({ children, fullWidth = true, variant = 'primary', ...props }: ButtonProps) {
  return (
    <MuiButton
      fullWidth={fullWidth}
      variant={variant === 'primary' ? 'contained' : 'outlined'}
      {...props}
    >
      {children}
    </MuiButton>
  );
}

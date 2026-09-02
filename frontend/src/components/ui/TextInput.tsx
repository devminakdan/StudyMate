import { Box, FormLabel, Stack, TextField, type TextFieldProps } from '@mui/material';
import type { ReactNode } from 'react';

interface TextInputProps extends Omit<TextFieldProps, 'id' | 'label'> {
  id?: string;
  label: string;
  auxiliary?: ReactNode;
}

export function TextInput({ auxiliary, id, label, ...props }: TextInputProps) {
  const inputId = id ?? label.toLowerCase().replaceAll(' ', '-');

  return (
    <Stack spacing={0.75}>
      <Stack direction="row" sx={{ alignItems: 'baseline', justifyContent: 'space-between' }}>
        <FormLabel htmlFor={inputId}>{label}</FormLabel>
        {auxiliary && <Box>{auxiliary}</Box>}
      </Stack>
      <TextField fullWidth hiddenLabel id={inputId} size="small" variant="outlined" {...props} />
    </Stack>
  );
}

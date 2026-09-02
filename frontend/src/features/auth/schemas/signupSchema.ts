import { z } from 'zod';

export const signupSchema = z.object({
  username: z
    .string()
    .min(2, 'Username must be at least 2 characters.')
    .max(20, 'Username must be 20 characters or fewer.')
    .refine((value) => value.trim().length > 0, 'Username is required.'),
  email: z
    .string()
    .min(1, 'Email is required.')
    .email('Enter a valid email address.'),
  password: z
    .string()
    .refine((value) => value.trim().length > 0, 'Password is required.')
    .min(8, 'Password must be at least 8 characters.')
    .max(30, 'Password must be 30 characters or fewer.'),
});

import { z } from 'zod'

/** An optional Indian mobile number, typed without the +91 the field shows as a prefix. */
export const indianMobile = z
  .string()
  .trim()
  .refine((value) => value === '' || /^[6-9]\d{9}$/.test(value), 'Enter a 10-digit mobile number')

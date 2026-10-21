import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

export function fieldErrors(error: unknown): Record<string, string> {
  const body = error instanceof HttpErrorResponse ? (error.error as ApiError | null) : null;
  const result: Record<string, string> = {};
  for (const fieldError of body?.fieldErrors ?? []) {
    result[fieldError.field] = fieldError.message;
  }
  return result;
}

export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ApiError | null;
    return body?.message || fallback;
  }
  return fallback;
}

import { signal } from '@angular/core';
import { CurrentUser, LeaveRequest } from './core/models';

export const EMPLOYEE_USER: CurrentUser = { id: 3, name: 'Alex Example', email: 'alex.example@example.com', role: 'EMPLOYEE' };
export const MANAGER_USER: CurrentUser = { id: 1, name: 'Morgan Manager', email: 'morgan.manager@example.com', role: 'MANAGER' };

export function leaveRequest(overrides: Partial<LeaveRequest> = {}): LeaveRequest {
  return {
    id: 1,
    employeeId: 3,
    employeeName: 'Alex Example',
    type: 'ANNUAL',
    startDate: '2030-01-10',
    endDate: '2030-01-12',
    days: 3,
    reason: null,
    status: 'PENDING',
    decidedById: null,
    decidedByName: null,
    decisionComment: null,
    createdAt: '2030-01-01T00:00:00Z',
    decidedAt: null,
    ...overrides,
  };
}

export function userSignal(user: CurrentUser | null) {
  return signal<CurrentUser | null>(user);
}

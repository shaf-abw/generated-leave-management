export type Role = 'EMPLOYEE' | 'MANAGER';
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export type LeaveType = 'ANNUAL' | 'SICK' | 'UNPAID' | 'OTHER';

export const LEAVE_TYPES: LeaveType[] = ['ANNUAL', 'SICK', 'UNPAID', 'OTHER'];
export const LEAVE_STATUSES: LeaveStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'];

export interface CurrentUser {
  id: number;
  name: string;
  email: string;
  role: Role;
}

export interface Employee {
  id: number;
  name: string;
  email: string;
  role: Role;
  managerId: number | null;
  managerName: string | null;
  active: boolean;
}

export interface EmployeePayload {
  name: string;
  email: string;
  role: Role;
  managerId: number | null;
  active?: boolean;
}

export interface LeaveRequest {
  id: number;
  employeeId: number;
  employeeName: string;
  type: LeaveType;
  startDate: string;
  endDate: string;
  days: number;
  reason: string | null;
  status: LeaveStatus;
  decidedById: number | null;
  decidedByName: string | null;
  decisionComment: string | null;
  createdAt: string;
  decidedAt: string | null;
}

export interface LeaveRequestPayload {
  type: LeaveType;
  startDate: string;
  endDate: string;
  reason?: string | null;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface HistoryFilter {
  status?: LeaveStatus | null;
  from?: string | null;
  to?: string | null;
  page: number;
  size: number;
}

export interface Dashboard {
  role: Role;
  pendingCount: number;
  approvedCount: number;
  rejectedCount: number;
  approvedDaysThisYear: number;
  pendingApprovals?: number;
  onLeaveToday?: number;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiError {
  status: number;
  error: string;
  message: string;
  fieldErrors: FieldError[];
}

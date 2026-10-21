import { Routes } from '@angular/router';
import { PendingApprovals } from './approvals/pending-approvals';
import { authGuard, managerGuard } from './core/auth.guard';
import { Dashboard } from './dashboard/dashboard';
import { EmployeeForm } from './employees/employee-form';
import { EmployeeList } from './employees/employee-list';
import { Health } from './health/health';
import { History } from './history/history';
import { LeaveRequestForm } from './leave/leave-request-form';
import { MyRequests } from './leave/my-requests';
import { Login } from './login/login';

export const routes: Routes = [
  { path: 'login', component: Login, title: 'Sign in' },
  { path: 'health', component: Health, title: 'Health' },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: Dashboard, title: 'Dashboard' },
      { path: 'leave/new', component: LeaveRequestForm, title: 'Request leave' },
      { path: 'leave/mine', component: MyRequests, title: 'My requests' },
      { path: 'history', component: History, title: 'Leave history' },
      {
        path: 'approvals',
        component: PendingApprovals,
        canActivate: [managerGuard],
        title: 'Pending approvals',
      },
      {
        path: 'employees',
        canActivate: [managerGuard],
        children: [
          { path: '', component: EmployeeList, title: 'Employees' },
          { path: 'new', component: EmployeeForm, title: 'New employee' },
          { path: ':id/edit', component: EmployeeForm, title: 'Edit employee' },
        ],
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];

import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/api-error';
import { EmployeeService } from '../core/employee.service';
import { Employee } from '../core/models';

@Component({
  selector: 'app-employee-list',
  imports: [RouterLink],
  template: `
    <div class="header-row">
      <h1>Employees</h1>
      <a routerLink="/employees/new" class="button">New employee</a>
    </div>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    <table>
      <thead>
        <tr><th>Name</th><th>Email</th><th>Role</th><th>Manager</th><th>Status</th><th></th></tr>
      </thead>
      <tbody>
        @for (employee of employees(); track employee.id) {
          <tr [class.inactive]="!employee.active">
            <td>{{ employee.name }}</td>
            <td>{{ employee.email }}</td>
            <td>{{ employee.role }}</td>
            <td>{{ employee.managerName ?? '—' }}</td>
            <td>{{ employee.active ? 'Active' : 'Inactive' }}</td>
            <td class="actions">
              <a [routerLink]="['/employees', employee.id, 'edit']">Edit</a>
              @if (employee.active && employee.id !== auth.currentUser()?.id) {
                <button type="button" class="secondary" (click)="deactivate(employee)">Deactivate</button>
              }
            </td>
          </tr>
        } @empty {
          <tr><td colspan="6">No employees found.</td></tr>
        }
      </tbody>
    </table>
  `,
})
export class EmployeeList implements OnInit {
  readonly auth = inject(AuthService);
  private readonly service = inject(EmployeeService);

  readonly employees = signal<Employee[]>([]);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.service.list().subscribe({
      next: (employees) => this.employees.set(employees),
      error: (err: unknown) => this.error.set(errorMessage(err)),
    });
  }

  deactivate(employee: Employee): void {
    if (!confirm(`Deactivate ${employee.name}?`)) {
      return;
    }
    this.service.deactivate(employee.id).subscribe({
      next: () =>
        this.employees.update((list) =>
          list.map((e) => (e.id === employee.id ? { ...e, active: false } : e)),
        ),
      error: (err: unknown) => this.error.set(errorMessage(err)),
    });
  }
}

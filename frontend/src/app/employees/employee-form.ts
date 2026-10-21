import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage, fieldErrors } from '../core/api-error';
import { EmployeeService } from '../core/employee.service';
import { Employee, EmployeePayload, Role } from '../core/models';

@Component({
  selector: 'app-employee-form',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="card narrow">
      <h1>{{ isEdit() ? 'Edit employee' : 'New employee' }}</h1>
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        <label for="name">Name</label>
        <input id="name" formControlName="name" />
        @if (fieldError('name'); as message) {
          <p class="field-error" data-field="name">{{ message }}</p>
        }

        <label for="email">Email</label>
        <input id="email" type="email" formControlName="email" />
        @if (fieldError('email'); as message) {
          <p class="field-error" data-field="email">{{ message }}</p>
        }

        <label for="role">Role</label>
        <select id="role" formControlName="role">
          <option value="EMPLOYEE">Employee</option>
          <option value="MANAGER">Manager</option>
        </select>
        @if (fieldError('role'); as message) {
          <p class="field-error" data-field="role">{{ message }}</p>
        }

        <label for="managerId">Manager</label>
        <select id="managerId" formControlName="managerId">
          <option [ngValue]="null">No manager</option>
          @for (manager of managers(); track manager.id) {
            <option [ngValue]="manager.id">{{ manager.name }}</option>
          }
        </select>
        @if (fieldError('managerId'); as message) {
          <p class="field-error" data-field="managerId">{{ message }}</p>
        }

        @if (isEdit()) {
          <label class="inline"><input type="checkbox" formControlName="active" /> Active</label>
        }

        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
        <div class="actions">
          <button type="submit" [disabled]="saving()">Save</button>
          <a routerLink="/employees">Cancel</a>
        </div>
      </form>
    </section>
  `,
})
export class EmployeeForm implements OnInit {
  private readonly service = inject(EmployeeService);
  private readonly router = inject(Router);

  /** Bound from the `:id` route parameter when editing. */
  readonly id = input<string>();
  readonly isEdit = computed(() => !!this.id());

  readonly form = inject(FormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    role: ['EMPLOYEE' as Role, [Validators.required]],
    managerId: [null as number | null],
    active: [true],
  });

  readonly employees = signal<Employee[]>([]);
  readonly managers = computed(() =>
    this.employees().filter(
      (e) => e.role === 'MANAGER' && e.active && String(e.id) !== this.id(),
    ),
  );
  readonly serverErrors = signal<Record<string, string>>({});
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);

  ngOnInit(): void {
    this.service.list().subscribe((list) => this.employees.set(list));
    const id = this.id();
    if (id) {
      this.service.get(Number(id)).subscribe({
        next: (employee) => this.form.patchValue(employee),
        error: (err: unknown) => this.error.set(errorMessage(err)),
      });
    }
  }

  fieldError(field: 'name' | 'email' | 'role' | 'managerId'): string | null {
    const server = this.serverErrors()[field];
    if (server) {
      return server;
    }
    const control = this.form.controls[field];
    if (!control.invalid || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.hasError('required')) {
      return 'This field is required';
    }
    if (control.hasError('email')) {
      return 'Enter a valid email address';
    }
    if (control.hasError('maxlength')) {
      return 'Value is too long';
    }
    return 'Invalid value';
  }

  save(): void {
    this.form.markAllAsTouched();
    this.serverErrors.set({});
    this.error.set(null);
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    const payload: EmployeePayload = {
      name: value.name ?? '',
      email: value.email ?? '',
      role: value.role ?? 'EMPLOYEE',
      managerId: value.managerId ?? null,
    };
    const id = this.id();
    if (id) {
      payload.active = value.active ?? true;
    }
    this.saving.set(true);
    const request = id ? this.service.update(Number(id), payload) : this.service.create(payload);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        void this.router.navigateByUrl('/employees');
      },
      error: (err: unknown) => {
        this.saving.set(false);
        const errors = fieldErrors(err);
        this.serverErrors.set(errors);
        if (Object.keys(errors).length === 0) {
          this.error.set(errorMessage(err));
        }
      },
    });
  }
}

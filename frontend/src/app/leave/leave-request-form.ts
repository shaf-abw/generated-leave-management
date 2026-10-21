import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage, fieldErrors } from '../core/api-error';
import { LeaveService } from '../core/leave.service';
import { LEAVE_TYPES, LeaveType } from '../core/models';

export function dateRangeValidator(group: AbstractControl): ValidationErrors | null {
  const start = group.get('startDate')?.value as string | null;
  const end = group.get('endDate')?.value as string | null;
  return start && end && end < start ? { dateRange: true } : null;
}

export function today(): string {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

@Component({
  selector: 'app-leave-request-form',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="card narrow">
      <h1>Request leave</h1>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <label for="type">Type</label>
        <select id="type" formControlName="type">
          @for (type of types; track type) {
            <option [value]="type">{{ type }}</option>
          }
        </select>

        <label for="startDate">Start date</label>
        <input id="startDate" type="date" formControlName="startDate" [min]="minDate" />
        @if (serverErrors()['startDate']; as message) {
          <p class="field-error">{{ message }}</p>
        }

        <label for="endDate">End date</label>
        <input id="endDate" type="date" formControlName="endDate" [min]="form.value.startDate || minDate" />
        @if (form.hasError('dateRange')) {
          <p class="field-error" data-testid="date-range-error">End date must not be before start date.</p>
        }
        @if (serverErrors()['endDate']; as message) {
          <p class="field-error">{{ message }}</p>
        }

        <label for="reason">Reason (optional)</label>
        <textarea id="reason" formControlName="reason" rows="3" maxlength="500"></textarea>

        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
        <div class="actions">
          <button type="submit" [disabled]="form.invalid || submitting()">Submit request</button>
          <a routerLink="/leave/mine">Cancel</a>
        </div>
      </form>
    </section>
  `,
})
export class LeaveRequestForm {
  private readonly leave = inject(LeaveService);
  private readonly router = inject(Router);

  readonly types = LEAVE_TYPES;
  readonly minDate = today();
  readonly form = inject(FormBuilder).nonNullable.group(
    {
      type: ['ANNUAL' as LeaveType, [Validators.required]],
      startDate: ['', [Validators.required]],
      endDate: ['', [Validators.required]],
      reason: ['', [Validators.maxLength(500)]],
    },
    { validators: dateRangeValidator },
  );
  readonly serverErrors = signal<Record<string, string>>({});
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.submitting.set(true);
    this.serverErrors.set({});
    this.error.set(null);
    const value = this.form.getRawValue();
    this.leave.submit({ ...value, reason: value.reason || null }).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigateByUrl('/leave/mine');
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.serverErrors.set(fieldErrors(err));
        this.error.set(errorMessage(err));
      },
    });
  }
}

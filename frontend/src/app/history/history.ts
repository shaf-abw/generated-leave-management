import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/api-error';
import { LeaveService } from '../core/leave.service';
import { LEAVE_STATUSES, LeaveRequest, LeaveStatus, Page } from '../core/models';
import { StatusBadge } from '../core/status-badge';

@Component({
  selector: 'app-history',
  imports: [ReactiveFormsModule, StatusBadge],
  template: `
    <h1>Leave history</h1>
    <form class="filters" [formGroup]="filters" (ngSubmit)="search()">
      <label
        >Status
        <select formControlName="status">
          <option [ngValue]="null">Any</option>
          @for (status of statuses; track status) {
            <option [ngValue]="status">{{ status }}</option>
          }
        </select>
      </label>
      <label>From <input type="date" formControlName="from" /></label>
      <label>To <input type="date" formControlName="to" /></label>
      <button type="submit">Apply</button>
    </form>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    <table>
      <thead>
        <tr>
          @if (auth.isManager()) {
            <th>Employee</th>
          }
          <th>Type</th>
          <th>From</th>
          <th>To</th>
          <th>Days</th>
          <th>Status</th>
          <th>Decided by</th>
          <th>Comment</th>
        </tr>
      </thead>
      <tbody>
        @for (request of page()?.content ?? []; track request.id) {
          <tr [attr.data-id]="request.id">
            @if (auth.isManager()) {
              <td>{{ request.employeeName }}</td>
            }
            <td>{{ request.type }}</td>
            <td>{{ request.startDate }}</td>
            <td>{{ request.endDate }}</td>
            <td>{{ request.days }}</td>
            <td><app-status-badge [status]="request.status" /></td>
            <td>{{ request.decidedByName ?? '' }}</td>
            <td class="comment">{{ request.decisionComment ?? '' }}</td>
          </tr>
        } @empty {
          <tr>
            <td colspan="8">No requests match the filters.</td>
          </tr>
        }
      </tbody>
    </table>
    @if (page(); as p) {
      @if (p.totalPages > 0) {
        <div class="pager">
          <button
            type="button"
            class="secondary"
            [disabled]="p.page === 0"
            (click)="goTo(p.page - 1)"
          >
            Previous
          </button>
          <span>Page {{ p.page + 1 }} of {{ p.totalPages }}</span>
          <button
            type="button"
            class="secondary"
            [disabled]="p.page + 1 >= p.totalPages"
            (click)="goTo(p.page + 1)"
          >
            Next
          </button>
        </div>
      }
    }
  `,
})
export class History implements OnInit {
  readonly auth = inject(AuthService);
  private readonly leave = inject(LeaveService);

  readonly statuses = LEAVE_STATUSES;
  readonly pageSize = 10;
  readonly filters = inject(FormBuilder).group({
    status: [null as LeaveStatus | null],
    from: [''],
    to: [''],
  });
  readonly page = signal<Page<LeaveRequest> | null>(null);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.goTo(0);
  }

  search(): void {
    this.goTo(0);
  }

  goTo(page: number): void {
    const { status, from, to } = this.filters.getRawValue();
    this.error.set(null);
    this.leave.history({ status, from, to, page, size: this.pageSize }).subscribe({
      next: (result) => this.page.set(result),
      error: (err: unknown) => this.error.set(errorMessage(err)),
    });
  }
}

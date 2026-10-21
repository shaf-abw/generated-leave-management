import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../core/api-error';
import { LeaveService } from '../core/leave.service';
import { LeaveRequest } from '../core/models';
import { StatusBadge } from '../core/status-badge';

@Component({
  selector: 'app-my-requests',
  imports: [RouterLink, StatusBadge],
  template: `
    <div class="header-row">
      <h1>My requests</h1>
      <a routerLink="/leave/new" class="button">Request leave</a>
    </div>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    <table>
      <thead>
        <tr><th>Type</th><th>From</th><th>To</th><th>Days</th><th>Status</th><th>Comment</th><th></th></tr>
      </thead>
      <tbody>
        @for (request of requests(); track request.id) {
          <tr [attr.data-id]="request.id">
            <td>{{ request.type }}</td>
            <td>{{ request.startDate }}</td>
            <td>{{ request.endDate }}</td>
            <td>{{ request.days }}</td>
            <td><app-status-badge [status]="request.status" /></td>
            <td>{{ request.decisionComment ?? '' }}</td>
            <td>
              @if (request.status === 'PENDING') {
                <button type="button" class="secondary" (click)="cancel(request)">Cancel</button>
              }
            </td>
          </tr>
        } @empty {
          <tr><td colspan="7">You have not requested any leave yet.</td></tr>
        }
      </tbody>
    </table>
  `,
})
export class MyRequests implements OnInit {
  private readonly leave = inject(LeaveService);

  readonly requests = signal<LeaveRequest[]>([]);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.leave.mine().subscribe({
      next: (requests) => this.requests.set(requests),
      error: (err: unknown) => this.error.set(errorMessage(err)),
    });
  }

  cancel(request: LeaveRequest): void {
    this.leave.cancel(request.id).subscribe({
      next: (updated) =>
        this.requests.update((list) => list.map((r) => (r.id === updated.id ? updated : r))),
      error: (err: unknown) => this.error.set(errorMessage(err)),
    });
  }
}

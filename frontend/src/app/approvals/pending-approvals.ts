import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../core/api-error';
import { LeaveService } from '../core/leave.service';
import { LeaveRequest } from '../core/models';

@Component({
  selector: 'app-pending-approvals',
  imports: [FormsModule],
  template: `
    <h1>Pending approvals</h1>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    <table>
      <thead>
        <tr><th>Employee</th><th>Type</th><th>From</th><th>To</th><th>Days</th><th>Reason</th><th></th></tr>
      </thead>
      <tbody>
        @for (request of requests(); track request.id) {
          <tr [attr.data-id]="request.id">
            <td>{{ request.employeeName }}</td>
            <td>{{ request.type }}</td>
            <td>{{ request.startDate }}</td>
            <td>{{ request.endDate }}</td>
            <td>{{ request.days }}</td>
            <td>{{ request.reason ?? '' }}</td>
            <td class="actions">
              <button type="button" class="approve" (click)="approve(request)">Approve</button>
              <button type="button" class="secondary reject" (click)="openReject(request)">Reject</button>
            </td>
          </tr>
        } @empty {
          <tr><td colspan="7">No requests are waiting for your decision.</td></tr>
        }
      </tbody>
    </table>

    @if (rejecting(); as request) {
      <div class="dialog-backdrop">
        <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="reject-title">
          <h2 id="reject-title">Reject request from {{ request.employeeName }}</h2>
          <label for="reject-comment">Comment (required)</label>
          <textarea
            id="reject-comment"
            rows="3"
            maxlength="500"
            [(ngModel)]="rejectComment"
          ></textarea>
          <div class="actions">
            <button
              type="button"
              class="confirm-reject"
              [disabled]="!rejectComment.trim()"
              (click)="confirmReject()"
            >
              Reject
            </button>
            <button type="button" class="secondary" (click)="closeReject()">Cancel</button>
          </div>
        </div>
      </div>
    }
  `,
})
export class PendingApprovals implements OnInit {
  private readonly leave = inject(LeaveService);

  readonly requests = signal<LeaveRequest[]>([]);
  readonly error = signal<string | null>(null);
  readonly rejecting = signal<LeaveRequest | null>(null);
  rejectComment = '';

  ngOnInit(): void {
    this.leave.pending().subscribe({
      next: (requests) => this.requests.set(requests),
      error: (err: unknown) => this.error.set(errorMessage(err)),
    });
  }

  approve(request: LeaveRequest): void {
    this.leave.approve(request.id).subscribe({
      next: () => this.remove(request.id),
      error: (err: unknown) => this.error.set(errorMessage(err)),
    });
  }

  openReject(request: LeaveRequest): void {
    this.rejectComment = '';
    this.rejecting.set(request);
  }

  closeReject(): void {
    this.rejecting.set(null);
  }

  confirmReject(): void {
    const request = this.rejecting();
    const comment = this.rejectComment.trim();
    if (!request || !comment) {
      return;
    }
    this.leave.reject(request.id, comment).subscribe({
      next: () => {
        this.remove(request.id);
        this.closeReject();
      },
      error: (err: unknown) => this.error.set(errorMessage(err)),
    });
  }

  private remove(id: number): void {
    this.requests.update((list) => list.filter((r) => r.id !== id));
  }
}

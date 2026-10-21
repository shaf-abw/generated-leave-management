import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/api-error';
import { LeaveService } from '../core/leave.service';
import { Dashboard as DashboardData } from '../core/models';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  template: `
    <h1>Welcome, {{ auth.currentUser()?.name }}</h1>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    @if (data(); as d) {
      <div class="tiles">
        <div class="tile" data-testid="pending"><span class="value">{{ d.pendingCount }}</span>Pending</div>
        <div class="tile" data-testid="approved"><span class="value">{{ d.approvedCount }}</span>Approved</div>
        <div class="tile" data-testid="rejected"><span class="value">{{ d.rejectedCount }}</span>Rejected</div>
        <div class="tile" data-testid="days">
          <span class="value">{{ d.approvedDaysThisYear }}</span>Approved days this year
        </div>
        @if (d.pendingApprovals !== undefined) {
          <a class="tile" routerLink="/approvals" data-testid="pending-approvals">
            <span class="value">{{ d.pendingApprovals }}</span>Pending approvals
          </a>
        }
        @if (d.onLeaveToday !== undefined) {
          <div class="tile" data-testid="on-leave-today">
            <span class="value">{{ d.onLeaveToday }}</span>Team on leave today
          </div>
        }
      </div>
    }
    <p><a routerLink="/leave/new" class="button">Request leave</a></p>
  `,
})
export class Dashboard implements OnInit {
  readonly auth = inject(AuthService);
  private readonly leave = inject(LeaveService);

  readonly data = signal<DashboardData | null>(null);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.leave.dashboard().subscribe({
      next: (data) => this.data.set(data),
      error: (err: unknown) => this.error.set(errorMessage(err)),
    });
  }
}

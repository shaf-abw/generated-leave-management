import { Component, input } from '@angular/core';
import { LeaveStatus } from './models';

@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [class]="'badge badge-' + status().toLowerCase()">{{ status() }}</span>`,
})
export class StatusBadge {
  readonly status = input.required<LeaveStatus>();
}

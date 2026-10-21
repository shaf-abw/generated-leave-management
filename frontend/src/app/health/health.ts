import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';

@Component({
  selector: 'app-health',
  template: `
    <section class="card narrow">
      <h1>Service health</h1>
      <p>
        Backend status:
        <strong data-testid="health-status">{{ status() }}</strong>
      </p>
    </section>
  `,
})
export class Health implements OnInit {
  private readonly http = inject(HttpClient);
  readonly status = signal('CHECKING');

  ngOnInit(): void {
    this.http.get<{ status: string }>('/actuator/health').subscribe({
      next: (body) => this.status.set(body.status),
      error: () => this.status.set('DOWN'),
    });
  }
}

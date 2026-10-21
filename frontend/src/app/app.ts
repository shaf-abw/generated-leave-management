import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    @if (auth.currentUser(); as user) {
      <header class="topbar">
        <strong class="brand">Leave Management</strong>
        <nav>
          <a routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
          <a routerLink="/leave/mine" routerLinkActive="active">My requests</a>
          <a routerLink="/history" routerLinkActive="active">History</a>
          @if (auth.isManager()) {
            <a routerLink="/approvals" routerLinkActive="active" data-testid="nav-approvals">Approvals</a>
            <a routerLink="/employees" routerLinkActive="active" data-testid="nav-employees">Employees</a>
          }
        </nav>
        <span class="user">{{ user.name }} ({{ user.role }})</span>
        <button type="button" class="secondary" (click)="logout()">Sign out</button>
      </header>
    }
    <main>
      <router-outlet />
    </main>
  `,
})
export class App {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/login'));
  }
}

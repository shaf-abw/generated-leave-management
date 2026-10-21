import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  template: `
    <section class="card narrow">
      <h1>Sign in</h1>
      <p class="muted">Demo environment — use one of the synthetic accounts from the README.</p>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <label for="username">Email</label>
        <input id="username" type="email" formControlName="username" autocomplete="username" />
        <label for="password">Password</label>
        <input id="password" type="password" formControlName="password" autocomplete="current-password" />
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
        <button type="submit" [disabled]="form.invalid || submitting()">Sign in</button>
      </form>
    </section>
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login(username, password).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigateByUrl('/dashboard');
      },
      error: () => {
        this.submitting.set(false);
        this.error.set('Invalid email or password.');
      },
    });
  }
}

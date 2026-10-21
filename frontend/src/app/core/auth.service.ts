import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { CurrentUser } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly user = signal<CurrentUser | null>(null);

  readonly currentUser = this.user.asReadonly();
  readonly isAuthenticated = computed(() => this.user() !== null);
  readonly isManager = computed(() => this.user()?.role === 'MANAGER');

  login(username: string, password: string): Observable<CurrentUser> {
    return this.http
      .post<CurrentUser>('/api/auth/login', { username, password })
      .pipe(tap((user) => this.user.set(user)));
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null).pipe(
      catchError(() => of(undefined)),
      tap(() => this.clear()),
    );
  }

  /** Resolves the current user, loading it from the session once if not yet known. */
  ensureUser(): Observable<CurrentUser | null> {
    const known = this.user();
    if (known) {
      return of(known);
    }
    return this.http.get<CurrentUser>('/api/auth/me').pipe(
      tap((user) => this.user.set(user)),
      map((user): CurrentUser | null => user),
      catchError(() => of(null)),
    );
  }

  clear(): void {
    this.user.set(null);
  }
}

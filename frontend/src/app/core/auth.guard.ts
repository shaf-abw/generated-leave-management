import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureUser().pipe(map((user) => (user ? true : router.createUrlTree(['/login']))));
};

export const managerGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureUser().pipe(
    map((user) => {
      if (!user) {
        return router.createUrlTree(['/login']);
      }
      return user.role === 'MANAGER' ? true : router.createUrlTree(['/dashboard']);
    }),
  );
};

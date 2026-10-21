import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { EMPLOYEE_USER, MANAGER_USER } from '../testing';
import { authGuard, managerGuard } from './auth.guard';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('auth', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  function runGuard(guard: typeof authGuard): Promise<boolean | UrlTree> {
    const result = TestBed.runInInjectionContext(() => guard({} as never, {} as never));
    return firstValueFrom(result as Observable<boolean | UrlTree>);
  }

  it('login stores the current user', () => {
    auth.login('alex.example@example.com', 'secret').subscribe();
    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ username: 'alex.example@example.com', password: 'secret' });
    req.flush(EMPLOYEE_USER);
    expect(auth.currentUser()).toEqual(EMPLOYEE_USER);
    expect(auth.isManager()).toBeFalse();
  });

  it('logout clears the user even if the request fails', () => {
    auth.login('x', 'y').subscribe();
    http.expectOne('/api/auth/login').flush(MANAGER_USER);
    auth.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Error' });
    expect(auth.isAuthenticated()).toBeFalse();
  });

  it('authGuard redirects unauthenticated users to /login', async () => {
    const result = runGuard(authGuard);
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    const tree = (await result) as UrlTree;
    expect(router.serializeUrl(tree)).toBe('/login');
  });

  it('authGuard allows users with a valid session', async () => {
    const result = runGuard(authGuard);
    http.expectOne('/api/auth/me').flush(EMPLOYEE_USER);
    expect(await result).toBeTrue();
    expect(await runGuard(authGuard)).toBeTrue();
  });

  it('managerGuard only admits managers', async () => {
    const asEmployee = runGuard(managerGuard);
    http.expectOne('/api/auth/me').flush(EMPLOYEE_USER);
    expect(router.serializeUrl((await asEmployee) as UrlTree)).toBe('/dashboard');

    auth.clear();
    const asManager = runGuard(managerGuard);
    http.expectOne('/api/auth/me').flush(MANAGER_USER);
    expect(await asManager).toBeTrue();

    auth.clear();
    const anonymous = runGuard(managerGuard);
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(router.serializeUrl((await anonymous) as UrlTree)).toBe('/login');
  });

  it('interceptor redirects to /login on 401 from API calls', () => {
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);
    TestBed.inject(HttpClient).get('/api/dashboard').subscribe({ error: () => undefined });
    const req = http.expectOne('/api/dashboard');
    expect(req.request.withCredentials).toBeTrue();
    req.flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('interceptor does not redirect for failed login attempts', () => {
    const navigate = spyOn(router, 'navigate');
    auth.login('x', 'bad').subscribe({ error: () => undefined });
    http.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(navigate).not.toHaveBeenCalled();
  });
});

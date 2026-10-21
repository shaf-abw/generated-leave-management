import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { App } from './app';
import { routes } from './app.routes';
import { AuthService } from './core/auth.service';
import { EMPLOYEE_USER, MANAGER_USER } from './testing';

describe('App shell and routing', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function signIn(user = EMPLOYEE_USER) {
    TestBed.inject(AuthService).login('u', 'p').subscribe();
    http.expectOne('/api/auth/login').flush(user);
  }

  it('hides employee management and approvals navigation for employees', () => {
    signIn(EMPLOYEE_USER);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="nav-employees"]')).toBeNull();
    expect(el.querySelector('[data-testid="nav-approvals"]')).toBeNull();
    expect(el.textContent).toContain('Alex Example');
  });

  it('shows employee management navigation for managers', () => {
    signIn(MANAGER_USER);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-employees"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-approvals"]')).not.toBeNull();
  });

  it('shows no navigation when signed out', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('header')).toBeNull();
  });

  it('dashboard is the default route for an authenticated user', async () => {
    signIn(EMPLOYEE_USER);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    expect(TestBed.inject(Router).url).toBe('/dashboard');
    http.expectOne('/api/dashboard').flush({});
  });

  it('unauthenticated navigation ends on the login page', async () => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/history');
    await new Promise((resolve) => setTimeout(resolve));
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await navigation;
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('employees are redirected away from manager-only routes', async () => {
    signIn(EMPLOYEE_USER);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/approvals');
    expect(TestBed.inject(Router).url).toBe('/dashboard');
    http.expectOne('/api/dashboard').flush({});
  });

  it('sign out returns to the login page', async () => {
    signIn(MANAGER_USER);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    (fixture.nativeElement.querySelector('header button') as HTMLButtonElement).click();
    http.expectOne('/api/auth/logout').flush(null);
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});

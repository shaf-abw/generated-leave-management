import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { EMPLOYEE_USER } from '../testing';
import { Login } from './login';

describe('Login', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('navigates to the dashboard after a successful login', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    const fixture = TestBed.createComponent(Login);
    fixture.componentInstance.form.setValue({ username: 'alex.example@example.com', password: 'pw' });
    fixture.componentInstance.submit();
    http.expectOne('/api/auth/login').flush(EMPLOYEE_USER);
    expect(navigate).toHaveBeenCalledWith('/dashboard');
  });

  it('shows an error for invalid credentials', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.componentInstance.form.setValue({ username: 'alex.example@example.com', password: 'bad' });
    fixture.componentInstance.submit();
    http.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Invalid');
  });

  it('does not submit an empty form', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.componentInstance.submit();
    http.expectNone('/api/auth/login');
    expect(fixture.componentInstance.form.invalid).toBeTrue();
  });
});

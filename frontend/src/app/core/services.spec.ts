import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EmployeeService } from './employee.service';
import { LeaveService } from './leave.service';
import { errorMessage, fieldErrors } from './api-error';
import { HttpErrorResponse } from '@angular/common/http';

describe('API services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('EmployeeService calls the employee endpoints', () => {
    const service = TestBed.inject(EmployeeService);
    const payload = { name: 'A', email: 'a@example.com', role: 'EMPLOYEE' as const, managerId: null };
    service.list().subscribe();
    service.get(2).subscribe();
    service.create(payload).subscribe();
    service.update(2, payload).subscribe();
    service.deactivate(2).subscribe();
    http.expectOne({ method: 'GET', url: '/api/employees' }).flush([]);
    http.expectOne({ method: 'GET', url: '/api/employees/2' }).flush({});
    http.expectOne({ method: 'POST', url: '/api/employees' }).flush({});
    http.expectOne({ method: 'PUT', url: '/api/employees/2' }).flush({});
    const del = http.expectOne({ method: 'DELETE', url: '/api/employees/2' });
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });

  it('LeaveService calls the leave endpoints', () => {
    const service = TestBed.inject(LeaveService);
    service.submit({ type: 'ANNUAL', startDate: '2030-01-01', endDate: '2030-01-02' }).subscribe();
    service.mine().subscribe();
    service.cancel(4).subscribe();
    service.pending().subscribe();
    service.approve(4).subscribe();
    service.reject(4, 'No').subscribe();
    service.dashboard().subscribe();
    service.history({ status: 'APPROVED', from: '2030-01-01', to: '2030-02-01', page: 1, size: 5 }).subscribe();
    http.expectOne({ method: 'POST', url: '/api/leave-requests' }).flush({});
    http.expectOne('/api/leave-requests/mine').flush([]);
    http.expectOne('/api/leave-requests/4/cancel').flush({});
    http.expectOne('/api/leave-requests/pending').flush([]);
    expect(http.expectOne('/api/leave-requests/4/approve').request.body).toEqual({ comment: null });
    expect(http.expectOne('/api/leave-requests/4/reject').request.body).toEqual({ comment: 'No' });
    http.expectOne('/api/dashboard').flush({});
    const history = http.expectOne((r) => r.url === '/api/leave-requests/history');
    expect(history.request.params.toString()).toBe('page=1&size=5&status=APPROVED&from=2030-01-01&to=2030-02-01');
    history.flush({});
  });

  it('extracts API error details', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { message: 'Validation failed', fieldErrors: [{ field: 'email', message: 'Taken' }] },
    });
    expect(fieldErrors(error)).toEqual({ email: 'Taken' });
    expect(errorMessage(error)).toBe('Validation failed');
    expect(errorMessage(new Error('x'), 'fallback')).toBe('fallback');
    expect(fieldErrors(null)).toEqual({});
  });
});

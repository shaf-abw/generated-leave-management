import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render(body: object) {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne('/api/dashboard').flush(body);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders employee counts without manager tiles', () => {
    const el = render({ role: 'EMPLOYEE', pendingCount: 1, approvedCount: 2, rejectedCount: 3, approvedDaysThisYear: 7 });
    expect(el.querySelector('[data-testid="pending"]')?.textContent).toContain('1');
    expect(el.querySelector('[data-testid="days"]')?.textContent).toContain('7');
    expect(el.querySelector('[data-testid="pending-approvals"]')).toBeNull();
    expect(el.querySelector('[data-testid="on-leave-today"]')).toBeNull();
  });

  it('renders manager tiles', () => {
    const el = render({
      role: 'MANAGER', pendingCount: 0, approvedCount: 0, rejectedCount: 0, approvedDaysThisYear: 0,
      pendingApprovals: 4, onLeaveToday: 2,
    });
    expect(el.querySelector('[data-testid="pending-approvals"]')?.textContent).toContain('4');
    expect(el.querySelector('[data-testid="on-leave-today"]')?.textContent).toContain('2');
  });

  it('shows an error when loading fails', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne('/api/dashboard').flush({ message: 'Boom' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Boom');
  });
});

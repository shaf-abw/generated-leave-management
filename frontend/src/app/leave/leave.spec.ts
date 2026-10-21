import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { leaveRequest } from '../testing';
import { LeaveRequestForm } from './leave-request-form';
import { MyRequests } from './my-requests';

describe('Leave requests', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LeaveRequestForm, MyRequests],
      providers: [provideRouter([{ path: 'leave/mine', component: MyRequests }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('LeaveRequestForm', () => {
    it('prevents submission when the end date is before the start date', () => {
      const fixture = TestBed.createComponent(LeaveRequestForm);
      fixture.componentInstance.form.patchValue({ startDate: '2030-05-10', endDate: '2030-05-09' });
      fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      expect(fixture.componentInstance.form.hasError('dateRange')).toBeTrue();
      expect(el.querySelector('[data-testid="date-range-error"]')).not.toBeNull();
      expect((el.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBeTrue();
      fixture.componentInstance.submit();
      http.expectNone('/api/leave-requests');
    });

    it('submits a valid request and shows it as PENDING in My requests', async () => {
      const router = TestBed.inject(Router);
      const form = TestBed.createComponent(LeaveRequestForm);
      form.componentInstance.form.patchValue({ type: 'SICK', startDate: '2030-05-10', endDate: '2030-05-11' });
      form.componentInstance.submit();
      const req = http.expectOne({ method: 'POST', url: '/api/leave-requests' });
      expect(req.request.body).toEqual({ type: 'SICK', startDate: '2030-05-10', endDate: '2030-05-11', reason: null });
      const created = leaveRequest({ id: 42, type: 'SICK', startDate: '2030-05-10', endDate: '2030-05-11', days: 2 });
      req.flush(created);
      await TestBed.inject(Router).navigateByUrl('/leave/mine');
      expect(router.url).toBe('/leave/mine');

      const mine = TestBed.createComponent(MyRequests);
      mine.detectChanges();
      http.expectOne('/api/leave-requests/mine').flush([created]);
      mine.detectChanges();
      const row = mine.nativeElement.querySelector('tr[data-id="42"]') as HTMLElement;
      expect(row.textContent).toContain('PENDING');
      expect(row.textContent).toContain('SICK');
    });

    it('shows server-side errors', () => {
      const fixture = TestBed.createComponent(LeaveRequestForm);
      fixture.componentInstance.form.patchValue({ startDate: '2030-05-10', endDate: '2030-05-11' });
      fixture.componentInstance.submit();
      http.expectOne('/api/leave-requests').flush(
        { message: 'Request overlaps an existing pending or approved request', fieldErrors: [] },
        { status: 409, statusText: 'Conflict' },
      );
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('overlaps');
    });
  });

  describe('MyRequests', () => {
    function render() {
      const fixture = TestBed.createComponent(MyRequests);
      fixture.detectChanges();
      http.expectOne('/api/leave-requests/mine').flush([
        leaveRequest({ id: 1, status: 'PENDING' }),
        leaveRequest({ id: 2, status: 'APPROVED' }),
        leaveRequest({ id: 3, status: 'REJECTED', decisionComment: 'Busy week' }),
        leaveRequest({ id: 4, status: 'CANCELLED' }),
      ]);
      fixture.detectChanges();
      return fixture;
    }

    it('shows the cancel button only for pending requests', () => {
      const el: HTMLElement = render().nativeElement;
      expect(el.querySelector('tr[data-id="1"] button')).not.toBeNull();
      for (const id of [2, 3, 4]) {
        expect(el.querySelector(`tr[data-id="${id}"] button`)).toBeNull();
      }
      expect(el.querySelector('tr[data-id="3"]')?.textContent).toContain('Busy week');
    });

    it('cancels a pending request', () => {
      const fixture = render();
      (fixture.nativeElement.querySelector('tr[data-id="1"] button') as HTMLButtonElement).click();
      http.expectOne({ method: 'POST', url: '/api/leave-requests/1/cancel' }).flush(leaveRequest({ id: 1, status: 'CANCELLED' }));
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('tr[data-id="1"] button')).toBeNull();
      expect(fixture.nativeElement.querySelector('tr[data-id="1"]').textContent).toContain('CANCELLED');
    });
  });
});

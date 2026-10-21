import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../core/auth.service';
import { MANAGER_USER, leaveRequest } from '../testing';
import { History } from './history';

describe('History', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [History],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const page = {
    content: [
      leaveRequest({
        id: 1,
        status: 'APPROVED',
        decidedByName: 'Morgan Manager',
        decisionComment: 'Enjoy',
      }),
      leaveRequest({
        id: 2,
        status: 'REJECTED',
        decidedByName: 'Morgan Manager',
        decisionComment: 'Busy week',
      }),
      leaveRequest({ id: 3, status: 'PENDING' }),
    ],
    page: 0,
    size: 10,
    totalElements: 13,
    totalPages: 2,
  };

  it('renders status badge and decision comment for each row', () => {
    const fixture = TestBed.createComponent(History);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/leave-requests/history').flush(page);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('tr[data-id="1"] .badge-approved')?.textContent).toContain('APPROVED');
    expect(el.querySelector('tr[data-id="1"] .comment')?.textContent).toContain('Enjoy');
    expect(el.querySelector('tr[data-id="2"] .badge-rejected')?.textContent).toContain('REJECTED');
    expect(el.querySelector('tr[data-id="2"] .comment')?.textContent).toContain('Busy week');
    expect(el.querySelector('tr[data-id="3"] .badge-pending')).not.toBeNull();
    expect(el.textContent).toContain('Page 1 of 2');
  });

  it('hides the pager when there are no results', () => {
    const fixture = TestBed.createComponent(History);
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === '/api/leave-requests/history')
      .flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No requests match the filters.');
    expect(el.querySelector('.pager')).toBeNull();
  });

  it('applies filters and paginates', () => {
    TestBed.inject(AuthService).login('u', 'p').subscribe();
    http.expectOne('/api/auth/login').flush(MANAGER_USER);
    const fixture = TestBed.createComponent(History);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/leave-requests/history').flush(page);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('thead').textContent).toContain('Employee');

    fixture.componentInstance.filters.setValue({
      status: 'APPROVED',
      from: '2030-01-01',
      to: '2030-12-31',
    });
    fixture.componentInstance.search();
    const filtered = http.expectOne((r) => r.url === '/api/leave-requests/history');
    expect(filtered.request.params.get('status')).toBe('APPROVED');
    expect(filtered.request.params.get('from')).toBe('2030-01-01');
    expect(filtered.request.params.get('page')).toBe('0');
    filtered.flush(page);

    fixture.componentInstance.goTo(1);
    const next = http.expectOne((r) => r.url === '/api/leave-requests/history');
    expect(next.request.params.get('page')).toBe('1');
    next.flush({ message: 'Bad' }, { status: 400, statusText: 'Bad Request' });
    expect(fixture.componentInstance.error()).toBe('Bad');
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { leaveRequest } from '../testing';
import { PendingApprovals } from './pending-approvals';

describe('PendingApprovals', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PendingApprovals],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render() {
    const fixture = TestBed.createComponent(PendingApprovals);
    fixture.detectChanges();
    http.expectOne('/api/leave-requests/pending').flush([
      leaveRequest({ id: 1, employeeName: 'Alex Example' }),
      leaveRequest({ id: 2, employeeName: 'Sam Sample' }),
    ]);
    fixture.detectChanges();
    return fixture;
  }

  it('removes a request from the list after approval', () => {
    const fixture = render();
    (fixture.nativeElement.querySelector('tr[data-id="1"] button.approve') as HTMLButtonElement).click();
    http.expectOne({ method: 'POST', url: '/api/leave-requests/1/approve' }).flush(leaveRequest({ id: 1, status: 'APPROVED' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('tr[data-id="1"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('tr[data-id="2"]')).not.toBeNull();
  });

  it('requires a comment before reject is enabled, then removes the request', async () => {
    const fixture = render();
    (fixture.nativeElement.querySelector('tr[data-id="2"] button.reject') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    const confirm = () => fixture.nativeElement.querySelector('button.confirm-reject') as HTMLButtonElement;
    expect(confirm().disabled).toBeTrue();

    const textarea = fixture.nativeElement.querySelector('#reject-comment') as HTMLTextAreaElement;
    textarea.value = '   ';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(confirm().disabled).toBeTrue();

    textarea.value = 'Team offsite';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(confirm().disabled).toBeFalse();
    confirm().click();
    const req = http.expectOne({ method: 'POST', url: '/api/leave-requests/2/reject' });
    expect(req.request.body).toEqual({ comment: 'Team offsite' });
    req.flush(leaveRequest({ id: 2, status: 'REJECTED' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('tr[data-id="2"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
  });

  it('keeps the request and shows an error when a decision fails', () => {
    const fixture = render();
    fixture.componentInstance.approve(leaveRequest({ id: 1 }));
    http.expectOne('/api/leave-requests/1/approve').flush({ message: 'Only pending requests can be decided' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('tr[data-id="1"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Only pending');
  });

  it('closing the dialog does not send a request', () => {
    const fixture = render();
    fixture.componentInstance.openReject(leaveRequest({ id: 1 }));
    fixture.componentInstance.confirmReject();
    fixture.componentInstance.closeReject();
    expect(fixture.componentInstance.rejecting()).toBeNull();
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Health } from './health';

describe('Health', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Health],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render(flush: (req: ReturnType<HttpTestingController['expectOne']>) => void) {
    const fixture = TestBed.createComponent(Health);
    fixture.detectChanges();
    flush(http.expectOne('/actuator/health'));
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('[data-testid="health-status"]').textContent as string;
  }

  it('shows UP when the backend is healthy', () => {
    expect(render((req) => req.flush({ status: 'UP' }))).toContain('UP');
  });

  it('shows DOWN when the backend is unreachable', () => {
    expect(render((req) => req.flush(null, { status: 503, statusText: 'Unavailable' }))).toContain('DOWN');
  });
});

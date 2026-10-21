import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Employee } from '../core/models';
import { EmployeeForm } from './employee-form';
import { EmployeeList } from './employee-list';

const EMPLOYEES: Employee[] = [
  { id: 1, name: 'Morgan Manager', email: 'morgan.manager@example.com', role: 'MANAGER', managerId: null, managerName: null, active: true },
  { id: 3, name: 'Alex Example', email: 'alex.example@example.com', role: 'EMPLOYEE', managerId: 1, managerName: 'Morgan Manager', active: true },
  { id: 4, name: 'Old Timer', email: 'old.timer@example.com', role: 'EMPLOYEE', managerId: null, managerName: null, active: false },
];

describe('Employee management', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EmployeeList, EmployeeForm],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('EmployeeList', () => {
    it('lists employees and their status', () => {
      const fixture = TestBed.createComponent(EmployeeList);
      fixture.detectChanges();
      http.expectOne('/api/employees').flush(EMPLOYEES);
      fixture.detectChanges();
      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      expect(rows.length).toBe(3);
      expect(rows[1].textContent).toContain('Alex Example');
      expect(rows[2].textContent).toContain('Inactive');
      expect(rows[2].querySelector('button')).toBeNull();
    });

    it('deactivates an employee after confirmation', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      const fixture = TestBed.createComponent(EmployeeList);
      fixture.detectChanges();
      http.expectOne('/api/employees').flush(EMPLOYEES);
      fixture.detectChanges();
      (fixture.nativeElement.querySelectorAll('tbody tr')[1].querySelector('button') as HTMLButtonElement).click();
      http.expectOne({ method: 'DELETE', url: '/api/employees/3' }).flush(null, { status: 204, statusText: 'No Content' });
      fixture.detectChanges();
      expect(fixture.componentInstance.employees()[1].active).toBeFalse();
      expect(fixture.nativeElement.querySelectorAll('tbody tr')[1].textContent).toContain('Inactive');
    });

    it('does nothing when deactivation is not confirmed', () => {
      spyOn(window, 'confirm').and.returnValue(false);
      const fixture = TestBed.createComponent(EmployeeList);
      fixture.detectChanges();
      http.expectOne('/api/employees').flush(EMPLOYEES);
      fixture.componentInstance.deactivate(EMPLOYEES[1]);
      http.expectNone('/api/employees/3');
      expect(fixture.componentInstance.employees()[1].active).toBeTrue();
    });

    it('shows an error when deactivation fails', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      const fixture = TestBed.createComponent(EmployeeList);
      fixture.detectChanges();
      http.expectOne('/api/employees').flush(EMPLOYEES);
      fixture.componentInstance.deactivate(EMPLOYEES[0]);
      http.expectOne('/api/employees/1').flush({ message: 'You cannot deactivate your own account' }, { status: 409, statusText: 'Conflict' });
      expect(fixture.componentInstance.error()).toContain('own account');
    });
  });

  describe('EmployeeForm', () => {
    function create(id?: string) {
      const fixture = TestBed.createComponent(EmployeeForm);
      if (id) {
        fixture.componentRef.setInput('id', id);
      }
      fixture.detectChanges();
      http.expectOne({ method: 'GET', url: '/api/employees' }).flush(EMPLOYEES);
      return fixture;
    }

    it('blocks submission and shows client-side validation errors', () => {
      const fixture = create();
      fixture.componentInstance.form.patchValue({ name: '', email: 'not-an-email' });
      fixture.componentInstance.save();
      fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-field="name"]')?.textContent).toContain('required');
      expect(el.querySelector('[data-field="email"]')?.textContent).toContain('valid email');
      http.expectNone({ method: 'POST', url: '/api/employees' });
    });

    it('offers only active managers as manager options', () => {
      const fixture = create();
      expect(fixture.componentInstance.managers().map((m) => m.id)).toEqual([1]);
    });

    it('creates an employee and returns to the list', () => {
      const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
      const fixture = create();
      fixture.componentInstance.form.patchValue({ name: 'Casey', email: 'casey@example.com', role: 'EMPLOYEE', managerId: 1 });
      fixture.componentInstance.save();
      const req = http.expectOne({ method: 'POST', url: '/api/employees' });
      expect(req.request.body).toEqual({ name: 'Casey', email: 'casey@example.com', role: 'EMPLOYEE', managerId: 1 });
      req.flush({});
      expect(navigate).toHaveBeenCalledWith('/employees');
    });

    it('displays server-side field errors next to the fields', () => {
      const fixture = create();
      fixture.componentInstance.form.patchValue({ name: 'Casey', email: 'alex.example@example.com' });
      fixture.componentInstance.save();
      http.expectOne({ method: 'POST', url: '/api/employees' }).flush(
        { status: 400, message: 'Validation failed', fieldErrors: [{ field: 'email', message: 'Email is already in use' }] },
        { status: 400, statusText: 'Bad Request' },
      );
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-field="email"]').textContent).toContain('Email is already in use');
      expect(fixture.componentInstance.error()).toBeNull();
    });

    it('shows a general error when the server returns no field errors', () => {
      const fixture = create();
      fixture.componentInstance.form.patchValue({ name: 'Casey', email: 'casey@example.com' });
      fixture.componentInstance.save();
      http.expectOne({ method: 'POST', url: '/api/employees' }).flush({ message: 'Access denied' }, { status: 403, statusText: 'Forbidden' });
      expect(fixture.componentInstance.error()).toBe('Access denied');
    });

    it('loads and updates an existing employee', () => {
      spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
      const fixture = create('3');
      http.expectOne({ method: 'GET', url: '/api/employees/3' }).flush(EMPLOYEES[1]);
      expect(fixture.componentInstance.form.value.name).toBe('Alex Example');
      fixture.componentInstance.form.patchValue({ name: 'Alex Renamed' });
      fixture.componentInstance.save();
      const req = http.expectOne({ method: 'PUT', url: '/api/employees/3' });
      expect(req.request.body.name).toBe('Alex Renamed');
      expect(req.request.body.active).toBeTrue();
      req.flush({});
    });
  });
});

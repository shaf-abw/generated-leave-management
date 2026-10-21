import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Employee, EmployeePayload } from './models';

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private readonly http = inject(HttpClient);

  list(): Observable<Employee[]> {
    return this.http.get<Employee[]>('/api/employees');
  }

  get(id: number): Observable<Employee> {
    return this.http.get<Employee>(`/api/employees/${id}`);
  }

  create(payload: EmployeePayload): Observable<Employee> {
    return this.http.post<Employee>('/api/employees', payload);
  }

  update(id: number, payload: EmployeePayload): Observable<Employee> {
    return this.http.put<Employee>(`/api/employees/${id}`, payload);
  }

  deactivate(id: number): Observable<void> {
    return this.http.delete<void>(`/api/employees/${id}`);
  }
}

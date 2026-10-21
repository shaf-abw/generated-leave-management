import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Dashboard, HistoryFilter, LeaveRequest, LeaveRequestPayload, Page } from './models';

@Injectable({ providedIn: 'root' })
export class LeaveService {
  private readonly http = inject(HttpClient);

  submit(payload: LeaveRequestPayload): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>('/api/leave-requests', payload);
  }

  mine(): Observable<LeaveRequest[]> {
    return this.http.get<LeaveRequest[]>('/api/leave-requests/mine');
  }

  cancel(id: number): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`/api/leave-requests/${id}/cancel`, null);
  }

  pending(): Observable<LeaveRequest[]> {
    return this.http.get<LeaveRequest[]>('/api/leave-requests/pending');
  }

  approve(id: number, comment?: string): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`/api/leave-requests/${id}/approve`, { comment: comment || null });
  }

  reject(id: number, comment: string): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`/api/leave-requests/${id}/reject`, { comment });
  }

  history(filter: HistoryFilter): Observable<Page<LeaveRequest>> {
    let params = new HttpParams().set('page', filter.page).set('size', filter.size);
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.from) {
      params = params.set('from', filter.from);
    }
    if (filter.to) {
      params = params.set('to', filter.to);
    }
    return this.http.get<Page<LeaveRequest>>('/api/leave-requests/history', { params });
  }

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/dashboard');
  }
}

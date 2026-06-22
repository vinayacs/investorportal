import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class TrackingService {
  constructor(private http: HttpClient) {}

  track(page: string): void {
    this.http.post('/api/public/track', { page }).subscribe({ error: () => {} });
  }
}

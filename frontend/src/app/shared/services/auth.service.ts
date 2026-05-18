import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { LoginRequest, LoginResponse, MfaSetupTotp, MfaStatus } from '../models/investor.model';

const API = '/api/auth';

@Injectable({ providedIn: 'root' })
export class AuthService {

  constructor(private http: HttpClient, private router: Router) {}

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API}/login`, request).pipe(
      tap(res => {
        if (!res.mfaPending) {
          localStorage.setItem('token', res.token);
          localStorage.setItem('role', res.role);
          localStorage.setItem('name', `${res.firstName} ${res.lastName}`);
        } else {
          sessionStorage.setItem('mfaToken', res.mfaToken);
          sessionStorage.setItem('mfaType', res.mfaType);
        }
      })
    );
  }

  verifyMfa(mfaToken: string, code: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API}/mfa/verify`, { mfaToken, code }).pipe(
      tap(res => {
        localStorage.setItem('token', res.token);
        localStorage.setItem('role', res.role);
        localStorage.setItem('name', `${res.firstName} ${res.lastName}`);
        sessionStorage.removeItem('mfaToken');
        sessionStorage.removeItem('mfaType');
      })
    );
  }

  selectMfaMethod(mfaToken: string, method: string): Observable<void> {
    return this.http.post<void>(`${API}/mfa/select-method`, { mfaToken, method });
  }

  resendMfaOtp(mfaToken: string): Observable<void> {
    return this.http.post<void>(`${API}/mfa/resend-otp`, { mfaToken });
  }

  forgotPassword(email: string): Observable<any> {
    return this.http.post(`${API}/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<any> {
    return this.http.post(`${API}/reset-password`, { token, newPassword });
  }

  logout(): void {
    localStorage.clear();
    sessionStorage.clear();
    this.router.navigate(['/login']);
  }

  getToken(): string | null { return localStorage.getItem('token'); }
  getRole(): string | null { return localStorage.getItem('role'); }
  getName(): string | null { return localStorage.getItem('name'); }
  isLoggedIn(): boolean { return !!this.getToken(); }
  isAdmin(): boolean { return this.getRole() === 'ADMIN'; }

  // ── Admin MFA management ──────────────────────────────────────────────────

  getAdminMfaStatus(): Observable<MfaStatus> {
    return this.http.get<MfaStatus>('/api/admin/mfa/status');
  }

  setupAdminTotp(): Observable<MfaSetupTotp> {
    return this.http.post<MfaSetupTotp>('/api/admin/mfa/setup/totp', {});
  }

  sendAdminMfaOtp(): Observable<void> {
    return this.http.post<void>('/api/admin/mfa/send-otp', {});
  }

  enableAdminMfa(type: string, code: string, secret?: string): Observable<void> {
    return this.http.post<void>('/api/admin/mfa/enable', { type, code, secret });
  }

  disableAdminMfa(type: string, code: string): Observable<void> {
    return this.http.post<void>('/api/admin/mfa/disable', { type, code });
  }
}

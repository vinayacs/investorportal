import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Investor, Investment, LoginLog, PageVisit, MfaSetupTotp, MfaStatus } from '../models/investor.model';

const API = '/api';

@Injectable({ providedIn: 'root' })
export class InvestorService {

  constructor(private http: HttpClient) {}

  getMyProfile(): Observable<Investor> {
    return this.http.get<Investor>(`${API}/investor/me`);
  }

  updateMyProfile(data: Partial<Investor>): Observable<Investor> {
    return this.http.put<Investor>(`${API}/investor/me`, data);
  }

  // Admin endpoints
  getAllInvestors(): Observable<Investor[]> {
    return this.http.get<Investor[]>(`${API}/admin/investors`);
  }

  getInvestorById(id: number): Observable<Investor> {
    return this.http.get<Investor>(`${API}/admin/investors/${id}`);
  }

  createInvestor(data: any): Observable<Investor> {
    return this.http.post<Investor>(`${API}/admin/investors`, data);
  }

  updateInvestor(id: number, data: Partial<Investor>): Observable<Investor> {
    return this.http.put<Investor>(`${API}/admin/investors/${id}`, data);
  }

  deactivateInvestor(id: number): Observable<any> {
    return this.http.patch(`${API}/admin/investors/${id}/deactivate`, {});
  }

  getLoginLogs(): Observable<LoginLog[]> {
    return this.http.get<LoginLog[]>(`${API}/admin/login-logs`);
  }

  getVisitorLogs(): Observable<PageVisit[]> {
    return this.http.get<PageVisit[]>(`${API}/admin/visitor-logs`);
  }

  getLocationStats(): Observable<any> {
    return this.http.get<any>(`${API}/admin/location-stats`);
  }

  // Investment endpoints (admin)
  getAllInvestments(): Observable<Investment[]> {
    return this.http.get<Investment[]>(`${API}/admin/investments`);
  }

  getInvestmentById(id: number): Observable<Investment> {
    return this.http.get<Investment>(`${API}/admin/investments/${id}`);
  }

  createInvestment(data: any): Observable<Investment> {
    return this.http.post<Investment>(`${API}/admin/investments`, data);
  }

  updateInvestment(id: number, data: any): Observable<Investment> {
    return this.http.put<Investment>(`${API}/admin/investments/${id}`, data);
  }

  deleteInvestment(id: number): Observable<any> {
    return this.http.delete(`${API}/admin/investments/${id}`);
  }

  uploadDocument(investmentId: number, name: string, file: File): Observable<Investment> {
    const formData = new FormData();
    formData.append('name', name);
    formData.append('file', file);
    return this.http.post<Investment>(`${API}/admin/investments/${investmentId}/documents/upload`, formData);
  }

  linkDocument(investmentId: number, name: string, url: string): Observable<Investment> {
    return this.http.post<Investment>(`${API}/admin/investments/${investmentId}/documents/link`, { name, url });
  }

  updateDocument(investmentId: number, documentId: number, data: { name: string }): Observable<Investment> {
    return this.http.patch<Investment>(`${API}/admin/investments/${investmentId}/documents/${documentId}`, data);
  }

  removeDocument(investmentId: number, documentId: number): Observable<any> {
    return this.http.delete(`${API}/admin/investments/${investmentId}/documents/${documentId}`);
  }

  // Investment endpoints (investor)
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API}/investor/me/change-password`, { currentPassword, newPassword });
  }

  getMyInvestments(): Observable<Investment[]> {
    return this.http.get<Investment[]>(`${API}/investor/me/investments`);
  }

  getDocumentPdf(documentId: number): Observable<ArrayBuffer> {
    return this.http.get(`${API}/investor/documents/${documentId}`, { responseType: 'arraybuffer' });
  }

  // ── Investor MFA management ───────────────────────────────────────────────

  getMfaStatus(): Observable<MfaStatus> {
    return this.http.get<MfaStatus>(`${API}/investor/me/mfa/status`);
  }

  setupTotp(): Observable<MfaSetupTotp> {
    return this.http.post<MfaSetupTotp>(`${API}/investor/me/mfa/setup/totp`, {});
  }

  sendMfaOtp(): Observable<void> {
    return this.http.post<void>(`${API}/investor/me/mfa/send-otp`, {});
  }

  enableMfa(type: string, code: string, secret?: string): Observable<void> {
    return this.http.post<void>(`${API}/investor/me/mfa/enable`, { type, code, secret });
  }

  disableMfa(type: string, code: string): Observable<void> {
    return this.http.post<void>(`${API}/investor/me/mfa/disable`, { type, code });
  }
}

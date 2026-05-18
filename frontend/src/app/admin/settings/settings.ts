import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../shared/services/auth.service';
import { MfaSetupTotp, MfaStatus } from '../../shared/models/investor.model';
import * as QRCode from 'qrcode';

@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './settings.html',
  styleUrl: './settings.scss'
})
export class AdminSettingsComponent implements OnInit {
  form = { currentPassword: '', newPassword: '', confirmPassword: '' };
  saved = false;
  error = '';

  // MFA state
  mfaStatus: MfaStatus | null = null;
  mfaView: 'status' | 'enable-email' | 'enable-totp' | 'disable-email' | 'disable-totp' = 'status';
  mfaCode = '';
  mfaError = '';
  mfaSuccess = '';
  mfaOtpSent = false;
  totpSetup: MfaSetupTotp | null = null;
  qrDataUrl = '';

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadMfaStatus();
  }

  changePassword(): void {
    this.error = '';
    this.saved = false;
    if (this.form.newPassword !== this.form.confirmPassword) {
      this.error = 'New passwords do not match.';
      return;
    }
    if (!/^(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]).{8,}$/.test(this.form.newPassword)) {
      this.error = 'Password must be at least 8 characters and contain at least one number and one special character.';
      return;
    }
    this.http.post<void>('/api/admin/change-password', {
      currentPassword: this.form.currentPassword,
      newPassword: this.form.newPassword
    }).subscribe({
      next: () => {
        this.saved = true;
        this.form = { currentPassword: '', newPassword: '', confirmPassword: '' };
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.error = err.error?.message || (err.status === 400 ? 'Current password is incorrect.' : 'Failed to update password. Please try again.');
        this.cdr.detectChanges();
      }
    });
  }

  // ── MFA ───────────────────────────────────────────────────────────────────

  private loadMfaStatus(): void {
    this.authService.getAdminMfaStatus().subscribe({
      next: status => { this.mfaStatus = status; this.cdr.detectChanges(); },
      error: () => {}
    });
  }

  startEmailSetup(): void {
    this.resetMfaState();
    this.mfaView = 'enable-email';
  }

  startTotpSetup(): void {
    this.resetMfaState();
    this.authService.setupAdminTotp().subscribe({
      next: async setup => {
        this.totpSetup = setup;
        this.qrDataUrl = await QRCode.toDataURL(setup.otpauthUrl, { width: 200, margin: 1 });
        this.mfaView = 'enable-totp';
        this.cdr.detectChanges();
      },
      error: () => { this.mfaError = 'Failed to start TOTP setup.'; this.cdr.detectChanges(); }
    });
  }

  startEmailDisable(): void {
    this.resetMfaState();
    this.mfaView = 'disable-email';
  }

  startTotpDisable(): void {
    this.resetMfaState();
    this.mfaView = 'disable-totp';
  }

  cancelMfa(): void {
    this.resetMfaState();
    this.mfaView = 'status';
  }

  sendEmailOtp(): void {
    this.authService.sendAdminMfaOtp().subscribe({
      next: () => { this.mfaOtpSent = true; this.cdr.detectChanges(); },
      error: err => { this.mfaError = err.error?.message || 'Failed to send code.'; this.cdr.detectChanges(); }
    });
  }

  confirmEnable(): void {
    const type = this.mfaView === 'enable-email' ? 'EMAIL' : 'TOTP';
    const secret = type === 'TOTP' ? this.totpSetup?.secret : undefined;
    this.authService.enableAdminMfa(type, this.mfaCode, secret).subscribe({
      next: () => {
        this.mfaSuccess = `${type === 'EMAIL' ? 'Email OTP' : 'Authenticator app'} MFA enabled successfully.`;
        this.loadMfaStatus();
        this.resetMfaState();
        this.mfaView = 'status';
        this.cdr.detectChanges();
      },
      error: err => { this.mfaError = err.error?.message || 'Invalid code.'; this.cdr.detectChanges(); }
    });
  }

  confirmDisable(): void {
    const type = this.mfaView === 'disable-email' ? 'EMAIL' : 'TOTP';
    this.authService.disableAdminMfa(type, this.mfaCode).subscribe({
      next: () => {
        this.mfaSuccess = `${type === 'EMAIL' ? 'Email OTP' : 'Authenticator app'} disabled successfully.`;
        this.loadMfaStatus();
        this.resetMfaState();
        this.mfaView = 'status';
        this.cdr.detectChanges();
      },
      error: err => { this.mfaError = err.error?.message || 'Invalid code.'; this.cdr.detectChanges(); }
    });
  }

  private resetMfaState(): void {
    this.mfaCode = '';
    this.mfaError = '';
    this.mfaSuccess = '';
    this.mfaOtpSent = false;
    this.totpSetup = null;
    this.qrDataUrl = '';
  }

  logout(): void {
    this.authService.logout();
  }
}

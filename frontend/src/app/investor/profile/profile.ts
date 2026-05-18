import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { InvestorService } from '../../shared/services/investor.service';
import { AuthService } from '../../shared/services/auth.service';
import { Investor, MfaSetupTotp, MfaStatus } from '../../shared/models/investor.model';
import * as QRCode from 'qrcode';

type MfaView = 'status' | 'enable-email' | 'enable-totp' | 'disable-email' | 'disable-totp';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './profile.html',
  styleUrl: './profile.scss'
})
export class ProfileComponent implements OnInit {
  investor: Investor | null = null;
  editMode = false;
  saved = false;
  error = '';
  form: Partial<Investor> = {};

  pwForm = { currentPassword: '', newPassword: '', confirmPassword: '' };
  pwSaved = false;
  pwError = '';
  pwMode = false;

  mfaStatus: MfaStatus | null = null;
  mfaView: MfaView = 'status';
  mfaCode = '';
  mfaError = '';
  mfaSuccess = '';
  mfaOtpSent = false;
  totpSetup: MfaSetupTotp | null = null;
  qrDataUrl = '';

  constructor(
    private investorService: InvestorService,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.investorService.getMyProfile().subscribe({
      next: data => { this.investor = data; this.form = { ...data }; this.cdr.detectChanges(); },
      error: err => {
        this.error = err.status === 404 ? 'No investor profile is linked to this account.' : 'Could not load profile.';
        this.cdr.detectChanges();
      }
    });
    this.loadMfaStatus();
  }

  toggleEdit(): void {
    this.editMode = !this.editMode;
    this.saved = false;
    if (this.editMode) this.form = { ...this.investor };
  }

  save(): void {
    this.investorService.updateMyProfile(this.form).subscribe({
      next: updated => { this.investor = updated; this.form = { ...updated }; this.editMode = false; this.saved = true; this.cdr.detectChanges(); },
      error: () => { this.error = 'Failed to save changes.'; this.cdr.detectChanges(); }
    });
  }

  togglePwMode(): void {
    this.pwMode = !this.pwMode;
    this.pwForm = { currentPassword: '', newPassword: '', confirmPassword: '' };
    this.pwSaved = false;
    this.pwError = '';
  }

  changePassword(): void {
    this.pwError = '';
    if (this.pwForm.newPassword !== this.pwForm.confirmPassword) { this.pwError = 'New passwords do not match.'; return; }
    if (!/^(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]).{8,}$/.test(this.pwForm.newPassword)) {
      this.pwError = 'Password must be at least 8 characters and contain at least one number and one special character.';
      return;
    }
    this.investorService.changePassword(this.pwForm.currentPassword, this.pwForm.newPassword).subscribe({
      next: () => { this.pwSaved = true; this.pwMode = false; this.pwForm = { currentPassword: '', newPassword: '', confirmPassword: '' }; this.cdr.detectChanges(); },
      error: err => { this.pwError = err.error?.message || (err.status === 400 ? 'Current password is incorrect.' : 'Failed to change password.'); this.cdr.detectChanges(); }
    });
  }

  // ── MFA ───────────────────────────────────────────────────────────────────

  private loadMfaStatus(): void {
    this.investorService.getMfaStatus().subscribe({
      next: s => { this.mfaStatus = s; this.cdr.detectChanges(); },
      error: () => {}
    });
  }

  startEmailSetup(): void { this.resetMfa(); this.mfaView = 'enable-email'; }

  startTotpSetup(): void {
    this.resetMfa();
    this.investorService.setupTotp().subscribe({
      next: async setup => {
        this.totpSetup = setup;
        this.qrDataUrl = await QRCode.toDataURL(setup.otpauthUrl, { width: 200, margin: 1 });
        this.mfaView = 'enable-totp';
        this.cdr.detectChanges();
      },
      error: () => { this.mfaError = 'Failed to start TOTP setup.'; this.cdr.detectChanges(); }
    });
  }

  startEmailDisable(): void { this.resetMfa(); this.mfaView = 'disable-email'; }
  startTotpDisable(): void  { this.resetMfa(); this.mfaView = 'disable-totp'; }
  cancelMfa(): void         { this.resetMfa(); this.mfaView = 'status'; }

  sendEmailOtp(): void {
    this.investorService.sendMfaOtp().subscribe({
      next: () => { this.mfaOtpSent = true; this.cdr.detectChanges(); },
      error: err => { this.mfaError = err.error?.message || 'Failed to send code.'; this.cdr.detectChanges(); }
    });
  }

  confirmEnable(): void {
    const type = this.mfaView === 'enable-email' ? 'EMAIL' : 'TOTP';
    const secret = type === 'TOTP' ? this.totpSetup?.secret : undefined;
    this.investorService.enableMfa(type, this.mfaCode, secret).subscribe({
      next: () => {
        this.mfaSuccess = `${type === 'EMAIL' ? 'Email OTP' : 'Authenticator App'} enabled.`;
        this.loadMfaStatus(); this.resetMfa(); this.mfaView = 'status'; this.cdr.detectChanges();
      },
      error: err => { this.mfaError = err.error?.message || 'Invalid code.'; this.cdr.detectChanges(); }
    });
  }

  confirmDisable(): void {
    const type = this.mfaView === 'disable-email' ? 'EMAIL' : 'TOTP';
    this.investorService.disableMfa(type, this.mfaCode).subscribe({
      next: () => {
        this.mfaSuccess = `${type === 'EMAIL' ? 'Email OTP' : 'Authenticator App'} disabled.`;
        this.loadMfaStatus(); this.resetMfa(); this.mfaView = 'status'; this.cdr.detectChanges();
      },
      error: err => { this.mfaError = err.error?.message || 'Invalid code.'; this.cdr.detectChanges(); }
    });
  }

  private resetMfa(): void {
    this.mfaCode = ''; this.mfaError = ''; this.mfaOtpSent = false;
    this.totpSetup = null; this.qrDataUrl = '';
  }

  logout(): void { this.authService.logout(); }
}

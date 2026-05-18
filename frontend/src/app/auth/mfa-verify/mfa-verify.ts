import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-mfa-verify',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './mfa-verify.html',
  styleUrl: './mfa-verify.scss'
})
export class MfaVerifyComponent implements OnInit {
  mfaToken = '';
  mfaType = '';   // "EMAIL", "TOTP", or "BOTH"
  selectedMethod = ''; // the method chosen after "BOTH" picker
  step: 'choose-method' | 'enter-code' = 'enter-code';

  code = '';
  error = '';
  loading = false;
  resending = false;
  resent = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.mfaToken = sessionStorage.getItem('mfaToken') ?? '';
    this.mfaType  = sessionStorage.getItem('mfaType') ?? '';
    if (!this.mfaToken) { this.router.navigate(['/login']); return; }
    if (this.mfaType === 'BOTH') {
      this.step = 'choose-method';
    } else {
      this.selectedMethod = this.mfaType;
      this.step = 'enter-code';
    }
  }

  selectMethod(method: string): void {
    this.error = '';
    this.loading = true;
    this.authService.selectMfaMethod(this.mfaToken, method).subscribe({
      next: () => {
        this.selectedMethod = method;
        this.step = 'enter-code';
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.loading = false;
        this.error = err.error?.message || 'Failed to select method. Please try again.';
        this.cdr.detectChanges();
      }
    });
  }

  onSubmit(): void {
    this.error = '';
    this.loading = true;
    this.authService.verifyMfa(this.mfaToken, this.code).subscribe({
      next: res => {
        this.loading = false;
        this.router.navigate([res.role === 'ADMIN' ? '/admin' : '/dashboard']);
        this.cdr.detectChanges();
      },
      error: err => {
        this.loading = false;
        this.error = err.error?.message || 'Invalid code. Please try again.';
        this.cdr.detectChanges();
      }
    });
  }

  resendCode(): void {
    this.resending = true;
    this.resent = false;
    this.error = '';
    this.authService.resendMfaOtp(this.mfaToken).subscribe({
      next: () => { this.resending = false; this.resent = true; this.cdr.detectChanges(); },
      error: err => { this.resending = false; this.error = err.error?.message || 'Could not resend code.'; this.cdr.detectChanges(); }
    });
  }

  backToLogin(): void {
    sessionStorage.removeItem('mfaToken');
    sessionStorage.removeItem('mfaType');
    this.router.navigate(['/login']);
  }
}

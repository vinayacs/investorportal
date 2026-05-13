import { ChangeDetectorRef, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.scss'
})
export class ForgotPasswordComponent {
  email = '';
  message = '';
  loading = false;

  constructor(private authService: AuthService, private cdr: ChangeDetectorRef) {}

  onSubmit(): void {
    this.loading = true;
    this.authService.forgotPassword(this.email).subscribe({
      next: () => {
        this.loading = false;
        this.message = 'If that email exists, a reset link has been sent.';
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.message = 'If that email exists, a reset link has been sent.';
        this.cdr.detectChanges();
      }
    });
  }
}

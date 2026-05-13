import { ChangeDetectorRef, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './settings.html',
  styleUrl: './settings.scss'
})
export class AdminSettingsComponent {
  form = { currentPassword: '', newPassword: '', confirmPassword: '' };
  saved = false;
  error = '';

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  changePassword(): void {
    this.error = '';
    this.saved = false;
    if (this.form.newPassword !== this.form.confirmPassword) {
      this.error = 'New passwords do not match.';
      return;
    }
    if (this.form.newPassword.length < 8) {
      this.error = 'New password must be at least 8 characters.';
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
        this.error = err.status === 400 ? 'Current password is incorrect.' : 'Failed to update password. Please try again.';
        this.cdr.detectChanges();
      }
    });
  }

  logout(): void {
    this.authService.logout();
  }
}

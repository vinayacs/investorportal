import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { InvestorService } from '../../shared/services/investor.service';
import { AuthService } from '../../shared/services/auth.service';
import { Investor } from '../../shared/models/investor.model';

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

  constructor(
    private investorService: InvestorService,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.investorService.getMyProfile().subscribe({
      next: data => {
        this.investor = data;
        this.form = { ...data };
        this.cdr.detectChanges();
      },
      error: err => {
        this.error = err.status === 404
          ? 'No investor profile is linked to this account.'
          : 'Could not load profile. Please try again.';
        this.cdr.detectChanges();
      }
    });
  }

  toggleEdit(): void {
    this.editMode = !this.editMode;
    this.saved = false;
    if (this.editMode) this.form = { ...this.investor };
  }

  save(): void {
    this.investorService.updateMyProfile(this.form).subscribe({
      next: updated => {
        this.investor = updated;
        this.form = { ...updated };
        this.editMode = false;
        this.saved = true;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = 'Failed to save changes. Please try again.';
        this.cdr.detectChanges();
      }
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
    if (this.pwForm.newPassword !== this.pwForm.confirmPassword) {
      this.pwError = 'New passwords do not match.';
      return;
    }
    if (this.pwForm.newPassword.length < 8) {
      this.pwError = 'New password must be at least 8 characters.';
      return;
    }
    this.investorService.changePassword(this.pwForm.currentPassword, this.pwForm.newPassword).subscribe({
      next: () => {
        this.pwSaved = true;
        this.pwMode = false;
        this.pwForm = { currentPassword: '', newPassword: '', confirmPassword: '' };
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.pwError = err.status === 400 ? 'Current password is incorrect.' : 'Failed to change password. Please try again.';
        this.cdr.detectChanges();
      }
    });
  }

  logout(): void {
    this.authService.logout();
  }
}

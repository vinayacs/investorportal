import { ChangeDetectorRef, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class LoginComponent {
  username = '';
  password = '';
  error = '';
  loading = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  onSubmit(): void {
    this.error = '';
    this.loading = true;
    this.authService.login({ username: this.username, password: this.password }).subscribe({
      next: res => {
        this.loading = false;
        this.cdr.detectChanges();
        this.router.navigate([res.role === 'ADMIN' ? '/admin' : '/dashboard']);
      },
      error: () => {
        this.loading = false;
        this.error = 'Invalid credentials. Please try again.';
        this.cdr.detectChanges();
      }
    });
  }
}

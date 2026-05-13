import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { InvestorService } from '../../shared/services/investor.service';
import { AuthService } from '../../shared/services/auth.service';
import { LoginLog } from '../../shared/models/investor.model';

@Component({
  selector: 'app-login-logs',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './login-logs.html',
  styleUrl: './login-logs.scss'
})
export class LoginLogsComponent implements OnInit {
  logs: LoginLog[] = [];

  constructor(
    private investorService: InvestorService,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.investorService.getLoginLogs().subscribe(data => {
      this.logs = data;
      this.cdr.detectChanges();
    });
  }

  logout(): void { this.authService.logout(); }
}

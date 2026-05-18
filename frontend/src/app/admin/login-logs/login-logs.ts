import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { InvestorService } from '../../shared/services/investor.service';
import { AuthService } from '../../shared/services/auth.service';
import { LoginLog } from '../../shared/models/investor.model';

interface UserSummary {
  name: string;
  lastLogin: string;
  successful: number;
  failed: number;
}

@Component({
  selector: 'app-login-logs',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './login-logs.html',
  styleUrl: './login-logs.scss'
})
export class LoginLogsComponent implements OnInit {
  logs: LoginLog[] = [];
  userSummary: UserSummary[] = [];

  constructor(
    private investorService: InvestorService,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.investorService.getLoginLogs().subscribe(data => {
      this.logs = data;
      this.userSummary = this.buildSummary(data);
      this.cdr.detectChanges();
    });
  }

  private buildSummary(logs: LoginLog[]): UserSummary[] {
    const map = new Map<string, UserSummary>();
    for (const log of logs) {
      const name = log.investorName ?? 'Unknown';
      if (!map.has(name)) map.set(name, { name, lastLogin: log.loginTimestamp, successful: 0, failed: 0 });
      const entry = map.get(name)!;
      if (log.status === 'SUCCESS') entry.successful++;
      else if (log.status === 'FAILED') entry.failed++;
      if (new Date(log.loginTimestamp) > new Date(entry.lastLogin)) entry.lastLogin = log.loginTimestamp;
    }
    return Array.from(map.values()).sort((a, b) => new Date(b.lastLogin).getTime() - new Date(a.lastLogin).getTime());
  }

  logout(): void { this.authService.logout(); }
}

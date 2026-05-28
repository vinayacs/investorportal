import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../shared/services/auth.service';

interface Stats {
  total7d: number;
  success7d: number;
  errors7d: number;
  successRate7d: number;
  avgDurationMs7d: number;
}

interface CountySummary {
  county: string;
  total: number;
  successCount: number;
  successRate: number;
  avgDurationMs: number;
}

interface ScraperLog {
  id: number;
  ts: string;
  county: string;
  city: string;
  searchType: string;
  input: string;
  success: boolean;
  durationMs: number;
  propertyId: string | null;
  errorMsg: string | null;
}

interface MonitoringData {
  stats: Stats;
  countySummary: CountySummary[];
  recentLogs: ScraperLog[];
}

@Component({
  selector: 'app-monitoring',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './monitoring.html',
  styleUrl: './monitoring.scss'
})
export class MonitoringComponent implements OnInit {
  data: MonitoringData | null = null;
  loading = true;
  error = '';

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.http.get<MonitoringData>('/api/admin/scraper-monitoring').subscribe({
      next: d => {
        this.data = d;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = 'Failed to load monitoring data.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  formatDuration(ms: number): string {
    if (ms < 1000) return ms + 'ms';
    return (ms / 1000).toFixed(1) + 's';
  }

  formatRate(rate: number): string {
    return (rate * 100).toFixed(1) + '%';
  }

  rateClass(rate: number): string {
    if (rate >= 0.9) return 'good';
    if (rate >= 0.7) return 'warn';
    return 'bad';
  }

  logout(): void { this.authService.logout(); }
}

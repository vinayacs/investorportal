import { ChangeDetectorRef, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../shared/services/auth.service';

interface AppraisalYear {
  year: number;
  landValue: number | null;
  improvementValue: number | null;
  appraisedValue: number | null;
  taxableValue: number | null;
}

interface AnalysisResult {
  supported: boolean;
  county: string;
  address: string;
  propertyId: string | null;
  propertyAddress: string | null;
  cadUrl: string;
  years: AppraisalYear[];
  message?: string;
}

@Component({
  selector: 'app-property-analysis',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './property-analysis.html',
  styleUrl: './property-analysis.scss'
})
export class PropertyAnalysisComponent {
  address = '';
  loading = false;
  error = '';
  result: AnalysisResult | null = null;

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  analyze(): void {
    if (!this.address.trim()) return;
    this.loading = true;
    this.error = '';
    this.result = null;

    this.http.get<AnalysisResult>(`/api/admin/property-analysis`, {
      params: { address: this.address.trim() }
    }).subscribe({
      next: data => {
        this.result = data;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.error = err.error?.detail || err.error?.message || 'Failed to analyze property. Please try again.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  formatCurrency(val: number | null): string {
    if (val == null) return '—';
    return '$' + val.toLocaleString('en-US');
  }

  // Year-over-year change as percentage
  changePercent(current: number | null, previous: number | null): string {
    if (current == null || previous == null || previous === 0) return '';
    const pct = ((current - previous) / previous) * 100;
    const sign = pct >= 0 ? '+' : '';
    return `${sign}${pct.toFixed(1)}%`;
  }

  changeClass(current: number | null, previous: number | null): string {
    if (current == null || previous == null) return '';
    return current >= previous ? 'up' : 'down';
  }

  logout(): void { this.authService.logout(); }
}

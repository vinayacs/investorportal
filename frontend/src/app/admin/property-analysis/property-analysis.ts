import { ChangeDetectorRef, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../shared/services/auth.service';
import {
  Chart, LineController, LineElement, PointElement,
  LinearScale, CategoryScale, Legend, Tooltip, Filler,
  TooltipItem
} from 'chart.js';

Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Legend, Tooltip, Filler);

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
  city: string;
  address: string;
  propertyId: string | null;
  propertyAddress: string | null;
  cadUrl: string;
  ownerName: string | null;
  mailingAddress: string | null;
  years: AppraisalYear[];
  message?: string;
}

const COUNTY_PORTALS: Record<string, string> = {
  'Harris':      'https://hcad.org',
  'Dallas':      'https://www.dallascad.org',
  'Tarrant':     'https://www.tad.org',
  'Denton':      'https://www.dentoncad.com',
  'Collin':      'https://www.collincad.org',
  'Fort Bend':   'https://www.fbcad.org',
  'Montgomery':  'https://mcad-tx.org',
  'Williamson':  'https://www.wcad.org',
  'Bexar':       'https://www.bcad.org',
  'Brazoria':    'https://brazoriacad.org',
  'Galveston':   'https://www.galvestoncad.org',
  'McLennan':    'https://www.mclennancad.org',
  'Lubbock':     'https://www.lubbockcad.org',
  'Jefferson':   'https://www.jcad.org',
  'El Paso':     'https://www.epcad.org',
};

const SUPPORTED_COUNTIES = [
  'Harris', 'Dallas', 'Tarrant', 'Denton', 'Collin',
  'Fort Bend', 'Montgomery', 'Williamson', 'Bexar',
  'Brazoria', 'Galveston', 'McLennan', 'Lubbock', 'Jefferson', 'El Paso',
];

@Component({
  selector: 'app-property-analysis',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './property-analysis.html',
  styleUrl: './property-analysis.scss'
})
export class PropertyAnalysisComponent implements OnDestroy {
  @ViewChild('chartCanvas') chartCanvas?: ElementRef<HTMLCanvasElement>;

  searchType: 'address' | 'propertyId' = 'address';
  address = '';
  propertyId = '';
  selectedCounty = '';
  counties = SUPPORTED_COUNTIES;
  loading = false;
  error = '';
  result: AnalysisResult | null = null;

  private chart: Chart | null = null;

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnDestroy(): void {
    this.destroyChart();
  }

  onSearchTypeChange(): void {
    this.address = '';
    this.propertyId = '';
    this.selectedCounty = '';
    this.result = null;
    this.error = '';
    this.destroyChart();
  }

  get canAnalyze(): boolean {
    if (this.searchType === 'address') return !!this.address.trim();
    return !!this.propertyId.trim() && !!this.selectedCounty;
  }

  analyze(): void {
    if (!this.canAnalyze) return;
    this.loading = true;
    this.error = '';
    this.result = null;
    this.destroyChart();

    const params: Record<string, string> = this.searchType === 'address'
      ? { address: this.address.trim() }
      : { propertyId: this.propertyId.trim(), county: this.selectedCounty };

    this.http.get<AnalysisResult>('/api/admin/property-analysis', { params }).subscribe({
      next: data => {
        this.result = data;
        this.loading = false;
        this.cdr.detectChanges();
        if (data.years.length > 0) {
          setTimeout(() => this.renderChart());
        }
      },
      error: err => {
        this.error = err.error?.detail || err.error?.message || 'Failed to analyze property. Please try again.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  private renderChart(): void {
    if (!this.chartCanvas) return;
    this.destroyChart();

    const sorted = [...(this.result?.years ?? [])].sort((a, b) => a.year - b.year);
    const labels = sorted.map(y => String(y.year));

    const fmt = (v: number | null) => v ?? null;

    this.chart = new Chart(this.chartCanvas.nativeElement, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'Appraised Value',
            data: sorted.map(y => fmt(y.appraisedValue)),
            borderColor: '#003366',
            backgroundColor: 'rgba(0,51,102,0.08)',
            borderWidth: 2.5,
            pointRadius: 4,
            pointHoverRadius: 6,
            tension: 0.3,
            fill: false,
          },
          {
            label: 'Land Value',
            data: sorted.map(y => fmt(y.landValue)),
            borderColor: '#2e7d32',
            backgroundColor: 'rgba(46,125,50,0.07)',
            borderWidth: 2,
            pointRadius: 3,
            pointHoverRadius: 5,
            tension: 0.3,
            fill: false,
          },
          {
            label: 'Improvement Value',
            data: sorted.map(y => fmt(y.improvementValue)),
            borderColor: '#e65100',
            backgroundColor: 'rgba(230,81,0,0.07)',
            borderWidth: 2,
            pointRadius: 3,
            pointHoverRadius: 5,
            tension: 0.3,
            fill: false,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { position: 'top', labels: { boxWidth: 12, font: { size: 12 } } },
          tooltip: {
            callbacks: {
              label: (ctx: TooltipItem<'line'>) => {
                const v = ctx.parsed.y;
                if (v == null) return `${ctx.dataset.label}: —`;
                return `${ctx.dataset.label}: $${v.toLocaleString('en-US')}`;
              }
            }
          }
        },
        scales: {
          x: {
            grid: { color: 'rgba(0,0,0,0.05)' },
            ticks: { font: { size: 12 } }
          },
          y: {
            grid: { color: 'rgba(0,0,0,0.05)' },
            ticks: {
              font: { size: 11 },
              callback: (v: string | number) => '$' + Number(v).toLocaleString('en-US')
            }
          }
        }
      }
    });
  }

  private destroyChart(): void {
    if (this.chart) {
      this.chart.destroy();
      this.chart = null;
    }
  }

  formatCurrency(val: number | null): string {
    if (val == null) return '—';
    return '$' + val.toLocaleString('en-US');
  }

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

  countyPortalUrl(county: string): string {
    return COUNTY_PORTALS[county] ?? '';
  }

  logout(): void { this.authService.logout(); }
}

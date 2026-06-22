import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import {
  Chart, BarController, BarElement, CategoryScale, LinearScale,
  DoughnutController, ArcElement, Tooltip, Legend
} from 'chart.js';
import { InvestorService } from '../../shared/services/investor.service';
import { AuthService } from '../../shared/services/auth.service';

Chart.register(BarController, BarElement, CategoryScale, LinearScale, DoughnutController, ArcElement, Tooltip, Legend);

interface NameCount { name: string; count: number; }

interface LocationStats {
  totalPageVisits: number;
  totalLoginEvents: number;
  uniqueCountries: number;
  uniqueCities: number;
  topCountries: NameCount[];
  topCities: NameCount[];
  pageBreakdown: NameCount[];
}

@Component({
  selector: 'app-location-stats',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './location-stats.html',
  styleUrl: './location-stats.scss'
})
export class LocationStatsComponent implements OnInit, OnDestroy {
  stats: LocationStats | null = null;
  loading = true;
  error = '';

  private countriesChart: Chart | null = null;
  private citiesChart: Chart | null = null;
  private pagesChart: Chart | null = null;

  private readonly BLUE_SHADES = [
    '#106EBE', '#1a7fd4', '#2491ea', '#3da2f5', '#56b3ff',
    '#70c4ff', '#89d5ff', '#a3e6ff', '#bdf7ff', '#d7feff'
  ];

  private readonly PAGE_COLORS = ['#106EBE', '#2e7d32', '#e65100', '#6a1b9a', '#c62828'];

  constructor(
    private investorService: InvestorService,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.investorService.getLocationStats().subscribe({
      next: (data: LocationStats) => {
        this.stats = data;
        this.loading = false;
        this.cdr.detectChanges();
        setTimeout(() => this.renderCharts(), 50);
      },
      error: () => {
        this.error = 'Failed to load location stats.';
        this.loading = false;
      }
    });
  }

  ngAfterViewInit(): void {}

  ngOnDestroy(): void {
    this.countriesChart?.destroy();
    this.citiesChart?.destroy();
    this.pagesChart?.destroy();
  }

  private getCanvas(id: string): HTMLCanvasElement | null {
    return document.getElementById(id) as HTMLCanvasElement | null;
  }

  private renderCharts(): void {
    if (!this.stats) return;
    this.renderCountriesChart();
    this.renderCitiesChart();
    this.renderPagesChart();
  }

  private renderCountriesChart(): void {
    const canvas = this.getCanvas('countriesCanvas');
    if (!canvas || !this.stats?.topCountries.length) return;
    this.countriesChart?.destroy();
    const labels = this.stats.topCountries.map(c => c.name);
    const data = this.stats.topCountries.map(c => c.count);
    this.countriesChart = new Chart(canvas, {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: 'Visits',
          data,
          backgroundColor: this.BLUE_SHADES.slice(0, labels.length),
          borderRadius: 4,
        }]
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        plugins: { legend: { display: false }, tooltip: { callbacks: { label: ctx => ` ${ctx.parsed.x} visits` } } },
        scales: { x: { ticks: { stepSize: 1 } } }
      }
    });
  }

  private renderCitiesChart(): void {
    const canvas = this.getCanvas('citiesCanvas');
    if (!canvas || !this.stats?.topCities.length) return;
    this.citiesChart?.destroy();
    const labels = this.stats.topCities.map(c => c.name);
    const data = this.stats.topCities.map(c => c.count);
    this.citiesChart = new Chart(canvas, {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: 'Visits',
          data,
          backgroundColor: '#2e7d32',
          borderRadius: 4,
        }]
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        plugins: { legend: { display: false }, tooltip: { callbacks: { label: ctx => ` ${ctx.parsed.x} visits` } } },
        scales: { x: { ticks: { stepSize: 1 } } }
      }
    });
  }

  private renderPagesChart(): void {
    const canvas = this.getCanvas('pagesCanvas');
    if (!canvas || !this.stats?.pageBreakdown.length) return;
    this.pagesChart?.destroy();
    const labels = this.stats.pageBreakdown.map(p => p.name);
    const data = this.stats.pageBreakdown.map(p => p.count);
    this.pagesChart = new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels,
        datasets: [{
          data,
          backgroundColor: this.PAGE_COLORS.slice(0, labels.length),
          borderWidth: 2,
        }]
      },
      options: {
        responsive: true,
        plugins: {
          legend: { position: 'bottom' },
          tooltip: { callbacks: { label: ctx => ` ${ctx.label}: ${ctx.parsed} visits` } }
        }
      }
    });
  }

  logout(): void { this.authService.logout(); }
}

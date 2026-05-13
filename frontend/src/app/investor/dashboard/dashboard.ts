import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { InvestorService } from '../../shared/services/investor.service';
import { AuthService } from '../../shared/services/auth.service';
import { Investment } from '../../shared/models/investor.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class DashboardComponent implements OnInit {
  investments: Investment[] = [];
  name = '';

  constructor(
    private investorService: InvestorService,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.name = this.authService.getName() ?? '';
    this.investorService.getMyInvestments().subscribe(data => {
      this.investments = data;
      this.cdr.detectChanges();
    });
  }

  logout(): void {
    this.authService.logout();
  }
}

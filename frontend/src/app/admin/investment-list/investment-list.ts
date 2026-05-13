import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { InvestorService } from '../../shared/services/investor.service';
import { AuthService } from '../../shared/services/auth.service';
import { Investment } from '../../shared/models/investor.model';

@Component({
  selector: 'app-investment-list',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './investment-list.html',
  styleUrl: './investment-list.scss'
})
export class InvestmentListComponent implements OnInit {
  investments: Investment[] = [];

  constructor(
    private investorService: InvestorService,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.investorService.getAllInvestments().subscribe(data => {
      this.investments = data;
      this.cdr.detectChanges();
    });
  }

  delete(id: number): void {
    if (!confirm('Delete this investment? This cannot be undone.')) return;
    this.investorService.deleteInvestment(id).subscribe(() => {
      this.investments = this.investments.filter(i => i.investmentId !== id);
      this.cdr.detectChanges();
    });
  }

  logout(): void { this.authService.logout(); }
}

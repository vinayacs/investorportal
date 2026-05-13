import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { InvestorService } from '../../shared/services/investor.service';
import { AuthService } from '../../shared/services/auth.service';
import { Investor } from '../../shared/models/investor.model';

@Component({
  selector: 'app-investor-list',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './investor-list.html',
  styleUrl: './investor-list.scss'
})
export class InvestorListComponent implements OnInit {
  investors: Investor[] = [];

  constructor(
    private investorService: InvestorService,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.investorService.getAllInvestors().subscribe(data => {
      this.investors = data;
      this.cdr.detectChanges();
    });
  }

  deactivate(id: number): void {
    if (!confirm('Deactivate this investor?')) return;
    this.investorService.deactivateInvestor(id).subscribe(() => {
      this.investors = this.investors.filter(i => i.investorId !== id);
      this.cdr.detectChanges();
    });
  }

  logout(): void { this.authService.logout(); }
}

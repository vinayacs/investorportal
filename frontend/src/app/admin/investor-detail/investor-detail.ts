import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { InvestorService } from '../../shared/services/investor.service';
import { AuthService } from '../../shared/services/auth.service';
import { Investor } from '../../shared/models/investor.model';

@Component({
  selector: 'app-investor-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './investor-detail.html',
  styleUrl: './investor-detail.scss'
})
export class InvestorDetailComponent implements OnInit {
  investor: Investor | null = null;
  isNew = false;
  saved = false;
  form: any = {};

  constructor(
    private route: ActivatedRoute,
    private investorService: InvestorService,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id === 'new') {
      this.isNew = true;
    } else {
      this.investorService.getInvestorById(Number(id)).subscribe(data => {
        this.investor = data;
        this.form = { ...data };
        this.cdr.detectChanges();
      });
    }
  }

  save(): void {
    if (this.isNew) {
      this.investorService.createInvestor(this.form).subscribe(data => {
        this.investor = data;
        this.saved = true;
        this.cdr.detectChanges();
        this.isNew = false;
      });
    } else {
      this.investorService.updateInvestor(this.investor!.investorId, this.form).subscribe(data => {
        this.investor = data;
        this.saved = true;
        this.cdr.detectChanges();
      });
    }
  }

  logout(): void { this.authService.logout(); }
}

import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

interface AmortYear {
  year: number;
  payment: number;
  principal: number;
  interest: number;
  balance: number;
}

interface CalcResult {
  monthlyPI: number;
  monthlyTax: number;
  monthlyInsurance: number;
  monthlyHOA: number;
  monthlyPMI: number;
  pmiApplies: boolean;
  totalMonthly: number;
  loanAmount: number;
  downPayment: number;
  totalInterest: number;
  totalCost: number;
  annualCashFlow: number;
  cashOnCash: number | null;
  breakEvenYears: number | null;
  amortTable: AmortYear[];
}

@Component({
  selector: 'app-mortgage-calculator',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './mortgage-calculator.html',
  styleUrl: './mortgage-calculator.scss'
})
export class MortgageCalculatorComponent implements OnInit {
  homePrice = 500000;
  downPaymentPct = 20;
  downPaymentDollar = 100000;
  downMode: 'pct' | 'dollar' = 'pct';
  interestRate = 7.0;
  loanTermYears = 30;
  propertyTaxRate = 2.0;
  annualInsurance = 1800;
  monthlyHOA = 0;
  pmiRate = 0.6;
  pmiMonthly = 0;       // displayed/used value — auto or overridden
  pmiOverridden = false;
  monthlyRent = 0;

  result: CalcResult | null = null;
  showFullTable = false;

  readonly termOptions = [10, 15, 20, 25, 30];

  ngOnInit(): void {
    this.calculate();
  }

  onDownPctChange(): void {
    this.downPaymentDollar = Math.round(this.homePrice * this.downPaymentPct / 100);
    this.calculate();
  }

  onDownDollarChange(): void {
    this.downPaymentPct = this.homePrice > 0
      ? Math.round((this.downPaymentDollar / this.homePrice) * 1000) / 10
      : 0;
    this.calculate();
  }

  onHomePriceChange(): void {
    if (this.downMode === 'pct') {
      this.downPaymentDollar = Math.round(this.homePrice * this.downPaymentPct / 100);
    } else {
      this.downPaymentPct = this.homePrice > 0
        ? Math.round((this.downPaymentDollar / this.homePrice) * 1000) / 10
        : 0;
    }
    this.calculate();
  }

  onPmiRateChange(): void {
    if (!this.pmiOverridden) this.calculate();
    else this.calculate(); // rate changed — clear override and recompute
    this.pmiOverridden = false;
  }

  onPmiMonthlyChange(): void {
    const dp = this.downMode === 'pct'
      ? this.homePrice * (this.downPaymentPct / 100)
      : this.downPaymentDollar;
    const loan = this.homePrice - dp;
    const pmiApplies = this.homePrice > 0 && (dp / this.homePrice) < 0.20;
    const autoPMI = pmiApplies ? Math.round((loan * this.pmiRate / 100) / 12) : 0;
    this.pmiOverridden = this.pmiMonthly !== autoPMI;
    this.calculate();
  }

  resetPmi(): void {
    this.pmiOverridden = false;
    this.calculate();
  }

  calculate(): void {
    const dp = this.downMode === 'pct'
      ? this.homePrice * (this.downPaymentPct / 100)
      : this.downPaymentDollar;
    const loan = this.homePrice - dp;
    const n = this.loanTermYears * 12;
    const r = this.interestRate / 100 / 12;

    let monthlyPI: number;
    if (r === 0) {
      monthlyPI = loan / n;
    } else {
      monthlyPI = loan * (r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
    }

    const monthlyTax = (this.homePrice * this.propertyTaxRate / 100) / 12;
    const monthlyInsurance = this.annualInsurance / 12;
    const monthlyHOA = this.monthlyHOA;
    // PMI only applies when down payment < 20% of home price.
    const pmiApplies = this.homePrice > 0 && (dp / this.homePrice) < 0.20;
    const autoPMI = pmiApplies ? (loan * this.pmiRate / 100) / 12 : 0;
    if (!this.pmiOverridden) this.pmiMonthly = Math.round(autoPMI);
    const monthlyPMI = pmiApplies ? this.pmiMonthly : 0;
    const totalMonthly = monthlyPI + monthlyTax + monthlyInsurance + monthlyHOA + monthlyPMI;

    const totalInterest = monthlyPI * n - loan;
    const totalCost = dp + monthlyPI * n + monthlyTax * n + monthlyInsurance * n + monthlyHOA * n + monthlyPMI * n;

    // Cash-on-cash: only meaningful if rent is entered
    let annualCashFlow = 0;
    let cashOnCash: number | null = null;
    if (this.monthlyRent > 0) {
      annualCashFlow = (this.monthlyRent - totalMonthly) * 12;
      cashOnCash = dp > 0 ? (annualCashFlow / dp) * 100 : null;
    }

    // Amortization table (yearly)
    const amortTable: AmortYear[] = [];
    let balance = loan;
    for (let y = 1; y <= this.loanTermYears; y++) {
      let yearPrincipal = 0;
      let yearInterest = 0;
      for (let m = 0; m < 12; m++) {
        const interestCharge = balance * r;
        const principalCharge = monthlyPI - interestCharge;
        yearPrincipal += principalCharge;
        yearInterest += interestCharge;
        balance = Math.max(0, balance - principalCharge);
      }
      amortTable.push({
        year: y,
        payment: monthlyPI * 12,
        principal: yearPrincipal,
        interest: yearInterest,
        balance,
      });
    }

    let breakEvenYears: number | null = null;
    if (annualCashFlow < 0) {
      const annualLoss = Math.abs(annualCashFlow);
      let cumulativeLoss = 0;
      let cumulativeEquity = 0;
      for (const row of amortTable) {
        cumulativeLoss += annualLoss;
        cumulativeEquity += row.principal;
        if (cumulativeEquity >= cumulativeLoss) {
          breakEvenYears = row.year;
          break;
        }
      }
    }

    this.result = {
      monthlyPI, monthlyTax, monthlyInsurance, monthlyHOA, monthlyPMI, pmiApplies,
      totalMonthly, loanAmount: loan, downPayment: dp,
      totalInterest, totalCost, annualCashFlow, cashOnCash, breakEvenYears,
      amortTable,
    };
  }

  fmt(val: number): string {
    return '$' + Math.round(val).toLocaleString('en-US');
  }

  fmtDec(val: number, decimals = 1): string {
    return val.toFixed(decimals) + '%';
  }

  get visibleRows(): AmortYear[] {
    if (!this.result) return [];
    return this.showFullTable ? this.result.amortTable : this.result.amortTable.slice(0, 5);
  }
}

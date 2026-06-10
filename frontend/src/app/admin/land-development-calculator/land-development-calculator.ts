import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';
import { Router } from '@angular/router';

const SQFT_PER_ACRE = 43_560;

interface DevResult {
  landSqft: number;
  landCost: number;
  horizCost: number;
  vertCost: number;
  totalCost: number;
  costPerBuiltSqft: number;
  far: number;
  landPct: number;
  horizPct: number;
  vertPct: number;
  grossAnnualRent: number;
  egi: number;
  noi: number;
  grossYield: number;
  netYield: number;
  stabilizedValue: number;
  devProfit: number;
  devMargin: number;
}

@Component({
  selector: 'app-admin-land-development-calculator',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './land-development-calculator.html',
  styleUrl: './land-development-calculator.scss'
})
export class AdminLandDevelopmentCalculatorComponent implements OnInit {
  landAcres = 5;
  landCostMode: 'per_acre' | 'per_sqft' = 'per_sqft';
  landCostValue = 15;

  horizMode: 'total' | 'per_acre' | 'per_built_sqft' = 'per_acre';
  horizValue = 300000;

  vertMode: 'total' | 'per_built_sqft' = 'per_built_sqft';
  vertValue = 300;

  builtSqft = 5 * 7500;
  builtSqftOverridden = false;
  readonly BUILT_SQFT_PER_ACRE = 7500;

  rentPerSqft = 40;
  vacancyRate = 5;
  opExpenseRate = 0;
  exitCapRate = 6.25;

  result: DevResult | null = null;

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit(): void { this.calculate(); }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  onLandAcresChange(): void {
    if (!this.builtSqftOverridden) {
      this.builtSqft = this.landAcres * this.BUILT_SQFT_PER_ACRE;
    }
    this.calculate();
  }

  onBuiltSqftChange(): void {
    const auto = this.landAcres * this.BUILT_SQFT_PER_ACRE;
    this.builtSqftOverridden = this.builtSqft !== auto;
    this.calculate();
  }

  resetBuiltSqft(): void {
    this.builtSqftOverridden = false;
    this.builtSqft = this.landAcres * this.BUILT_SQFT_PER_ACRE;
    this.calculate();
  }

  calculate(): void {
    const landSqft = this.landAcres * SQFT_PER_ACRE;

    const landCost = this.landCostMode === 'per_acre'
      ? this.landAcres * this.landCostValue
      : landSqft * this.landCostValue;

    const horizCost =
      this.horizMode === 'total'       ? this.horizValue :
      this.horizMode === 'per_acre'    ? this.landAcres * this.horizValue :
      /* per_built_sqft */               this.builtSqft * this.horizValue;

    const vertCost = this.vertMode === 'total'
      ? this.vertValue
      : this.builtSqft * this.vertValue;

    const totalCost = landCost + horizCost + vertCost;
    const costPerBuiltSqft = this.builtSqft > 0 ? totalCost / this.builtSqft : 0;
    const far = landSqft > 0 ? this.builtSqft / landSqft : 0;

    const landPct = totalCost > 0 ? (landCost / totalCost) * 100 : 0;
    const horizPct = totalCost > 0 ? (horizCost / totalCost) * 100 : 0;
    const vertPct = totalCost > 0 ? (vertCost / totalCost) * 100 : 0;

    const grossAnnualRent = this.builtSqft * this.rentPerSqft;
    const egi = grossAnnualRent * (1 - this.vacancyRate / 100);
    const noi = egi * (1 - this.opExpenseRate / 100);

    const grossYield = totalCost > 0 ? (grossAnnualRent / totalCost) * 100 : 0;
    const netYield = totalCost > 0 ? (noi / totalCost) * 100 : 0;

    const stabilizedValue = this.exitCapRate > 0 ? noi / (this.exitCapRate / 100) : 0;
    const devProfit = stabilizedValue - totalCost;
    const devMargin = stabilizedValue > 0 ? (devProfit / stabilizedValue) * 100 : 0;

    this.result = {
      landSqft, landCost, horizCost, vertCost,
      totalCost, costPerBuiltSqft, far,
      landPct, horizPct, vertPct,
      grossAnnualRent, egi, noi,
      grossYield, netYield,
      stabilizedValue, devProfit, devMargin,
    };
  }

  fmt(val: number): string {
    return '$' + Math.round(val).toLocaleString('en-US');
  }

  fmtDec(val: number, d = 2): string {
    return val.toFixed(d);
  }
}

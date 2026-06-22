import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

const SQFT_PER_ACRE = 43_560;
const SQFT_PER_SQYARD = 9;
const SQFT_PER_CENT = 435.6;

type AreaUnit = 'acres' | 'sqft' | 'sqyards' | 'cents';
type PriceUnit = 'per_acre' | 'per_sqft' | 'per_sqyard' | 'per_cent';
type Currency = 'USD' | 'INR';

interface PriceResult {
  totalPrice: number;
  perAcre: number;
  perSqft: number;
  perSqyard: number;
  perCent: number;
  areaSqft: number;
  areaAcres: number;
}

@Component({
  selector: 'app-land-price-calculator',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './land-price-calculator.html',
  styleUrl: './land-price-calculator.scss'
})
export class LandPriceCalculatorComponent implements OnInit {
  currency: Currency = 'USD';

  areaValue: number | null = 1;
  areaUnit: AreaUnit = 'acres';

  priceValue: number | null = 10;
  priceUnit: PriceUnit = 'per_sqft';

  result: PriceResult | null = null;

  get currencySymbol(): string { return this.currency === 'INR' ? '₹' : '$'; }

  ngOnInit(): void { this.calculate(); }

  calculate(): void {
    if (!this.areaValue || !this.priceValue || this.areaValue <= 0 || this.priceValue < 0) {
      this.result = null;
      return;
    }

    const areaSqft = this.toSqft(this.areaValue, this.areaUnit);
    const pricePerSqft = this.toPricePerSqft(this.priceValue, this.priceUnit);
    const totalPrice = areaSqft * pricePerSqft;

    this.result = {
      totalPrice,
      perAcre: pricePerSqft * SQFT_PER_ACRE,
      perSqft: pricePerSqft,
      perSqyard: pricePerSqft * SQFT_PER_SQYARD,
      perCent: pricePerSqft * SQFT_PER_CENT,
      areaSqft,
      areaAcres: areaSqft / SQFT_PER_ACRE,
    };
  }

  reset(): void {
    this.areaValue = null;
    this.priceValue = null;
    this.result = null;
  }

  private toSqft(value: number, unit: AreaUnit): number {
    switch (unit) {
      case 'acres':   return value * SQFT_PER_ACRE;
      case 'sqft':    return value;
      case 'sqyards': return value * SQFT_PER_SQYARD;
      case 'cents':   return value * SQFT_PER_CENT;
    }
  }

  private toPricePerSqft(value: number, unit: PriceUnit): number {
    switch (unit) {
      case 'per_acre':   return value / SQFT_PER_ACRE;
      case 'per_sqft':   return value;
      case 'per_sqyard': return value / SQFT_PER_SQYARD;
      case 'per_cent':   return value / SQFT_PER_CENT;
    }
  }

  fmt(val: number): string {
    const locale = this.currency === 'INR' ? 'en-IN' : 'en-US';
    return this.currencySymbol + val.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  fmtArea(sqft: number): string {
    return sqft.toLocaleString('en-US', { maximumFractionDigits: 2 }) + ' sqft';
  }
}

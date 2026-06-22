import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LandPriceCalculatorComponent } from '../../shared/components/land-price-calculator/land-price-calculator';

@Component({
  selector: 'app-public-land-price-calculator',
  standalone: true,
  imports: [RouterLink, LandPriceCalculatorComponent],
  templateUrl: './land-price-calculator.html',
  styleUrl: './land-price-calculator.scss'
})
export class PublicLandPriceCalculatorComponent {}

import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';
import { LandPriceCalculatorComponent } from '../../shared/components/land-price-calculator/land-price-calculator';

@Component({
  selector: 'app-admin-land-price-calculator',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, LandPriceCalculatorComponent],
  templateUrl: './land-price-calculator.html',
  styleUrl: './land-price-calculator.scss'
})
export class AdminLandPriceCalculatorComponent {
  constructor(private authService: AuthService, private router: Router) {}

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}

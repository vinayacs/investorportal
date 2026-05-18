import { Routes } from '@angular/router';
import { authGuard, adminGuard } from './shared/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./auth/login/login').then(m => m.LoginComponent) },
  { path: 'forgot-password', loadComponent: () => import('./auth/forgot-password/forgot-password').then(m => m.ForgotPasswordComponent) },
  { path: 'reset-password', loadComponent: () => import('./auth/reset-password/reset-password').then(m => m.ResetPasswordComponent) },
  { path: 'mfa-verify', loadComponent: () => import('./auth/mfa-verify/mfa-verify').then(m => m.MfaVerifyComponent) },
  { path: 'dashboard', canActivate: [authGuard], loadComponent: () => import('./investor/dashboard/dashboard').then(m => m.DashboardComponent) },
  { path: 'profile', canActivate: [authGuard], loadComponent: () => import('./investor/profile/profile').then(m => m.ProfileComponent) },
  { path: 'admin', canActivate: [adminGuard], loadComponent: () => import('./admin/investor-list/investor-list').then(m => m.InvestorListComponent) },
  { path: 'admin/investors', canActivate: [adminGuard], loadComponent: () => import('./admin/investor-list/investor-list').then(m => m.InvestorListComponent) },
  { path: 'admin/investors/:id', canActivate: [adminGuard], loadComponent: () => import('./admin/investor-detail/investor-detail').then(m => m.InvestorDetailComponent) },
  { path: 'admin/investments', canActivate: [adminGuard], loadComponent: () => import('./admin/investment-list/investment-list').then(m => m.InvestmentListComponent) },
  { path: 'admin/investments/:id', canActivate: [adminGuard], loadComponent: () => import('./admin/investment-detail/investment-detail').then(m => m.InvestmentDetailComponent) },
  { path: 'admin/login-logs', canActivate: [adminGuard], loadComponent: () => import('./admin/login-logs/login-logs').then(m => m.LoginLogsComponent) },
  { path: 'admin/settings', canActivate: [adminGuard], loadComponent: () => import('./admin/settings/settings').then(m => m.AdminSettingsComponent) },
  { path: 'investments', canActivate: [authGuard], loadComponent: () => import('./investor/investments/investments').then(m => m.InvestmentsComponent) },
  { path: '**', redirectTo: 'login' }
];

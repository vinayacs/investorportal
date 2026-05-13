import { test, expect } from '@playwright/test';
import { loginAs, logout, ADMIN_EMAIL, ADMIN_PASSWORD, INVESTOR_EMAIL, INVESTOR_PASSWORD } from './helpers';

test.describe('Login', () => {

  test('shows login page at root', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/login/);
    await expect(page.locator('h1')).toContainText('Investor Portal');
  });

  test('shows error on invalid credentials', async ({ page }) => {
    await page.goto('/login');
    await page.fill('input[name="username"]', 'wrong@email.com');
    await page.fill('input[name="password"]', 'wrongpassword');
    await page.click('button[type="submit"]');
    await expect(page.locator('.error-msg')).toBeVisible();
    await expect(page.locator('.error-msg')).toContainText('Invalid credentials');
  });

  test('admin login redirects to admin page', async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.waitForURL('**/admin**');
    await expect(page.locator('.logo')).toContainText('Admin Portal');
  });

  test('investor login redirects to dashboard', async ({ page }) => {
    await loginAs(page, INVESTOR_EMAIL, INVESTOR_PASSWORD);
    await page.waitForURL('**/dashboard');
    await expect(page.locator('h1')).toContainText('Welcome');
  });

  test('logout clears session and redirects to login', async ({ page }) => {
    await loginAs(page, INVESTOR_EMAIL, INVESTOR_PASSWORD);
    await page.waitForURL('**/dashboard');
    await logout(page);
    await expect(page).toHaveURL(/login/);
    // Verify protected route is inaccessible after logout
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/login/);
  });

  test('forgot password page is accessible', async ({ page }) => {
    await page.goto('/login');
    await page.click('a.forgot-link');
    await expect(page).toHaveURL(/forgot-password/);
    await expect(page.locator('h1')).toContainText('Reset Password');
  });

  test('forgot password shows confirmation message', async ({ page }) => {
    await page.goto('/forgot-password');
    await page.fill('input[name="email"]', 'any@email.com');
    await page.click('button[type="submit"]');
    await expect(page.locator('.success-msg')).toBeVisible();
  });

});

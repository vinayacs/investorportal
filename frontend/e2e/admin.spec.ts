import { test, expect } from '@playwright/test';
import { loginAs, logout, ADMIN_EMAIL, ADMIN_PASSWORD, INVESTOR_EMAIL, INVESTOR_PASSWORD } from './helpers';

test.describe('Admin Portal', () => {

  test.beforeEach(async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.waitForURL('**/admin**');
  });

  test('admin dashboard shows Admin Portal branding', async ({ page }) => {
    await expect(page.locator('.logo')).toContainText('Admin Portal');
    await expect(page.locator('h1')).toBeVisible();
  });

  test('sidebar has investors and login-logs links', async ({ page }) => {
    await expect(page.locator('a[routerlink="/admin/investors"]')).toBeVisible();
    await expect(page.locator('a[routerlink="/admin/login-logs"]')).toBeVisible();
  });

  test('investor list page loads', async ({ page }) => {
    await page.click('a[routerlink="/admin/investors"]');
    await page.waitForURL('**/admin/investors');
    await expect(page.locator('h1')).toContainText('All Investors');
    await expect(page.locator('table')).toBeVisible();
    await expect(page.locator('thead th').first()).toContainText('ID');
  });

  test('investor list shows at least one row', async ({ page }) => {
    await page.goto('/admin/investors');
    await expect(page.locator('tbody tr').first()).toBeVisible();
  });

  test('add investor button navigates to new investor form', async ({ page }) => {
    await page.goto('/admin/investors');
    await page.click('a.add-btn');
    await page.waitForURL('**/admin/investors/new');
    await expect(page.locator('h1')).toContainText('Add Investor');
    await expect(page.locator('input[name="firstName"]')).toBeVisible();
    await expect(page.locator('input[name="email"]')).toBeVisible();
    await expect(page.locator('input[name="password"]')).toBeVisible();
  });

  test('create investor form validates required fields', async ({ page }) => {
    await page.goto('/admin/investors/new');
    await page.click('button[type="submit"]');
    // HTML5 validation should prevent submission
    const firstName = page.locator('input[name="firstName"]');
    await expect(firstName).toBeVisible();
  });

  test('create new investor successfully', async ({ page }) => {
    await page.goto('/admin/investors/new');
    const uniqueEmail = `e2etest_${Date.now()}@test.com`;
    await page.fill('input[name="firstName"]', 'E2E');
    await page.fill('input[name="lastName"]', 'TestUser');
    await page.fill('input[name="email"]', uniqueEmail);
    await page.fill('input[name="password"]', 'TestPass#1234');
    await page.fill('input[name="phone"]', '5555555555');
    await page.click('button[type="submit"]');
    await expect(page.locator('.success-msg')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.success-msg')).toContainText('Investor created');
  });

  test('view investor detail from list', async ({ page }) => {
    await page.goto('/admin/investors');
    await page.locator('a.action-link').first().click();
    await expect(page.getByText(/Edit Investor/)).toBeVisible({ timeout: 5000 });
    await expect(page.locator('input[name="firstName"]')).toBeVisible();
  });

  test('edit investor saves changes', async ({ page }) => {
    await page.goto('/admin/investors');
    await page.locator('a.action-link').first().click();
    await page.waitForURL('**/admin/investors/**');
    await page.fill('input[name="phone"]', '8888888888');
    await page.click('button[type="submit"]');
    await expect(page.locator('.success-msg')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.success-msg')).toContainText('Changes saved');
  });

  test('back to list link works from investor detail', async ({ page }) => {
    await page.goto('/admin/investors');
    await page.locator('a.action-link').first().click();
    await page.waitForURL('**/admin/investors/**');
    await page.click('a.back-link');
    await expect(page).toHaveURL(/admin\/investors$/);
  });

  test('login logs page shows table', async ({ page }) => {
    await page.click('a[routerlink="/admin/login-logs"]');
    await page.waitForURL('**/admin/login-logs');
    await expect(page.locator('table')).toBeVisible();
  });

  test('logout from admin portal redirects to login', async ({ page }) => {
    await logout(page);
    await expect(page).toHaveURL(/login/);
  });

  test('investor cannot access admin routes', async ({ page }) => {
    await logout(page);
    await loginAs(page, INVESTOR_EMAIL, INVESTOR_PASSWORD);
    await page.waitForURL('**/dashboard');
    await page.goto('/admin/investors');
    await expect(page).not.toHaveURL(/admin\/investors/);
  });

});

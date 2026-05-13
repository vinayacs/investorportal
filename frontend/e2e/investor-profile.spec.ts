import { test, expect } from '@playwright/test';
import { loginAs, logout, INVESTOR_EMAIL, INVESTOR_PASSWORD } from './helpers';

test.describe('Investor Profile', () => {

  test.beforeEach(async ({ page }) => {
    await loginAs(page, INVESTOR_EMAIL, INVESTOR_PASSWORD);
    await page.waitForURL('**/dashboard');
  });

  test('dashboard shows investor name and investment info', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('Welcome');
    await expect(page.locator('.sidebar .logo')).toContainText('Investor Portal');
  });

  test('profile page loads with investor data', async ({ page }) => {
    await page.click('a[routerlink="/profile"]');
    await page.waitForURL('**/profile');

    // Should not show loading indefinitely
    await expect(page.locator('.loading')).not.toBeVisible({ timeout: 5000 });
    await expect(page.locator('.error-msg')).not.toBeVisible();

    // Should show investor details
    await expect(page.locator('h2').first()).toContainText('Personal Details');
    await expect(page.locator('.field-grid').first()).toBeVisible();
  });

  test('profile shows correct investor name', async ({ page }) => {
    await page.goto('/profile');
    await expect(page.locator('.loading')).not.toBeVisible({ timeout: 5000 });

    const firstNameField = page.locator('.field').filter({ hasText: 'First Name' }).locator('span').last();
    await expect(firstNameField).not.toBeEmpty();
  });

  test('edit button appears after profile loads', async ({ page }) => {
    await page.goto('/profile');
    await expect(page.locator('.loading')).not.toBeVisible({ timeout: 5000 });
    await expect(page.locator('button.edit-btn')).toBeVisible();
  });

  test('edit mode shows input fields', async ({ page }) => {
    await page.goto('/profile');
    await expect(page.locator('.loading')).not.toBeVisible({ timeout: 5000 });
    await page.click('button.edit-btn');
    await expect(page.locator('input[name="phone"]')).toBeVisible();
    await expect(page.locator('input[name="mailingAddress"]')).toBeVisible();
    await expect(page.locator('button.save-btn')).toBeVisible();
  });

  test('cancel edit restores view mode', async ({ page }) => {
    await page.goto('/profile');
    await expect(page.locator('.loading')).not.toBeVisible({ timeout: 5000 });
    await page.click('button.edit-btn');
    await expect(page.locator('input[name="phone"]')).toBeVisible();
    await page.click('button.edit-btn'); // Cancel
    await expect(page.locator('input[name="phone"]')).not.toBeVisible();
  });

  test('profile update saves and shows success message', async ({ page }) => {
    await page.goto('/profile');
    await expect(page.locator('.loading')).not.toBeVisible({ timeout: 5000 });
    await page.click('button.edit-btn');
    await page.fill('input[name="phone"]', '9999999999');
    await page.click('button.save-btn');
    await expect(page.locator('.success-msg')).toBeVisible();
    await expect(page.locator('.success-msg')).toContainText('updated successfully');
  });

  test('sidebar navigation works', async ({ page }) => {
    await page.goto('/profile');
    await page.click('a[routerlink="/dashboard"]');
    await expect(page).toHaveURL(/dashboard/);
  });

  test('logout from profile page works', async ({ page }) => {
    await page.goto('/profile');
    await logout(page);
    await expect(page).toHaveURL(/login/);
  });

});

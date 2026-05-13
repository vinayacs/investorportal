import { Page } from '@playwright/test';

export const ADMIN_EMAIL = 'vinaycse@gmail.com';
export const ADMIN_PASSWORD = 'TestApp#38899';
export const INVESTOR_EMAIL = 'vinayatp@gmail.com';
export const INVESTOR_PASSWORD = 'TestApp#38899';

export async function loginAs(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.fill('input[name="username"]', email);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');
}

export async function logout(page: Page) {
  await page.click('button.logout-btn');
  await page.waitForURL('**/login');
}

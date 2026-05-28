import { Injectable, inject, NgZone } from '@angular/core';
import { AuthService } from './auth.service';

const INACTIVITY_MS = 300_000;  // 300 seconds
const CHECK_INTERVAL_MS = 5_000; // check every 5 seconds

const ACTIVITY_EVENTS = [
  'mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'click'
];

@Injectable({ providedIn: 'root' })
export class InactivityService {

  private authService = inject(AuthService);
  private zone = inject(NgZone);

  private lastActivity = Date.now();
  private intervalId: ReturnType<typeof setInterval> | null = null;
  private readonly resetFn = () => { this.lastActivity = Date.now(); };

  start(): void {
    this.lastActivity = Date.now();   // reset timer on each new session
    if (this.intervalId !== null) return;

    ACTIVITY_EVENTS.forEach(event =>
      document.addEventListener(event, this.resetFn, { passive: true })
    );

    // Run outside Angular zone — the interval should not trigger change detection
    this.zone.runOutsideAngular(() => {
      this.intervalId = setInterval(() => {
        const idle = Date.now() - this.lastActivity;
        if (idle >= INACTIVITY_MS && this.authService.isLoggedIn()) {
          this.zone.run(() => {
            this.stop();
            this.authService.logout();
          });
        }
      }, CHECK_INTERVAL_MS);
    });
  }

  stop(): void {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
    ACTIVITY_EVENTS.forEach(event =>
      document.removeEventListener(event, this.resetFn)
    );
  }
}

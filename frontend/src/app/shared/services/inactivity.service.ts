import { Injectable, inject, NgZone } from '@angular/core';
import { fromEvent, merge, Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AuthService } from './auth.service';

const INACTIVITY_MS = 60_000; // 60 seconds

const ACTIVITY_EVENTS = [
  'mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'click'
];

@Injectable({ providedIn: 'root' })
export class InactivityService {

  private authService = inject(AuthService);
  private zone = inject(NgZone);
  private subscription: Subscription | null = null;

  start(): void {
    if (this.subscription) return;

    const activity$ = merge(
      ...ACTIVITY_EVENTS.map(event => fromEvent(document, event))
    );

    // Run outside Angular zone so activity events don't trigger
    // unnecessary change detection on every mouse move.
    this.zone.runOutsideAngular(() => {
      this.subscription = activity$.pipe(
        switchMap(() => timer(INACTIVITY_MS))
      ).subscribe(() => {
        if (this.authService.isLoggedIn()) {
          this.zone.run(() => this.authService.logout());
        }
      });

      // Also start the initial timer — log out even if user never moves.
      timer(INACTIVITY_MS).subscribe(() => {
        if (this.authService.isLoggedIn()) {
          this.zone.run(() => this.authService.logout());
        }
      });
    });
  }

  stop(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
  }
}

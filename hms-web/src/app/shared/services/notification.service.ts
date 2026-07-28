import { Injectable, signal } from '@angular/core';
import { ToastConfig, ToastTone } from '../ui/toast/toast.model';

const DEFAULT_TITLES: Record<ToastTone, string> = {
  success: 'Success',
  error: 'Error',
  warning: 'Warning',
  info: 'Information',
  loading: 'Please wait'
};

/** Splits the difference of the approved toast design's "4-5 seconds" guideline - applies to every tone except 'loading', which stays up until the caller dismisses it. */
const DEFAULT_DURATION_MS = 4500;

interface TimerEntry {
  handle: ReturnType<typeof setTimeout>;
  remaining: number;
  startedAt: number;
}

/**
 * Single point of truth for transient user feedback across the whole app -
 * every feature module injects this same service rather than rendering its
 * own inline banners. Backed by a signal-based toast stack (rendered by
 * ToastStackComponent, mounted once at the app root) instead of MatSnackBar,
 * since MatSnackBar only ever shows one notification at a time and the
 * approved design calls for multiple stacked toasts.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly toastsSignal = signal<ToastConfig[]>([]);
  private readonly timers = new Map<string, TimerEntry>();
  private nextId = 0;

  readonly toasts = this.toastsSignal.asReadonly();

  success(message: string, title = DEFAULT_TITLES.success, durationMs = DEFAULT_DURATION_MS): string {
    return this.show('success', title, message, durationMs);
  }

  error(message: string, title = DEFAULT_TITLES.error, durationMs = DEFAULT_DURATION_MS): string {
    return this.show('error', title, message, durationMs);
  }

  warning(message: string, title = DEFAULT_TITLES.warning, durationMs = DEFAULT_DURATION_MS): string {
    return this.show('warning', title, message, durationMs);
  }

  info(message: string, title = DEFAULT_TITLES.info, durationMs = DEFAULT_DURATION_MS): string {
    return this.show('info', title, message, durationMs);
  }

  /** Persistent until dismiss()/updateProgress() - no auto-dismiss timer, since the caller owns the operation's lifecycle. */
  loading(message: string, title = DEFAULT_TITLES.loading): string {
    return this.show('loading', title, message, undefined, 0);
  }

  /** Updates a loading toast's determinate progress bar (0-100). */
  updateProgress(id: string, percent: number): void {
    this.toastsSignal.update((toasts) => toasts.map((toast) => (toast.id === id ? { ...toast, progress: percent } : toast)));
  }

  dismiss(id: string): void {
    this.clearTimer(id);
    this.toastsSignal.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }

  /** Dismisses the most recently added toast - used for the Escape-key shortcut. */
  dismissLatest(): void {
    const toasts = this.toastsSignal();
    const latest = toasts[toasts.length - 1];
    if (latest) {
      this.dismiss(latest.id);
    }
  }

  /** Pauses a toast's auto-dismiss timer (hover-to-pause) - no-op for toasts with no timer (e.g. 'loading'). */
  pause(id: string): void {
    const entry = this.timers.get(id);
    if (!entry) {
      return;
    }
    clearTimeout(entry.handle);
    entry.remaining -= Date.now() - entry.startedAt;
  }

  /** Resumes a paused toast's auto-dismiss timer with whatever time was left. */
  resume(id: string): void {
    const entry = this.timers.get(id);
    if (!entry || entry.remaining <= 0) {
      return;
    }
    entry.startedAt = Date.now();
    entry.handle = setTimeout(() => this.dismiss(id), entry.remaining);
  }

  private show(tone: ToastTone, title: string, message: string, duration?: number, progress?: number): string {
    const id = `toast-${++this.nextId}`;
    this.toastsSignal.update((toasts) => [...toasts, { id, tone, title, message, duration, progress }]);
    if (duration && duration > 0) {
      const handle = setTimeout(() => this.dismiss(id), duration);
      this.timers.set(id, { handle, remaining: duration, startedAt: Date.now() });
    }
    return id;
  }

  private clearTimer(id: string): void {
    const entry = this.timers.get(id);
    if (entry) {
      clearTimeout(entry.handle);
      this.timers.delete(id);
    }
  }
}

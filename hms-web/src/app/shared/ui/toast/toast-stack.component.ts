import { animate, style, transition, trigger } from '@angular/animations';
import { Component, HostListener, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NotificationService } from '../../services/notification.service';
import { ToastTone } from './toast.model';

const TONE_ICONS: Record<ToastTone, string> = {
  success: 'check_circle',
  error: 'cancel',
  warning: 'warning',
  info: 'info',
  loading: ''
};

/**
 * Renders the live NotificationService toast stack - mounted once at the
 * app root (app.component.html) so it works identically on the login page
 * and every authenticated route, not just inside the app shell.
 */
@Component({
  selector: 'app-toast-stack',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule, MatProgressSpinnerModule],
  templateUrl: './toast-stack.component.html',
  styleUrl: './toast-stack.component.scss',
  animations: [
    trigger('toastAnimation', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateX(24px)' }),
        animate('200ms ease-out', style({ opacity: 1, transform: 'translateX(0)' }))
      ]),
      transition(':leave', [animate('160ms ease-in', style({ opacity: 0, transform: 'translateX(24px)' }))])
    ])
  ]
})
export class ToastStackComponent {
  private readonly notification = inject(NotificationService);

  readonly toasts = this.notification.toasts;
  readonly toneIcons = TONE_ICONS;

  @HostListener('window:keydown.escape')
  onEscape(): void {
    this.notification.dismissLatest();
  }

  close(id: string): void {
    this.notification.dismiss(id);
  }

  pause(id: string): void {
    this.notification.pause(id);
  }

  resume(id: string): void {
    this.notification.resume(id);
  }
}

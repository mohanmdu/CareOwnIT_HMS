import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { NotificationService } from '../../shared/services/notification.service';
import { EmptyStateComponent } from '../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import { StatusBadgeComponent, StatusBadgeTone } from '../../shared/ui/status-badge/status-badge.component';
import { BackupService } from './backup.service';
import { ClientBackupRecord, ClientBackupStatus } from './backup.model';

const STATUS_LABELS: Record<ClientBackupStatus, string> = {
  NEVER_RUN: 'Never Run',
  IN_PROGRESS: 'In Progress',
  SUCCESS: 'Success',
  FAILED: 'Failed'
};

const STATUS_TONES: Record<ClientBackupStatus, StatusBadgeTone> = {
  NEVER_RUN: 'neutral',
  IN_PROGRESS: 'info',
  SUCCESS: 'success',
  FAILED: 'danger'
};

/**
 * Super Admin "Database Backup & Recovery" screen - one row per client,
 * mirroring client-list.component.ts's own table/inline-panel patterns
 * (Manage/Add Admin there -> Restore here) rather than a modal dialog, so
 * this stays consistent with the rest of the Super Admin module's UI
 * language instead of introducing a new interaction pattern for one screen.
 */
@Component({
  selector: 'app-backup-list',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusBadgeComponent
  ],
  templateUrl: './backup-list.component.html',
  styleUrl: './backup-list.component.scss'
})
export class BackupListComponent {
  private readonly service = inject(BackupService);
  private readonly notification = inject(NotificationService);

  readonly columns = ['clientName', 'databaseName', 'status', 'lastSuccessAt', 'size', 'age', 'actions'];

  backups = signal<ClientBackupRecord[]>([]);
  loading = signal(false);

  /** Which row's action is currently mid-request - null when none. Scoped per-action so Run Now on one row doesn't disable Download on another. */
  runningClientId = signal<number | null>(null);
  downloadingClientId = signal<number | null>(null);
  restoringClientId = signal<number | null>(null);

  /** Which row has its inline "type the client code to confirm" restore panel open - null when none (see restore()'s own doc comment on why this isn't a plain confirm() dialog). */
  restorePanelClientId = signal<number | null>(null);
  restoreConfirmInput = '';

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (backups) => {
        this.backups.set(backups);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load backup status.');
      }
    });
  }

  statusLabel(status: ClientBackupStatus): string {
    return STATUS_LABELS[status];
  }

  statusTone(status: ClientBackupStatus): StatusBadgeTone {
    return STATUS_TONES[status];
  }

  /** "3 hours ago" / "2 days ago" / "just now" - deliberately hand-rolled rather than a library for something this small, and computed here (not sent from the server) so it never goes stale between polls. */
  ageDisplay(iso: string | null): string {
    if (!iso) {
      return '—';
    }
    const seconds = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
    if (seconds < 60) {
      return 'just now';
    }
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) {
      return `${minutes} minute${minutes === 1 ? '' : 's'} ago`;
    }
    const hours = Math.floor(minutes / 60);
    if (hours < 24) {
      return `${hours} hour${hours === 1 ? '' : 's'} ago`;
    }
    const days = Math.floor(hours / 24);
    return `${days} day${days === 1 ? '' : 's'} ago`;
  }

  sizeDisplay(bytes: number | null): string {
    if (bytes === null) {
      return '—';
    }
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    const kb = bytes / 1024;
    if (kb < 1024) {
      return `${kb.toFixed(1)} KB`;
    }
    const mb = kb / 1024;
    if (mb < 1024) {
      return `${mb.toFixed(1)} MB`;
    }
    return `${(mb / 1024).toFixed(2)} GB`;
  }

  runNow(backup: ClientBackupRecord): void {
    this.runningClientId.set(backup.clientId);
    this.service.runNow(backup.clientId).subscribe({
      next: () => {
        this.runningClientId.set(null);
        this.notification.success(`Backup completed for ${backup.clientName}.`);
        this.refresh();
      },
      error: (err) => {
        this.runningClientId.set(null);
        this.notification.error(err.error?.message ?? `Backup failed for ${backup.clientName}.`);
        this.refresh();
      }
    });
  }

  download(backup: ClientBackupRecord): void {
    this.downloadingClientId.set(backup.clientId);
    this.service.download(backup.clientId).subscribe({
      next: (blob) => {
        this.downloadingClientId.set(null);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${backup.clientCode.toLowerCase()}-backup-${new Date().toISOString().slice(0, 10)}.sql.gz`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.downloadingClientId.set(null);
        this.notification.error(err.error?.message ?? `Failed to download backup for ${backup.clientName}.`);
      }
    });
  }

  openRestorePanel(backup: ClientBackupRecord): void {
    this.restorePanelClientId.set(backup.clientId);
    this.restoreConfirmInput = '';
  }

  cancelRestore(): void {
    this.restorePanelClientId.set(null);
    this.restoreConfirmInput = '';
  }

  isRestoreConfirmValid(backup: ClientBackupRecord): boolean {
    return this.restoreConfirmInput.trim().toUpperCase() === backup.clientCode.toUpperCase();
  }

  /**
   * Requires typing the client's own code, not just clicking a confirm
   * button - see hms-api's RestoreService.restore()'s own doc comment on
   * why a wrong-client accident needs more friction than a dialog with one
   * "Yes" button a mis-click can trigger.
   */
  confirmRestore(backup: ClientBackupRecord): void {
    if (!this.isRestoreConfirmValid(backup)) {
      return;
    }
    this.restoringClientId.set(backup.clientId);
    this.service.restore(backup.clientId, this.restoreConfirmInput.trim()).subscribe({
      next: () => {
        this.restoringClientId.set(null);
        this.restorePanelClientId.set(null);
        this.notification.success(`${backup.clientName}'s database has been restored.`);
        this.refresh();
      },
      error: (err) => {
        this.restoringClientId.set(null);
        this.notification.error(err.error?.message ?? `Restore failed for ${backup.clientName}.`);
      }
    });
  }
}

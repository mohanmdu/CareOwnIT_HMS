import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { EMPTY, Observable, Subject } from 'rxjs';
import { catchError, filter, switchMap } from 'rxjs/operators';
import { NotificationService } from '../../../shared/services/notification.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { StatTileComponent } from '../../../shared/ui/stat-tile/stat-tile.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge/status-badge.component';
import { Consultant } from '../../masters-admin/consultants/consultant.model';
import { ConsultantService } from '../../masters-admin/consultants/consultant.service';
import { DoctorQueueDashboard, DoctorQueueEntry } from '../doctor-queue.model';
import { DoctorQueueService } from '../doctor-queue.service';

function toIsoDate(date: Date | null): string {
  if (!date) {
    return '';
  }
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function formatElapsed(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return minutes > 0 ? `${minutes}m ${seconds}s ago` : `${seconds}s ago`;
}

const POLL_MS = 10000;

/**
 * The live "now serving" screen driving the call/skip/complete workflow.
 * Called -> In Consultation is fully automatic server-side (see
 * DoctorQueueService.complete()) - there is deliberately no "Start
 * Consultation" button here, only the 4 headline actions.
 */
@Component({
  selector: 'app-doctor-queue-dashboard',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    EmptyStateComponent,
    PageHeaderComponent,
    StatTileComponent,
    StatusBadgeComponent
  ],
  templateUrl: './doctor-queue-dashboard.component.html',
  styleUrl: './doctor-queue-dashboard.component.scss'
})
export class DoctorQueueDashboardComponent {
  private readonly service = inject(DoctorQueueService);
  private readonly consultantService = inject(ConsultantService);
  private readonly notification = inject(NotificationService);

  consultants = signal<Consultant[]>([]);
  consultantId = signal<number | null>(null);
  queueDate = signal(new Date());

  dashboard = signal<DoctorQueueDashboard | null>(null);
  loading = signal(false);
  actionInFlight = signal(false);
  now = signal(Date.now());

  readonly canCallNext = computed(
    () => this.dashboard()?.currentCalled == null && (this.dashboard()?.waitingCount ?? 0) > 0
  );
  readonly hasCurrentCalled = computed(() => this.dashboard()?.currentCalled != null);
  readonly elapsedSinceCalled = computed(() => {
    const called = this.dashboard()?.currentCalled?.calledAt;
    return called ? formatElapsed(this.now() - new Date(called).getTime()) : null;
  });

  private readonly refresh$ = new Subject<void>();

  constructor() {
    this.refresh$
      .pipe(
        filter(() => this.consultantId() !== null),
        switchMap(() => {
          this.loading.set(true);
          return this.service.getDashboard(this.consultantId()!, toIsoDate(this.queueDate())).pipe(
            catchError(() => {
              this.notification.error('Failed to load the doctor dashboard.');
              this.loading.set(false);
              return EMPTY;
            })
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe((dashboard) => {
        this.dashboard.set(dashboard);
        this.loading.set(false);
      });

    this.consultantService.list().subscribe({
      next: (consultants) => this.consultants.set(consultants),
      error: () => this.notification.error('Failed to load consultants.')
    });

    const pollHandle = setInterval(() => this.refresh$.next(), POLL_MS);
    const tickHandle = setInterval(() => this.now.set(Date.now()), 1000);
    const destroyRef = inject(DestroyRef);
    destroyRef.onDestroy(() => clearInterval(pollHandle));
    destroyRef.onDestroy(() => clearInterval(tickHandle));
  }

  onFiltersChange(): void {
    this.refresh$.next();
  }

  refresh(): void {
    this.refresh$.next();
  }

  private runAction(action$: Observable<DoctorQueueEntry>, successMessage: string): void {
    this.actionInFlight.set(true);
    action$.subscribe({
      next: () => {
        this.actionInFlight.set(false);
        this.notification.success(successMessage);
        this.refresh();
      },
      error: (err) => {
        this.actionInFlight.set(false);
        this.notification.error(err.error?.message ?? 'Action failed.');
      }
    });
  }

  callNext(): void {
    const consultantId = this.consultantId();
    if (consultantId === null) {
      return;
    }
    this.runAction(this.service.nextPatient(consultantId, toIsoDate(this.queueDate())), 'Next patient called.');
  }

  recall(): void {
    const id = this.dashboard()?.currentCalled?.id;
    if (id == null) {
      return;
    }
    this.runAction(this.service.recall(id), 'Patient re-announced.');
  }

  skip(): void {
    const id = this.dashboard()?.currentCalled?.id;
    if (id == null) {
      return;
    }
    this.runAction(this.service.skip(id), 'Patient skipped.');
  }

  complete(): void {
    const id = this.dashboard()?.currentCalled?.id;
    if (id == null) {
      return;
    }
    this.runAction(this.service.complete(id), 'Consultation completed.');
  }

  escalate(entry: DoctorQueueEntry): void {
    this.runAction(this.service.escalate(entry.id), 'Escalated to emergency.');
  }

  deEscalate(entry: DoctorQueueEntry): void {
    this.runAction(this.service.deEscalate(entry.id), 'De-escalated to normal priority.');
  }
}

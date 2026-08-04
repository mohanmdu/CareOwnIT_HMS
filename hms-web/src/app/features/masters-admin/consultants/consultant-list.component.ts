import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, Observable } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { NotificationService } from '../../../shared/services/notification.service';
import { TablePagination } from '../../../shared/table/table-pagination';
import { TableSearchComponent } from '../../../shared/table/table-search.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { Department } from '../departments/department.model';
import { DepartmentService } from '../departments/department.service';
import { Specialization } from '../specializations/specialization.model';
import { SpecializationService } from '../specializations/specialization.service';
import { ConsultantFormDialogComponent, ConsultantFormDialogResult } from './consultant-form-dialog.component';
import { ConsultantTimingsDialogComponent } from './consultant-timings-dialog.component';
import { Consultant } from './consultant.model';
import { ConsultantService } from './consultant.service';

/**
 * Consultants: one screen, two tabs (Active / De-Activated), mirroring
 * Departments/Patient Registration. Add/Edit both use ConsultantFormDialogComponent;
 * deactivate is soft (moves a consultant to the De-Activated tab, restorable).
 */
@Component({
  selector: 'app-consultant-list',
  standalone: true,
  imports: [
    DecimalPipe,
    DatePipe,
    MatTabsModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressBarModule,
    MatPaginatorModule,
    PageHeaderComponent,
    EmptyStateComponent,
    TableSearchComponent
  ],
  templateUrl: './consultant-list.component.html',
  styleUrl: './consultant-list.component.scss'
})
export class ConsultantListComponent {
  private readonly service = inject(ConsultantService);
  private readonly departmentService = inject(DepartmentService);
  private readonly specializationService = inject(SpecializationService);
  private readonly notification = inject(NotificationService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly activeColumns = ['name', 'department', 'fee', 'website', 'timings', 'edit', 'deactivate', 'createdBy', 'updatedBy'];
  readonly inactiveColumns = ['name', 'department', 'deactivatedAt', 'deactivatedBy', 'actions'];

  activeConsultants = signal<Consultant[]>([]);
  inactiveConsultants = signal<Consultant[]>([]);
  departments = signal<Department[]>([]);
  specializations = signal<Specialization[]>([]);
  loadingActive = signal(false);
  loadingInactive = signal(false);

  /** Set when arriving from the "View consultants" link on a department row. */
  filterDepartmentId = signal<number | null>(null);
  searchTerm = signal('');

  filteredActiveConsultants = computed(() => this.filterBySearch(this.filterByDepartment(this.activeConsultants())));
  filteredInactiveConsultants = computed(() =>
    this.filterBySearch(this.filterByDepartment(this.inactiveConsultants()))
  );
  activePagination = new TablePagination(this.filteredActiveConsultants);
  inactivePagination = new TablePagination(this.filteredInactiveConsultants);

  filterDepartmentName = computed(() => {
    const departmentId = this.filterDepartmentId();
    return departmentId === null ? null : (this.departments().find((d) => d.id === departmentId)?.name ?? null);
  });

  constructor() {
    this.refreshActive();
    this.departmentService.list().subscribe({
      next: (departments) => this.departments.set(departments.filter((d) => d.active)),
      error: () => this.notification.error('Failed to load departments.')
    });
    this.specializationService.list().subscribe({
      next: (specializations) => this.specializations.set(specializations.filter((s) => s.active)),
      error: () => this.notification.error('Failed to load specializations.')
    });
    this.route.queryParamMap.subscribe((params) => {
      const raw = params.get('departmentId');
      this.filterDepartmentId.set(raw ? Number(raw) : null);
    });
  }

  private filterByDepartment(consultants: Consultant[]): Consultant[] {
    const departmentId = this.filterDepartmentId();
    return departmentId === null ? consultants : consultants.filter((c) => c.departmentId === departmentId);
  }

  private filterBySearch(consultants: Consultant[]): Consultant[] {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) {
      return consultants;
    }
    return consultants.filter(
      (c) => c.name.toLowerCase().includes(term) || (c.departmentName ?? '').toLowerCase().includes(term)
    );
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.activePagination.reset();
    this.inactivePagination.reset();
  }

  clearFilter(): void {
    this.router.navigate([], { queryParams: {} });
  }

  refreshActive(): void {
    this.loadingActive.set(true);
    this.service.list().subscribe({
      next: (consultants) => {
        this.activeConsultants.set(consultants);
        this.activePagination.reset();
        this.loadingActive.set(false);
      },
      error: () => {
        this.loadingActive.set(false);
        this.notification.error('Failed to load consultants.');
      }
    });
  }

  refreshInactive(): void {
    this.loadingInactive.set(true);
    this.service.listInactive().subscribe({
      next: (consultants) => {
        this.inactiveConsultants.set(consultants);
        this.inactivePagination.reset();
        this.loadingInactive.set(false);
      },
      error: () => {
        this.loadingInactive.set(false);
        this.notification.error('Failed to load de-activated consultants.');
      }
    });
  }

  onTabChange(index: number): void {
    if (index === 1) {
      this.refreshInactive();
    }
  }

  openAddDialog(): void {
    this.openFormDialog();
  }

  openEditDialog(consultant: Consultant): void {
    this.openFormDialog(consultant);
  }

  private openFormDialog(consultant?: Consultant): void {
    this.dialog
      .open(ConsultantFormDialogComponent, {
        // Wide enough for the embedded availability table's own natural
        // content width (same table as ConsultantTimingsDialogComponent,
        // same 1040px sizing reasoning - see its own dialog.open() call in
        // this file) to avoid a horizontal scrollbar; .consultant-dialog-field
        // caps the plain single-line fields above it at a sensible reading
        // width so they don't just stretch to fill the extra room.
        width: '1040px',
        maxWidth: '95vw',
        data: { consultant, departments: this.departments(), specializations: this.specializations() }
      })
      .afterClosed()
      .subscribe((result?: ConsultantFormDialogResult) => {
        if (!result) {
          return;
        }
        const save$ = consultant?.id
          ? this.service.update(consultant.id, result.input)
          : this.service.create(result.input);
        save$.subscribe({
          next: (saved) => {
            this.notification.success(consultant ? 'Consultant updated.' : 'Consultant added.');
            const followUps: Observable<unknown>[] = [];
            if (result.imageFile && saved.id !== null) {
              followUps.push(this.service.uploadImage(saved.id, result.imageFile));
            }
            if (result.availability && saved.id !== null) {
              followUps.push(this.service.saveAvailability(saved.id, result.availability));
            }
            if (followUps.length === 0) {
              this.refreshActive();
              return;
            }
            forkJoin(followUps).subscribe({
              next: () => this.refreshActive(),
              error: () => {
                this.notification.error('Consultant saved, but some details failed to save.');
                this.refreshActive();
              }
            });
          },
          error: (err) => this.notification.error(err.error?.message ?? 'Failed to save consultant.')
        });
      });
  }

  openTimingsDialog(consultant: Consultant): void {
    if (consultant.id === null) {
      return;
    }
    this.dialog.open(ConsultantTimingsDialogComponent, {
      // 960px used to be 26px too narrow for the availability table's own
      // natural content width once its content padding was accounted for,
      // forcing a horizontal scrollbar on every open regardless of screen
      // size - see consultant-availability-form.component.scss's table.
      width: '1040px',
      maxWidth: '95vw',
      data: { consultantId: consultant.id, consultantName: consultant.name }
    });
  }

  deactivate(consultant: Consultant): void {
    if (consultant.id === null) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: `Deactivate ${consultant.name}?`,
        message: 'The consultant will be moved to De-Activated Consultants and can be restored later.',
        confirmLabel: 'Deactivate',
        destructive: true
      })
      .subscribe((confirmed) => {
        if (!confirmed || consultant.id === null) {
          return;
        }
        this.service.deactivate(consultant.id).subscribe({
          next: () => {
            this.notification.success('Consultant deactivated.');
            this.refreshActive();
            this.refreshInactive();
          },
          error: () => this.notification.error('Failed to deactivate consultant.')
        });
      });
  }

  restore(consultant: Consultant): void {
    if (consultant.id === null) {
      return;
    }
    this.service.restore(consultant.id).subscribe({
      next: () => {
        this.notification.success('Consultant restored.');
        this.refreshActive();
        this.refreshInactive();
      },
      error: () => this.notification.error('Failed to restore consultant.')
    });
  }

  togglePublish(consultant: Consultant): void {
    if (consultant.id === null) {
      return;
    }
    const request = consultant.publishedToWeb ? this.service.unpublish(consultant.id) : this.service.publish(consultant.id);
    request.subscribe({
      next: () => {
        this.notification.success(
          consultant.publishedToWeb ? 'Consultant removed from website.' : 'Consultant published to website.'
        );
        this.refreshActive();
      },
      error: () => this.notification.error('Failed to update website visibility.')
    });
  }
}

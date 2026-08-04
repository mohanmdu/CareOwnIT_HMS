import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute } from '@angular/router';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { NotificationService } from '../../shared/services/notification.service';
import { PromptDialogService } from '../../shared/services/prompt-dialog.service';
import { TablePagination } from '../../shared/table/table-pagination';
import { PrivateImageDirective } from '../../shared/directives/private-image.directive';
import { PrivateLinkDirective } from '../../shared/directives/private-link.directive';
import { ClinicSettingsService } from '../masters-admin/clinic-settings/clinic-settings.service';
import { Patient } from '../registration/patients/patient.model';
import { PatientService } from '../registration/patients/patient.service';
import { PatientReport } from './patient-report.model';
import { PatientReportService } from './patient-report.service';

interface FileRow {
  sNo: number;
  file: PatientReport;
}

function matches(value: string, term: string): boolean {
  const trimmed = term.trim().toLowerCase();
  return !trimmed || value.toLowerCase().includes(trimmed);
}

/**
 * Patient Details + Files/Deleted Files (screen 2 of 2): "Delete" moves a
 * file from the Files table to the Deleted Files table (soft delete,
 * captures a Reason via PromptDialogService); "Permanent Delete" removes
 * the DB row and the file on disk (ConfirmDialogService, no reason needed -
 * it was already captured at soft-delete time). Both tables share the same
 * "Show N entries" + per-column filter chrome, duplicated rather than
 * abstracted since their filterable columns differ (mirrors
 * room-availability.component's bespoke TablePagination wiring).
 */
@Component({
  selector: 'app-patient-files',
  standalone: true,
  imports: [DatePipe, FormsModule, MatButtonModule, MatIconModule, MatProgressBarModule, PrivateImageDirective, PrivateLinkDirective],
  templateUrl: './patient-files.component.html',
  styleUrl: './patient-files.component.scss'
})
export class PatientFilesComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly patientService = inject(PatientService);
  private readonly reportService = inject(PatientReportService);
  private readonly clinicSettingsService = inject(ClinicSettingsService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly promptDialog = inject(PromptDialogService);
  private readonly notification = inject(NotificationService);

  private readonly patientId = Number(this.route.snapshot.paramMap.get('patientId'));
  private clinicName = 'the hospital';

  readonly pageSizeOptions = [10, 25, 50, 100];

  loading = signal(true);
  patient = signal<Patient | null>(null);
  activeFiles = signal<PatientReport[]>([]);
  deletedFiles = signal<PatientReport[]>([]);

  // Active Files filters
  activeFilterSNo = signal('');
  activeFilterMobile = signal('');
  activeFilterCreated = signal('');
  activeFilterUploaded = signal('');
  activeFilterUploadedBy = signal('');
  activeFilterComments = signal('');

  activeRows = computed<FileRow[]>(() => this.activeFiles().map((file, i) => ({ sNo: i + 1, file })));
  filteredActiveRows = computed<FileRow[]>(() =>
    this.activeRows().filter(
      (row) =>
        matches(String(row.sNo), this.activeFilterSNo()) &&
        matches(row.file.patientMobileNumber, this.activeFilterMobile()) &&
        matches(row.file.uploadedAt, this.activeFilterCreated()) &&
        matches(row.file.uploadedAt, this.activeFilterUploaded()) &&
        matches(row.file.uploadedBy ?? '', this.activeFilterUploadedBy()) &&
        matches(row.file.comments ?? '', this.activeFilterComments())
    )
  );
  activePagination = new TablePagination(this.filteredActiveRows);
  activeTotalPages = computed(() => Math.max(Math.ceil(this.activePagination.length() / this.activePagination.pageSize()), 1));
  activePageNumbers = computed(() => Array.from({ length: this.activeTotalPages() }, (_, i) => i + 1));
  activeRangeStart = computed(() =>
    this.activePagination.length() === 0 ? 0 : this.activePagination.pageIndex() * this.activePagination.pageSize() + 1
  );
  activeRangeEnd = computed(() =>
    Math.min((this.activePagination.pageIndex() + 1) * this.activePagination.pageSize(), this.activePagination.length())
  );

  // Deleted Files filters
  deletedFilterSNo = signal('');
  deletedFilterFileType = signal('');
  deletedFilterCreated = signal('');
  deletedFilterDeleted = signal('');
  deletedFilterDeletedBy = signal('');
  deletedFilterReason = signal('');

  deletedRows = computed<FileRow[]>(() => this.deletedFiles().map((file, i) => ({ sNo: i + 1, file })));
  filteredDeletedRows = computed<FileRow[]>(() =>
    this.deletedRows().filter(
      (row) =>
        matches(String(row.sNo), this.deletedFilterSNo()) &&
        matches(row.file.fileType, this.deletedFilterFileType()) &&
        matches(row.file.uploadedAt, this.deletedFilterCreated()) &&
        matches(row.file.deletedAt ?? '', this.deletedFilterDeleted()) &&
        matches(row.file.deletedBy ?? '', this.deletedFilterDeletedBy()) &&
        matches(row.file.deleteReason ?? '', this.deletedFilterReason())
    )
  );
  deletedPagination = new TablePagination(this.filteredDeletedRows);
  deletedTotalPages = computed(() => Math.max(Math.ceil(this.deletedPagination.length() / this.deletedPagination.pageSize()), 1));
  deletedPageNumbers = computed(() => Array.from({ length: this.deletedTotalPages() }, (_, i) => i + 1));
  deletedRangeStart = computed(() =>
    this.deletedPagination.length() === 0 ? 0 : this.deletedPagination.pageIndex() * this.deletedPagination.pageSize() + 1
  );
  deletedRangeEnd = computed(() =>
    Math.min((this.deletedPagination.pageIndex() + 1) * this.deletedPagination.pageSize(), this.deletedPagination.length())
  );

  constructor() {
    this.clinicSettingsService.get().subscribe({
      next: (settings) => {
        if (settings.name) {
          this.clinicName = settings.name;
        }
      },
      error: () => {}
    });

    this.patientService.get(this.patientId).subscribe({
      next: (patient) => {
        this.patient.set(patient);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load patient details.');
      }
    });

    this.refresh();
  }

  private refresh(): void {
    this.reportService.getActiveFiles(this.patientId).subscribe({
      next: (files) => this.activeFiles.set(files),
      error: () => this.notification.error('Failed to load files.')
    });
    this.reportService.getDeletedFiles(this.patientId).subscribe({
      next: (files) => this.deletedFiles.set(files),
      error: () => this.notification.error('Failed to load deleted files.')
    });
  }

  isImage(file: PatientReport): boolean {
    return ['JPG', 'JPEG', 'PNG', 'WEBP'].includes(file.fileType);
  }

  onActivePageSizeChange(size: number): void {
    this.activePagination.pageSize.set(size);
    this.activePagination.reset();
  }

  activeGoToPage(page: number): void {
    this.activePagination.pageIndex.set(page - 1);
  }

  activePreviousPage(): void {
    this.activePagination.pageIndex.update((i) => Math.max(i - 1, 0));
  }

  activeNextPage(): void {
    this.activePagination.pageIndex.update((i) => Math.min(i + 1, this.activeTotalPages() - 1));
  }

  onDeletedPageSizeChange(size: number): void {
    this.deletedPagination.pageSize.set(size);
    this.deletedPagination.reset();
  }

  deletedGoToPage(page: number): void {
    this.deletedPagination.pageIndex.set(page - 1);
  }

  deletedPreviousPage(): void {
    this.deletedPagination.pageIndex.update((i) => Math.max(i - 1, 0));
  }

  deletedNextPage(): void {
    this.deletedPagination.pageIndex.update((i) => Math.min(i + 1, this.deletedTotalPages() - 1));
  }

  shareToWhatsApp(file: PatientReport): void {
    const patient = this.patient();
    if (!patient) {
      return;
    }
    // The report itself is private (see PrivateFileController) - a raw
    // file.filePath would 401 for the patient, who has no HMS login at all.
    // Mint a time-limited, unauthenticated link scoped to just this one
    // report instead - see PatientReportService.createShareLink() on the backend.
    this.reportService.createShareLink(file.id).subscribe({
      next: ({ url }) => {
        const digitsOnly = patient.mobileNumber.replace(/\D/g, '');
        const fileUrl = `${location.origin}${url}`;
        const message = encodeURIComponent(
          `Hello ${patient.firstName}, your medical report (${file.originalFileName}) is available: ${fileUrl}. Please contact ${this.clinicName} for details.`
        );
        window.open(`https://wa.me/${digitsOnly}?text=${message}`, '_blank');
      },
      error: () => this.notification.error('Failed to create a share link for this file.')
    });
  }

  deleteFile(file: PatientReport): void {
    this.promptDialog
      .prompt({
        title: 'Delete this file?',
        message: `"${file.originalFileName}" will move to the Deleted Files list below.`,
        fields: [{ key: 'reason', label: 'Reason', type: 'textarea', required: true }],
        confirmLabel: 'Delete',
        destructive: true
      })
      .subscribe((values) => {
        if (!values) {
          return;
        }
        this.reportService.softDelete(file.id, values['reason'] as string).subscribe({
          next: () => {
            this.notification.success('File deleted.');
            this.refresh();
          },
          error: () => this.notification.error('Failed to delete the file.')
        });
      });
  }

  permanentlyDelete(file: PatientReport): void {
    this.confirmDialog
      .confirm({
        title: 'Permanently delete this file?',
        message: `"${file.originalFileName}" and its stored file will be permanently removed. This cannot be undone.`,
        confirmLabel: 'Delete Permanently',
        destructive: true
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.reportService.permanentDelete(file.id).subscribe({
          next: () => {
            this.notification.success('File permanently deleted.');
            this.refresh();
          },
          error: () => this.notification.error('Failed to permanently delete the file.')
        });
      });
  }
}

import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { NotificationService } from '../../shared/services/notification.service';
import { Patient } from '../registration/patients/patient.model';
import { PatientService } from '../registration/patients/patient.service';
import { PatientReportService } from './patient-report.service';

/**
 * Reports Upload (screens 2-3 of 4): a single form whose read-only Patient
 * Name/Mobile rows and file-preview badge only appear once a patient and a
 * file are picked - not a separate route, since nothing here needs its own
 * URL (mirrors how LabRequisitionFormComponent's Consultant autocomplete
 * search is built).
 */
@Component({
  selector: 'app-reports-upload-form',
  standalone: true,
  imports: [
    FormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule
  ],
  templateUrl: './reports-upload-form.component.html',
  styleUrl: './reports-upload-form.component.scss'
})
export class ReportsUploadFormComponent {
  private readonly patientService = inject(PatientService);
  private readonly reportService = inject(PatientReportService);
  private readonly notification = inject(NotificationService);
  private readonly nameSearchTerms = new Subject<string>();
  private readonly patientIdSearchTerms = new Subject<string>();

  loadingPatients = signal(true);
  patients = signal<Patient[]>([]);

  nameQuery = signal('');
  nameResults = signal<Patient[]>([]);

  patientIdInput = signal('');
  selectedPatient = signal<Patient | null>(null);

  comments = '';
  selectedFile = signal<File | null>(null);
  filePreviewUrl = signal<string | null>(null);
  isImagePreview = signal(false);

  uploading = signal(false);

  canUpload = computed(() => this.selectedPatient() !== null && this.selectedFile() !== null && !this.uploading());

  constructor() {
    this.patientService.search('').subscribe({
      next: (patients) => {
        this.patients.set(patients);
        this.loadingPatients.set(false);
      },
      error: () => {
        this.loadingPatients.set(false);
        this.notification.error('Failed to load patients.');
      }
    });

    this.nameSearchTerms
      .pipe(debounceTime(300), distinctUntilChanged(), switchMap((query) => this.patientService.search(query)), takeUntilDestroyed())
      .subscribe((results) => this.nameResults.set(results));

    this.patientIdSearchTerms
      .pipe(debounceTime(300), distinctUntilChanged(), switchMap((query) => this.patientService.search(query)), takeUntilDestroyed())
      .subscribe((results) => {
        const typed = this.patientIdInput().trim().toLowerCase();
        const match = results.find((p) => p.registrationNumber?.toLowerCase() === typed);
        this.selectedPatient.set(match ?? null);
      });
  }

  onNameQueryChange(value: string): void {
    this.nameQuery.set(value);
    if (value.trim().length >= 1) {
      this.nameSearchTerms.next(value.trim());
    } else {
      this.nameResults.set([]);
    }
  }

  selectPatientFromAutocomplete(patient: Patient): void {
    this.selectPatient(patient);
    this.nameQuery.set('');
    this.nameResults.set([]);
  }

  onPatientIdChange(value: string): void {
    this.patientIdInput.set(value);
    this.selectedPatient.set(null);
    if (value.trim().length >= 1) {
      this.patientIdSearchTerms.next(value.trim());
    }
  }

  selectPatient(patient: Patient): void {
    this.selectedPatient.set(patient);
    this.patientIdInput.set(patient.registrationNumber ?? '');
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFile.set(file);
    if (this.filePreviewUrl()) {
      URL.revokeObjectURL(this.filePreviewUrl()!);
    }
    if (file && file.type.startsWith('image/')) {
      this.filePreviewUrl.set(URL.createObjectURL(file));
      this.isImagePreview.set(true);
    } else {
      this.filePreviewUrl.set(null);
      this.isImagePreview.set(false);
    }
  }

  upload(): void {
    const patient = this.selectedPatient();
    const file = this.selectedFile();
    if (!patient || patient.id === null || !file) {
      return;
    }
    this.uploading.set(true);
    this.reportService.upload(patient.id, this.comments || null, file).subscribe({
      next: (report) => {
        this.uploading.set(false);
        this.notification.success(`Medical report for ${report.patientName} - ${report.patientRegistrationNumber} has been uploaded successfully.`);
        if (this.filePreviewUrl()) {
          URL.revokeObjectURL(this.filePreviewUrl()!);
        }
        this.comments = '';
        this.selectedFile.set(null);
        this.filePreviewUrl.set(null);
        this.isImagePreview.set(false);
      },
      error: (err) => {
        this.uploading.set(false);
        this.notification.error(err.error?.message ?? 'Failed to upload the report.');
      }
    });
  }
}

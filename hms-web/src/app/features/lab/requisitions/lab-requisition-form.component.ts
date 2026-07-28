import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { NotificationService } from '../../../shared/services/notification.service';
import { Consultant } from '../../masters-admin/consultants/consultant.model';
import { ConsultantService } from '../../masters-admin/consultants/consultant.service';
import { PatientVisitSummaryService } from '../../icd/patient-visit-summary.service';
import { Patient } from '../../registration/patients/patient.model';
import { PatientService } from '../../registration/patients/patient.service';
import { LabBillingType, LabCategoryTestGroup } from './lab-requisition.model';
import { LabRequisitionService } from './lab-requisition.service';

/**
 * Lab Requisition Form (screen 2 of 6): checkbox test selection grouped by
 * Category, priced by whichever of the patient's most recent Appointment vs
 * Admission is more recent (reusing PatientVisitSummaryService from the ICD
 * module rather than re-deriving OP/IP client-side) - the backend computes
 * the same patientType independently at create time, so the running total
 * shown here matches what's actually charged.
 */
@Component({
  selector: 'app-lab-requisition-form',
  standalone: true,
  imports: [DecimalPipe, FormsModule, MatAutocompleteModule, MatButtonModule, MatCheckboxModule, MatProgressBarModule],
  templateUrl: './lab-requisition-form.component.html',
  styleUrl: './lab-requisition-form.component.scss'
})
export class LabRequisitionFormComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly patientService = inject(PatientService);
  private readonly visitSummaryService = inject(PatientVisitSummaryService);
  private readonly consultantService = inject(ConsultantService);
  private readonly service = inject(LabRequisitionService);
  private readonly notification = inject(NotificationService);
  private readonly consultantSearchTerms = new Subject<string>();

  private readonly patientId = Number(this.route.snapshot.paramMap.get('patientId'));

  loading = signal(true);
  saving = signal(false);
  patient = signal<Patient | null>(null);
  patientType = signal<'OP' | 'IP'>('OP');
  testGroups = signal<LabCategoryTestGroup[]>([]);
  selectedSubCategoryIds = signal<Set<number>>(new Set());

  billingType: LabBillingType = 'CASH';

  consultantQuery = signal('');
  consultantResults = signal<Consultant[]>([]);
  selectedConsultant = signal<Consultant | null>(null);

  totalAmount = computed(() => {
    const type = this.patientType();
    const ids = this.selectedSubCategoryIds();
    let total = 0;
    for (const group of this.testGroups()) {
      for (const test of group.tests) {
        if (ids.has(test.subCategoryId)) {
          total += type === 'IP' ? test.ipAmount : test.opAmount;
        }
      }
    }
    return total;
  });

  constructor() {
    this.patientService.get(this.patientId).subscribe({
      next: (patient) => {
        this.patient.set(patient);
        if (patient.registrationNumber) {
          this.visitSummaryService.search(patient.registrationNumber).subscribe((rows) => {
            const match = rows.find((r) => r.patientId === this.patientId);
            if (match?.patientType === 'IP') {
              this.patientType.set('IP');
            }
          });
        }
      },
      error: () => this.notification.error('Failed to load patient details.')
    });

    this.service.getTestCatalog().subscribe({
      next: (groups) => {
        this.testGroups.set(groups);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load the lab test catalog.');
      }
    });

    this.consultantSearchTerms
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((query) => this.consultantService.search(query)),
        takeUntilDestroyed()
      )
      .subscribe((results) => this.consultantResults.set(results));
  }

  onConsultantQueryChange(value: string): void {
    this.consultantQuery.set(value);
    this.selectedConsultant.set(null);
    if (value.trim().length >= 1) {
      this.consultantSearchTerms.next(value.trim());
    } else {
      this.consultantResults.set([]);
    }
  }

  selectConsultant(consultant: Consultant): void {
    this.selectedConsultant.set(consultant);
    this.consultantQuery.set(consultant.name);
    this.consultantResults.set([]);
  }

  isSelected(subCategoryId: number): boolean {
    return this.selectedSubCategoryIds().has(subCategoryId);
  }

  toggleTest(subCategoryId: number, checked: boolean): void {
    const set = new Set(this.selectedSubCategoryIds());
    if (checked) {
      set.add(subCategoryId);
    } else {
      set.delete(subCategoryId);
    }
    this.selectedSubCategoryIds.set(set);
  }

  get isValid(): boolean {
    return this.selectedSubCategoryIds().size > 0;
  }

  submit(): void {
    if (!this.isValid) {
      return;
    }
    this.saving.set(true);
    this.service
      .create({
        patientId: this.patientId,
        consultantId: this.selectedConsultant()?.id ?? null,
        billingType: this.billingType,
        subCategoryIds: Array.from(this.selectedSubCategoryIds())
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.notification.success('Labtest requisition has been initiated. Receive the payment in Labtest billing.');
          this.router.navigate(['/lab/billing']);
        },
        error: (err) => {
          this.saving.set(false);
          this.notification.error(err.error?.message ?? 'Failed to submit the requisition.');
        }
      });
  }
}

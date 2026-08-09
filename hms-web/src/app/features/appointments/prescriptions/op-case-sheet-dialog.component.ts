import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { NotificationService } from '../../../shared/services/notification.service';
import { Product } from '../../pharmacy/inventory-master/products/product.model';
import { ProductService } from '../../pharmacy/inventory-master/products/product.service';
import { OpCaseSheet, OpCaseSheetHeader, OpCaseSheetSaveRequest, OpPrescriptionItem } from './op-case-sheet.model';
import { OpCaseSheetService } from './op-case-sheet.service';

/** Exactly one of appointmentId/directBillingId is set - matches PrescriptionWorklistEntry's own two-source shape. */
export interface OpCaseSheetDialogData {
  appointmentId: number | null;
  directBillingId: number | null;
}

const YES_NO_OPTIONS = ['Yes', 'No'];

function emptyItem(): OpPrescriptionItem {
  return {
    id: null,
    drugName: '',
    qty: null,
    intake: null,
    morningDose: null,
    afternoonDose: null,
    eveningDose: null,
    nightDose: null
  };
}

/**
 * Doctor's OP Case Sheet entry/edit form (migration doc's Patient
 * Prescription screens) - vitals, assessment, diagnosis, and a repeating
 * Pharmacy Prescription grid. "Save draft" always upserts the whole sheet
 * (OpCaseSheetService.save), matching the legacy button's semantics of
 * overwriting with the latest full form state rather than tracking partial
 * edits.
 */
@Component({
  selector: 'app-op-case-sheet-dialog',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatDatepickerModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule
  ],
  templateUrl: './op-case-sheet-dialog.component.html',
  styleUrl: './op-case-sheet-dialog.component.scss'
})
export class OpCaseSheetDialogComponent implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<OpCaseSheetDialogComponent, boolean>);
  private readonly data = inject<OpCaseSheetDialogData>(MAT_DIALOG_DATA);
  private readonly service = inject(OpCaseSheetService);
  private readonly productService = inject(ProductService);
  private readonly notification = inject(NotificationService);

  readonly yesNoOptions = YES_NO_OPTIONS;
  readonly itemColumns = ['drugName', 'qty', 'intake', 'morningDose', 'afternoonDose', 'eveningDose', 'nightDose', 'remove'];

  loading = signal(true);
  saving = signal(false);
  header = signal<OpCaseSheetHeader | null>(null);
  drugSuggestions = signal<Product[]>([]);

  form: OpCaseSheetSaveRequest = this.blankForm();
  newItem: OpPrescriptionItem = emptyItem();

  // A plain field, not a getter - a getter re-derived from form.reviewDate would
  // return a brand-new Date instance on every change-detection cycle, which
  // MatDatepicker (bound via [(ngModel)]) interprets as the model constantly
  // changing underneath it and never settles - the calendar becomes unresponsive.
  reviewDateValue: Date | null = null;

  ngOnInit(): void {
    this.productService.list().subscribe({
      next: (products) => this.drugSuggestions.set(products),
      error: () => {}
    });
    const load = this.data.appointmentId !== null
      ? this.service.getByAppointment(this.data.appointmentId)
      : this.service.getByDirectBilling(this.data.directBillingId!);
    load.subscribe({
      next: (caseSheet) => {
        this.header.set(caseSheet.header);
        this.form = this.toForm(caseSheet);
        this.reviewDateValue = caseSheet.reviewDate ? new Date(caseSheet.reviewDate) : null;
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load the case sheet.');
      }
    });
  }

  addItem(): void {
    if (!this.newItem.drugName.trim()) {
      return;
    }
    this.form.prescriptionItems = [...this.form.prescriptionItems, this.newItem];
    this.newItem = emptyItem();
  }

  removeItem(index: number): void {
    this.form.prescriptionItems = this.form.prescriptionItems.filter((_, i) => i !== index);
  }

  save(): void {
    this.saving.set(true);
    const request: OpCaseSheetSaveRequest = { ...this.form, reviewDate: this.toIsoDate(this.reviewDateValue) };
    const save$ = this.data.appointmentId !== null
      ? this.service.save(this.data.appointmentId, request)
      : this.service.saveDirectBilling(this.data.directBillingId!, request);
    save$.subscribe({
      next: () => {
        this.saving.set(false);
        this.notification.success('Case sheet saved.');
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.notification.error(err.error?.message ?? 'Failed to save the case sheet.');
      }
    });
  }

  private toIsoDate(date: Date | null): string | null {
    if (!date) {
      return null;
    }
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  close(): void {
    this.dialogRef.close(false);
  }

  private blankForm(): OpCaseSheetSaveRequest {
    return {
      foodDrugAllergy: null,
      heightCm: null,
      weightKg: null,
      bmi: null,
      temperatureF: null,
      pulseBpm: null,
      respirationBpm: null,
      bpSystolic: null,
      bpDiastolic: null,
      spo2: null,
      bodyFatPercent: null,
      chiefComplaints: null,
      pastMedicalCondition: null,
      currentMedication: null,
      physicalActivity: null,
      sleepDurationHours: null,
      smoking: null,
      alcohol: null,
      surgery: null,
      familyHistory: null,
      provisionalDiagnosis: null,
      cbg: null,
      findings: null,
      investigation: null,
      doctorNotes1: null,
      doctorNotes2: null,
      prescriptionItems: [],
      diet: null,
      remarks: null,
      reviewDate: null
    };
  }

  private toForm(caseSheet: OpCaseSheet): OpCaseSheetSaveRequest {
    return {
      foodDrugAllergy: caseSheet.foodDrugAllergy,
      heightCm: caseSheet.heightCm,
      weightKg: caseSheet.weightKg,
      bmi: caseSheet.bmi,
      temperatureF: caseSheet.temperatureF,
      pulseBpm: caseSheet.pulseBpm,
      respirationBpm: caseSheet.respirationBpm,
      bpSystolic: caseSheet.bpSystolic,
      bpDiastolic: caseSheet.bpDiastolic,
      spo2: caseSheet.spo2,
      bodyFatPercent: caseSheet.bodyFatPercent,
      chiefComplaints: caseSheet.chiefComplaints,
      pastMedicalCondition: caseSheet.pastMedicalCondition,
      currentMedication: caseSheet.currentMedication,
      physicalActivity: caseSheet.physicalActivity,
      sleepDurationHours: caseSheet.sleepDurationHours,
      smoking: caseSheet.smoking,
      alcohol: caseSheet.alcohol,
      surgery: caseSheet.surgery,
      familyHistory: caseSheet.familyHistory,
      provisionalDiagnosis: caseSheet.provisionalDiagnosis,
      cbg: caseSheet.cbg,
      findings: caseSheet.findings,
      investigation: caseSheet.investigation,
      doctorNotes1: caseSheet.doctorNotes1,
      doctorNotes2: caseSheet.doctorNotes2,
      prescriptionItems: caseSheet.prescriptionItems,
      diet: caseSheet.diet,
      remarks: caseSheet.remarks,
      reviewDate: caseSheet.reviewDate
    };
  }
}

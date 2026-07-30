import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { PatientSearchComponent } from '../../shared/ui/patient-search/patient-search.component';
import { Patient } from '../registration/patients/patient.model';
import { Consultant } from '../masters-admin/consultants/consultant.model';
import { QueuePriority } from './doctor-queue.model';

export interface RegisterWalkInDialogData {
  consultants: Consultant[];
  defaultConsultantId: number | null;
}

export interface RegisterWalkInDialogResult {
  patientId: number;
  consultantId: number;
  priority?: QueuePriority;
  priorityReason?: string;
}

@Component({
  selector: 'app-register-walk-in-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatInputModule,
    MatButtonModule,
    PatientSearchComponent
  ],
  templateUrl: './register-walk-in-dialog.component.html',
  styleUrl: './register-walk-in-dialog.component.scss'
})
export class RegisterWalkInDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<RegisterWalkInDialogComponent, RegisterWalkInDialogResult>);
  readonly data = inject<RegisterWalkInDialogData>(MAT_DIALOG_DATA);

  selectedPatient = signal<Patient | null>(null);
  consultantId: number | null = this.data.defaultConsultantId;
  isEmergency = false;
  priorityReason = '';

  get isValid(): boolean {
    return this.selectedPatient() !== null && this.consultantId !== null;
  }

  onPatientSelected(patient: Patient): void {
    this.selectedPatient.set(patient);
  }

  changePatient(): void {
    this.selectedPatient.set(null);
  }

  submit(): void {
    const patient = this.selectedPatient();
    if (!this.isValid || patient === null || patient.id === null || this.consultantId === null) {
      return;
    }
    this.dialogRef.close({
      patientId: patient.id,
      consultantId: this.consultantId,
      ...(this.isEmergency
        ? { priority: 'EMERGENCY' as const, priorityReason: this.priorityReason.trim() || undefined }
        : {})
    });
  }
}

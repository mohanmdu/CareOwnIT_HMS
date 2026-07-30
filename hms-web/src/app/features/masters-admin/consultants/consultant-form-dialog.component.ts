import { Component, inject, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Department } from '../departments/department.model';
import { Specialization } from '../specializations/specialization.model';
import { ConsultantAvailabilityFormComponent } from './consultant-availability-form.component';
import { ConsultantAvailability } from './consultant-timing.model';
import { Consultant } from './consultant.model';
import { ConsultantInput, ConsultantService } from './consultant.service';

export interface ConsultantFormDialogData {
  consultant?: Consultant;
  departments: Department[];
  specializations: Specialization[];
}

export interface ConsultantFormDialogResult {
  input: ConsultantInput;
  imageFile: File | null;
  availability: ConsultantAvailability | null;
}

/**
 * Add/Edit dialog for the Consultants grid - mirrors PatientFormDialogComponent's
 * shape (one dialog for both add and edit). Image upload is deferred to the
 * caller: this dialog just hands back the picked File (if any), since a new
 * consultant doesn't have an id to upload against until after creation.
 */
@Component({
  selector: 'app-consultant-form-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    ConsultantAvailabilityFormComponent
  ],
  templateUrl: './consultant-form-dialog.component.html',
  styleUrl: './consultant-form-dialog.component.scss'
})
export class ConsultantFormDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<ConsultantFormDialogComponent, ConsultantFormDialogResult>);
  private readonly data = inject<ConsultantFormDialogData>(MAT_DIALOG_DATA);
  private readonly consultantService = inject(ConsultantService);

  @ViewChild(ConsultantAvailabilityFormComponent) availabilityForm?: ConsultantAvailabilityFormComponent;

  readonly isEdit = !!this.data.consultant;
  readonly departments = this.data.departments;
  readonly specializations = this.data.specializations;

  imagePreviewUrl = signal<string | null>(this.data.consultant?.imageUrl ?? null);
  private imageFile: File | null = null;

  loadingAvailability = signal(false);
  initialAvailability = signal<ConsultantAvailability | null>(null);

  form = {
    name: this.data.consultant?.name ?? '',
    departmentId: this.data.consultant?.departmentId ?? (null as number | null),
    specializationId: this.data.consultant?.specializationId ?? (null as number | null),
    email: this.data.consultant?.email ?? '',
    mobileNumber: this.data.consultant?.mobileNumber ?? '',
    consultationFee: this.data.consultant?.consultationFee ?? 0,
    profile: this.data.consultant?.profile ?? '',
    address: this.data.consultant?.address ?? '',
    acceptingAppointments: this.data.consultant?.acceptingAppointments ?? true,
    queueTokenPrefix: this.data.consultant?.queueTokenPrefix ?? '',
    consultingRoomLabel: this.data.consultant?.consultingRoomLabel ?? ''
  };

  constructor() {
    const consultantId = this.data.consultant?.id;
    if (consultantId) {
      this.loadingAvailability.set(true);
      this.consultantService.getAvailability(consultantId).subscribe({
        next: (availability) => {
          this.initialAvailability.set(availability);
          this.loadingAvailability.set(false);
        },
        error: () => this.loadingAvailability.set(false)
      });
    }
  }

  get isValid(): boolean {
    if (this.form.name.trim().length === 0 || !this.form.departmentId || !this.form.specializationId) {
      return false;
    }
    if (this.form.mobileNumber && !/^\d{10}$/.test(this.form.mobileNumber)) {
      return false;
    }
    if (this.form.acceptingAppointments && this.loadingAvailability()) {
      return false;
    }
    if (this.form.acceptingAppointments && this.availabilityForm && !this.availabilityForm.isValid) {
      return false;
    }
    return true;
  }

  onMobileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.form.mobileNumber = input.value.replace(/\D/g, '').slice(0, 10);
  }

  onQueueTokenPrefixInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.form.queueTokenPrefix = input.value.replace(/[^a-zA-Z]/g, '').toUpperCase().slice(0, 3);
  }

  onImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) {
      return;
    }
    this.imageFile = file;
    const reader = new FileReader();
    reader.onload = () => this.imagePreviewUrl.set(reader.result as string);
    reader.readAsDataURL(file);
  }

  submit(): void {
    if (!this.isValid) {
      return;
    }
    this.dialogRef.close({
      input: {
        name: this.form.name.trim(),
        departmentId: this.form.departmentId!,
        specializationId: this.form.specializationId,
        email: this.form.email.trim() || null,
        mobileNumber: this.form.mobileNumber.trim() || null,
        consultationFee: this.form.consultationFee,
        profile: this.form.profile.trim() || null,
        address: this.form.address.trim() || null,
        acceptingAppointments: this.form.acceptingAppointments,
        queueTokenPrefix: this.form.queueTokenPrefix.trim() || null,
        consultingRoomLabel: this.form.consultingRoomLabel.trim() || null
      },
      imageFile: this.imageFile,
      availability: this.form.acceptingAppointments && this.availabilityForm ? this.availabilityForm.getValue() : null
    });
  }
}

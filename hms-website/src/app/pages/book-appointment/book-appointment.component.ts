import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { PublicConsultantService } from '../../core/services/public-consultant.service';
import { PublicDepartmentService } from '../../core/services/public-department.service';
import { PublicAppointmentService } from '../../core/services/public-appointment.service';
import {
  NewPatientDetails,
  PatientLookupResult,
  PublicBookingResult,
  PublicConsultant,
  PublicDepartment,
  PublicSlot
} from '../../core/models/public.model';

type Step = 1 | 2 | 3 | 4;

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Deliberately no login/OTP in front of this - see the Public Appointment
 * Booking plan. Anyone can book directly; if their mobile number doesn't
 * match an existing patient, they register inline as part of the same flow
 * rather than being sent away to a separate screen.
 */
@Component({
  selector: 'app-book-appointment',
  standalone: true,
  imports: [FormsModule, PageHeaderComponent],
  templateUrl: './book-appointment.component.html',
  styleUrl: './book-appointment.component.scss'
})
export class BookAppointmentComponent {
  private readonly departmentService = inject(PublicDepartmentService);
  private readonly consultantService = inject(PublicConsultantService);
  private readonly appointmentService = inject(PublicAppointmentService);

  readonly step = signal<Step>(1);

  // Step 1: department -> consultant
  readonly departments = signal<PublicDepartment[]>([]);
  readonly consultants = signal<PublicConsultant[]>([]);
  selectedDepartmentId: number | null = null;
  readonly selectedConsultant = signal<PublicConsultant | null>(null);

  // Step 2: date -> slot
  selectedDate = todayIso();
  readonly minDate = todayIso();
  readonly slots = signal<PublicSlot[]>([]);
  readonly loadingSlots = signal(false);
  readonly selectedSlot = signal<PublicSlot | null>(null);

  // Step 3: mobile lookup -> existing patient or new patient
  mobileNumber = '';
  readonly lookupResults = signal<PatientLookupResult[] | null>(null);
  readonly selectedPatientId = signal<number | null>(null);
  readonly showNewPatientForm = signal(false);
  newPatient: NewPatientDetails = { firstName: '', lastName: '', gender: '', age: null, email: '', address: '' };

  readonly searchingPatient = signal(false);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly bookingResult = signal<PublicBookingResult | null>(null);

  constructor() {
    this.departmentService.list().subscribe({ next: (departments) => this.departments.set(departments) });
  }

  onDepartmentChange(): void {
    this.consultants.set([]);
    this.selectedConsultant.set(null);
    if (this.selectedDepartmentId == null) {
      return;
    }
    this.consultantService.list(this.selectedDepartmentId).subscribe({ next: (consultants) => this.consultants.set(consultants) });
  }

  selectConsultant(consultant: PublicConsultant): void {
    this.selectedConsultant.set(consultant);
    this.step.set(2);
    this.loadSlots();
  }

  onDateChange(): void {
    this.selectedSlot.set(null);
    this.loadSlots();
  }

  private loadSlots(): void {
    const consultant = this.selectedConsultant();
    if (!consultant) {
      return;
    }
    this.loadingSlots.set(true);
    this.appointmentService.slots(consultant.id, this.selectedDate).subscribe({
      next: (slots) => {
        this.slots.set(slots);
        this.loadingSlots.set(false);
      },
      error: () => {
        this.slots.set([]);
        this.loadingSlots.set(false);
      }
    });
  }

  selectSlot(slot: PublicSlot): void {
    if (slot.status !== 'AVAILABLE') {
      return;
    }
    this.selectedSlot.set(slot);
    this.step.set(3);
  }

  searchByMobile(): void {
    if (!/^\d{10}$/.test(this.mobileNumber)) {
      this.errorMessage.set('Enter a valid 10-digit mobile number.');
      return;
    }
    this.errorMessage.set(null);
    this.searchingPatient.set(true);
    this.appointmentService.lookupByMobile(this.mobileNumber).subscribe({
      next: (results) => {
        this.searchingPatient.set(false);
        this.lookupResults.set(results);
        this.showNewPatientForm.set(results.length === 0);
      },
      error: () => {
        this.searchingPatient.set(false);
        this.errorMessage.set('Could not look up patients right now - please try again.');
      }
    });
  }

  selectExistingPatient(patientId: number): void {
    this.selectedPatientId.set(patientId);
    this.showNewPatientForm.set(false);
  }

  addNewPatientInstead(): void {
    this.selectedPatientId.set(null);
    this.showNewPatientForm.set(true);
  }

  changeMobileNumber(): void {
    this.lookupResults.set(null);
    this.selectedPatientId.set(null);
    this.showNewPatientForm.set(false);
  }

  get canConfirmBooking(): boolean {
    if (this.selectedPatientId() != null) {
      return true;
    }
    return this.showNewPatientForm() && this.newPatient.firstName.trim().length > 0;
  }

  confirmBooking(): void {
    const consultant = this.selectedConsultant();
    const slot = this.selectedSlot();
    if (!consultant || !slot || !this.canConfirmBooking || this.submitting()) {
      return;
    }
    this.errorMessage.set(null);
    this.submitting.set(true);
    this.appointmentService
      .book({
        mobileNumber: this.mobileNumber,
        existingPatientId: this.selectedPatientId(),
        newPatient: this.showNewPatientForm() ? this.newPatient : null,
        consultantId: consultant.id,
        date: slot.date,
        slotTime: slot.time
      })
      .subscribe({
        next: (result) => {
          this.submitting.set(false);
          this.bookingResult.set(result);
          this.step.set(4);
        },
        error: (error) => {
          this.submitting.set(false);
          this.errorMessage.set(error.error?.message ?? 'Could not book this appointment - please try again.');
        }
      });
  }

  backToStep(step: Step): void {
    this.step.set(step);
  }

  startOver(): void {
    this.step.set(1);
    this.selectedDepartmentId = null;
    this.consultants.set([]);
    this.selectedConsultant.set(null);
    this.selectedDate = todayIso();
    this.slots.set([]);
    this.selectedSlot.set(null);
    this.mobileNumber = '';
    this.lookupResults.set(null);
    this.selectedPatientId.set(null);
    this.showNewPatientForm.set(false);
    this.newPatient = { firstName: '', lastName: '', gender: '', age: null, email: '', address: '' };
    this.bookingResult.set(null);
    this.errorMessage.set(null);
  }
}

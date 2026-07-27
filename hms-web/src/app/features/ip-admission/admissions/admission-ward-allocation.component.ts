import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router } from '@angular/router';
import { NotificationService } from '../../../shared/services/notification.service';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { Patient } from '../../registration/patients/patient.model';
import { PatientService } from '../../registration/patients/patient.service';
import { Room } from '../rooms/room.model';
import { RoomService } from '../rooms/room.service';
import { RoomType } from '../rooms/room-type.model';
import { RoomTypeService } from '../rooms/room-type.service';
import { Admission, AdmissionAdmitInput, AdmissionPaymentType } from './admission.model';
import { AdmissionService } from './admission.service';

const MARITAL_STATUS_OPTIONS = ['Single', 'Married', 'Divorced', 'Widowed'];
const DESCRIPTION_OF_CASE_OPTIONS = ['Non-Surgery', 'Surgery'];
const INSURANCE_TYPE_OPTIONS = ['None', 'Direct Insurance', 'Private TPA', 'Govt Insurance'];
const PATIENT_TYPE_OPTIONS = ['Normal', 'Senior Citizen', 'VIP'];
const PAYMENT_TYPE_OPTIONS: { value: AdmissionPaymentType; label: string }[] = [
  { value: 'CASH', label: 'Cash' },
  { value: 'INSURANCE', label: 'Insurance' },
  { value: 'CORPORATE', label: 'Corporate' }
];

/**
 * Ward Allocation (PDF p.8, "EDIT INPATIENT ADMISSION ADVICE" + "WARD
 * ALLOCATION & ADVANCE"): Step 2 of the two-step flow. Lets staff review/fix
 * the intake details captured at registration, check room availability for
 * the chosen ward type, assign a specific room, record an initial advance,
 * and admit - all in one submit, mirroring the legacy screen's single
 * "Admit" action rather than splitting edit/allocate/advance into 3 steps.
 */
@Component({
  selector: 'app-admission-ward-allocation',
  standalone: true,
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatProgressBarModule,
    PageHeaderComponent
  ],
  templateUrl: './admission-ward-allocation.component.html',
  styleUrl: './admission-ward-allocation.component.scss'
})
export class AdmissionWardAllocationComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly patientService = inject(PatientService);
  private readonly roomTypeService = inject(RoomTypeService);
  private readonly roomService = inject(RoomService);
  private readonly admissionService = inject(AdmissionService);
  private readonly notification = inject(NotificationService);

  readonly maritalStatusOptions = MARITAL_STATUS_OPTIONS;
  readonly descriptionOfCaseOptions = DESCRIPTION_OF_CASE_OPTIONS;
  readonly insuranceTypeOptions = INSURANCE_TYPE_OPTIONS;
  readonly patientTypeOptions = PATIENT_TYPE_OPTIONS;
  readonly paymentTypeOptions = PAYMENT_TYPE_OPTIONS;

  admission = signal<Admission | null>(null);
  patient = signal<Patient | null>(null);
  roomTypes = signal<RoomType[]>([]);
  availableRooms = signal<Room[]>([]);
  checkedAvailability = signal(false);
  loading = signal(true);
  submitting = signal(false);
  notPending = signal(false);

  /** Distinct room numbers among the available rooms - a room number can have several beds (see bedsForSelectedRoomNumber), so it must only appear once in this dropdown. */
  uniqueRoomNumbers = computed(() => {
    const seen = new Set<string>();
    const result: string[] = [];
    for (const room of this.availableRooms()) {
      if (!seen.has(room.roomNumber)) {
        seen.add(room.roomNumber);
        result.push(room.roomNumber);
      }
    }
    return result;
  });

  /** Every available bed under the selected room number - what selectedRoomId actually resolves against. Recomputed explicitly in onRoomNumberChange() rather than as a computed(), since selectedRoomNumber is a plain ngModel-bound field, not a signal. */
  bedsForSelectedRoomNumber = signal<Room[]>([]);

  selectedRoomTypeId: number | null = null;
  selectedRoomNumber: string | null = null;
  selectedRoomId: number | null = null;
  form!: Omit<AdmissionAdmitInput, 'patientId' | 'roomId'>;

  constructor() {
    const admissionId = Number(this.route.snapshot.paramMap.get('id'));
    this.roomTypeService.list().subscribe({ next: (types) => this.roomTypes.set(types) });

    this.admissionService.get(admissionId).subscribe({
      next: (admission) => {
        if (admission.status !== 'REGISTERED') {
          this.notPending.set(true);
          this.loading.set(false);
          return;
        }
        this.admission.set(admission);
        this.selectedRoomTypeId = admission.roomTypeId;
        this.form = {
          admissionDate: admission.admissionDate,
          attenderName: admission.attenderName,
          relationType: admission.relationType,
          fatherSpouseName: admission.fatherSpouseName,
          relationMobileNo: admission.relationMobileNo,
          occupation: admission.occupation,
          maritalStatus: admission.maritalStatus,
          periodOfStayDays: admission.periodOfStayDays,
          descriptionOfCase: admission.descriptionOfCase,
          referralDoctor: admission.referralDoctor,
          primaryConsultant: admission.primaryConsultant,
          secondaryConsultant: admission.secondaryConsultant,
          paymentType: admission.paymentType,
          heightCm: admission.heightCm,
          weightKg: admission.weightKg,
          mlc: admission.mlc,
          insuranceType: admission.insuranceType,
          corporateName: admission.corporateName,
          tpaName: admission.tpaName,
          insuranceCompany: admission.insuranceCompany,
          patientType: admission.patientType,
          remarks: admission.remarks,
          aadhaarNumber: admission.aadhaarNumber,
          ventilatorRequired: admission.ventilatorRequired,
          monitorRequired: admission.monitorRequired,
          advanceAmount: admission.advanceAmount
        };
        this.patientService.get(admission.patientId).subscribe({
          next: (patient) => {
            this.patient.set(patient);
            this.loading.set(false);
          },
          error: () => {
            this.loading.set(false);
            this.notification.error('Failed to load patient details.');
          }
        });
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load admission.');
      }
    });
  }

  checkRoomAvailability(): void {
    if (!this.selectedRoomTypeId) {
      return;
    }
    this.selectedRoomNumber = null;
    this.selectedRoomId = null;
    this.bedsForSelectedRoomNumber.set([]);
    this.roomService.list().subscribe({
      next: (rooms) => {
        this.availableRooms.set(rooms.filter((r) => r.status === 'AVAILABLE' && r.roomTypeId === this.selectedRoomTypeId));
        this.checkedAvailability.set(true);
      },
      error: () => this.notification.error('Failed to check room availability.')
    });
  }

  /** A room number can span several beds - narrow to just this room number's beds, and auto-pick when there's only one. */
  onRoomNumberChange(): void {
    const beds = this.availableRooms().filter((room) => room.roomNumber === this.selectedRoomNumber);
    this.bedsForSelectedRoomNumber.set(beds);
    this.selectedRoomId = beds.length === 1 ? beds[0].id : null;
  }

  submit(): void {
    const admission = this.admission();
    if (!admission || admission.id === null || !this.selectedRoomId) {
      return;
    }
    this.submitting.set(true);
    this.admissionService.admitRegistered(admission.id, { patientId: admission.patientId, roomId: this.selectedRoomId, ...this.form }).subscribe({
      next: (admitted) => {
        this.submitting.set(false);
        this.router.navigate(['/ip/admissions', admitted.id, 'confirmation']);
      },
      error: (err) => {
        this.submitting.set(false);
        this.notification.error(err.error?.message ?? 'Failed to admit patient.');
      }
    });
  }
}

import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router } from '@angular/router';
import { NotificationService } from '../../../shared/services/notification.service';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge/status-badge.component';
import { Patient } from '../../registration/patients/patient.model';
import { PatientService } from '../../registration/patients/patient.service';
import { Room } from '../rooms/room.model';
import { RoomService } from '../rooms/room.service';
import { RoomType } from '../rooms/room-type.model';
import { RoomTypeService } from '../rooms/room-type.service';
import { Admission, AdmissionAdmitInput, AdmissionPaymentType } from './admission.model';
import { AdmissionService } from './admission.service';
import { WardRoomPickerDialogComponent } from './ward-room-picker-dialog.component';

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
    MatButtonModule,
    MatCheckboxModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    PageHeaderComponent,
    StatusBadgeComponent
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
  private readonly dialog = inject(MatDialog);

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
  loadingRooms = signal(false);
  loading = signal(true);
  submitting = signal(false);
  notPending = signal(false);

  /** Collapsible-card open state for the three review sections - all open by default so nothing required is hidden on first view. */
  attenderPanelExpanded = signal(true);
  clinicalPanelExpanded = signal(true);
  insurancePanelExpanded = signal(true);

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
          advanceAmount: admission.advanceAmount ?? 0
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
        // Ward type is already known from registration - load its rooms straight away instead
        // of waiting for the user to re-select it.
        if (admission.roomTypeId) {
          this.fetchAvailableRooms(admission.roomTypeId);
        }
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load admission.');
      }
    });
  }

  /** Ward Type -> Room No cascade: fires automatically on selection, no separate "load" step needed. */
  onWardTypeChange(): void {
    this.selectedRoomNumber = null;
    this.selectedRoomId = null;
    this.bedsForSelectedRoomNumber.set([]);
    this.availableRooms.set([]);
    this.checkedAvailability.set(false);
    if (!this.selectedRoomTypeId) {
      return;
    }
    this.fetchAvailableRooms(this.selectedRoomTypeId);
  }

  /** A room number can span several beds - narrow to just this room number's beds, and auto-pick when there's only one. */
  onRoomNumberChange(): void {
    const beds = this.availableRooms().filter((room) => room.roomNumber === this.selectedRoomNumber);
    this.bedsForSelectedRoomNumber.set(beds);
    this.selectedRoomId = beds.length === 1 ? beds[0].id : null;
  }

  /** Opens the bed-level availability picker (occupied/maintenance beds shown, not just available ones) so staff can see the full ward before choosing - separate from the automatic Ward Type -> Room No cascade above. */
  openAvailabilityPicker(): void {
    const dialogRef = this.dialog.open(WardRoomPickerDialogComponent, {
      width: '760px',
      maxWidth: '95vw',
      autoFocus: false,
      data: { roomTypeId: this.selectedRoomTypeId, roomTypes: this.roomTypes() }
    });
    dialogRef.afterClosed().subscribe((room?: Room) => {
      if (!room || room.id === null) {
        return;
      }
      this.selectedRoomTypeId = room.roomTypeId;
      this.fetchAvailableRooms(room.roomTypeId, () => {
        this.selectedRoomNumber = room.roomNumber;
        this.onRoomNumberChange();
        this.selectedRoomId = room.id;
      });
    });
  }

  private fetchAvailableRooms(roomTypeId: number, onLoaded?: () => void): void {
    this.loadingRooms.set(true);
    this.roomService.list().subscribe({
      next: (rooms) => {
        this.availableRooms.set(rooms.filter((r) => r.status === 'AVAILABLE' && r.roomTypeId === roomTypeId));
        this.checkedAvailability.set(true);
        this.loadingRooms.set(false);
        onLoaded?.();
      },
      error: () => {
        this.loadingRooms.set(false);
        this.notification.error('Failed to check room availability.');
      }
    });
  }

  submit(formRef: NgForm): void {
    const admission = this.admission();
    if (!admission || admission.id === null) {
      return;
    }
    if (formRef.invalid || !this.selectedRoomTypeId || !this.selectedRoomNumber || !this.selectedRoomId) {
      this.notification.error('Please select a Ward Type, Room No and Bed No before admitting the patient.');
      return;
    }
    this.submitting.set(true);
    this.admissionService.admitRegistered(admission.id, { patientId: admission.patientId, roomId: this.selectedRoomId, ...this.form }).subscribe({
      next: (admitted) => {
        this.submitting.set(false);
        this.notification.success(
          `${admitted.patientName} (${admitted.admissionNumber}) has been admitted as an In-Patient in ${admitted.roomTypeName} - ${admitted.roomNumber}. Status updated to Admitted.`
        );
        this.router.navigate(['/ip/admissions']);
      },
      error: (err) => {
        this.submitting.set(false);
        this.notification.error(err.error?.message ?? 'Failed to admit patient.');
      }
    });
  }
}

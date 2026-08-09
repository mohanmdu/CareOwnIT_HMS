import { Component, ElementRef, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router } from '@angular/router';
import { NotificationService } from '../../../shared/services/notification.service';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { Consultant } from '../../masters-admin/consultants/consultant.model';
import { ConsultantService } from '../../masters-admin/consultants/consultant.service';
import { Patient } from '../../registration/patients/patient.model';
import { PatientService } from '../../registration/patients/patient.service';
import { RoomType } from '../rooms/room-type.model';
import { RoomTypeService } from '../rooms/room-type.service';
import { Admission, AdmissionPaymentType, AdmissionRegistrationInput } from './admission.model';
import { AdmissionService } from './admission.service';

const MARITAL_STATUS_OPTIONS = ['Single', 'Married', 'Divorced', 'Widowed'];
const DESCRIPTION_OF_CASE_OPTIONS = ['Non-Surgery', 'Surgery'];
const INSURANCE_TYPE_OPTIONS = ['None', 'Direct Insurance', 'Private TPA', 'Govt Insurance'];
const INSURANCE_COMPANY_OPTIONS = [
  'VIPUL MEDCORP TPA PVT LTD.',
  'ERICTION INSURANCE TPA PVT LTD.',
  'STAR HEALTH AND ALLIED INSURANCE CO.LTD.',
  'TAMILNADU GOVERNMENT EMPLOYEES SCHEME'
];
const PATIENT_TYPE_OPTIONS = ['Normal', 'Senior Citizen', 'VIP'];
const PAYMENT_TYPE_OPTIONS: { value: AdmissionPaymentType; label: string }[] = [
  { value: 'CASH', label: 'Cash' },
  { value: 'INSURANCE', label: 'Insurance' },
  { value: 'CORPORATE', label: 'Corporate' }
];

function nowAsDatetimeLocal(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
}

/** ISO instant/local-datetime string -> datetime-local input value (truncates seconds/timezone). */
function toDatetimeLocal(value: string | null): string {
  return value ? value.slice(0, 16) : nowAsDatetimeLocal();
}

function toFormFromAdmission(admission: Admission): Omit<AdmissionRegistrationInput, 'patientId'> {
  return {
    admissionDate: toDatetimeLocal(admission.admissionDate),
    roomTypeId: admission.roomTypeId,
    attenderName: admission.attenderName,
    relationType: admission.relationType,
    fatherSpouseName: admission.fatherSpouseName,
    relationMobileNo: admission.relationMobileNo,
    occupation: admission.occupation,
    maritalStatus: admission.maritalStatus,
    periodOfStayDays: admission.periodOfStayDays,
    descriptionOfCase: admission.descriptionOfCase ?? 'Non-Surgery',
    referralDoctor: admission.referralDoctor,
    primaryConsultant: admission.primaryConsultant,
    secondaryConsultant: admission.secondaryConsultant,
    paymentType: admission.paymentType ?? 'CASH',
    heightCm: admission.heightCm,
    weightKg: admission.weightKg,
    mlc: admission.mlc,
    insuranceType: admission.insuranceType ?? 'None',
    corporateName: admission.corporateName,
    tpaName: admission.tpaName,
    insuranceCompany: admission.insuranceCompany,
    patientType: admission.patientType ?? 'Normal',
    remarks: admission.remarks,
    aadhaarNumber: admission.aadhaarNumber,
    ventilatorRequired: admission.ventilatorRequired,
    monitorRequired: admission.monitorRequired
  };
}

const EMPTY_FORM: Omit<AdmissionRegistrationInput, 'patientId'> = {
  admissionDate: '',
  roomTypeId: null,
  attenderName: null,
  relationType: null,
  fatherSpouseName: null,
  relationMobileNo: null,
  occupation: null,
  maritalStatus: null,
  periodOfStayDays: null,
  descriptionOfCase: 'Non-Surgery',
  referralDoctor: null,
  primaryConsultant: null,
  secondaryConsultant: null,
  paymentType: 'CASH',
  heightCm: null,
  weightKg: null,
  mlc: false,
  insuranceType: 'None',
  corporateName: null,
  tpaName: null,
  insuranceCompany: null,
  patientType: 'Normal',
  remarks: null,
  aadhaarNumber: null,
  ventilatorRequired: false,
  monitorRequired: false
};

/** IP Admission Advice (PDF p.3): register a new admission for the patient selected on the search screen. */
@Component({
  selector: 'app-admission-registration-form',
  standalone: true,
  imports: [
    FormsModule,
    MatAutocompleteModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatProgressBarModule,
    PageHeaderComponent
  ],
  templateUrl: './admission-registration-form.component.html',
  styleUrl: './admission-registration-form.component.scss'
})
export class AdmissionRegistrationFormComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly patientService = inject(PatientService);
  private readonly roomTypeService = inject(RoomTypeService);
  private readonly consultantService = inject(ConsultantService);
  private readonly admissionService = inject(AdmissionService);
  private readonly notification = inject(NotificationService);
  private readonly elementRef: ElementRef<HTMLElement> = inject(ElementRef);

  readonly maritalStatusOptions = MARITAL_STATUS_OPTIONS;
  readonly descriptionOfCaseOptions = DESCRIPTION_OF_CASE_OPTIONS;
  readonly insuranceTypeOptions = INSURANCE_TYPE_OPTIONS;
  readonly insuranceCompanyOptions = INSURANCE_COMPANY_OPTIONS;
  readonly patientTypeOptions = PATIENT_TYPE_OPTIONS;
  readonly paymentTypeOptions = PAYMENT_TYPE_OPTIONS;

  patient = signal<Patient | null>(null);
  roomTypes = signal<RoomType[]>([]);
  consultants = signal<Consultant[]>([]);
  filteredPrimaryConsultants = signal<Consultant[]>([]);
  filteredSecondaryConsultants = signal<Consultant[]>([]);
  loading = signal(true);
  submitting = signal(false);

  photoPreviewUrl = signal<string | null>(null);
  private photoFile: File | null = null;

  /** Collapsible-card open state - forced back open (and scrolled to) when a section holds an invalid field on submit. */
  attenderPanelExpanded = signal(true);
  clinicalPanelExpanded = signal(true);
  roomPanelExpanded = signal(true);

  form: Omit<AdmissionRegistrationInput, 'patientId'> = { ...EMPTY_FORM, admissionDate: nowAsDatetimeLocal() };

  get showInsuranceFields(): boolean {
    return this.form.insuranceType !== 'None';
  }

  get showInsuranceCompany(): boolean {
    return this.form.insuranceType === 'Private TPA' || this.form.insuranceType === 'Govt Insurance';
  }

  private readonly editingAdmissionId = this.route.snapshot.paramMap.has('id')
    ? Number(this.route.snapshot.paramMap.get('id'))
    : null;
  readonly isEditMode = this.editingAdmissionId !== null;

  constructor() {
    if (this.editingAdmissionId !== null) {
      this.admissionService.get(this.editingAdmissionId).subscribe({
        next: (admission) => {
          this.form = toFormFromAdmission(admission);
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
          this.notification.error('Failed to load the admission.');
        }
      });
    } else {
      const patientId = Number(this.route.snapshot.paramMap.get('patientId'));
      this.patientService.get(patientId).subscribe({
        next: (patient) => {
          this.patient.set(patient);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.notification.error('Failed to load patient details.');
        }
      });
    }
    this.roomTypeService.list().subscribe({ next: (types) => this.roomTypes.set(types) });
    this.consultantService.list().subscribe({
      next: (consultants) => {
        this.consultants.set(consultants);
        this.filteredPrimaryConsultants.set(consultants);
        this.filteredSecondaryConsultants.set(consultants);
      }
    });
  }

  onRelationMobileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.form.relationMobileNo = input.value.replace(/\D/g, '').slice(0, 10);
  }

  get isRelationMobileValid(): boolean {
    return !!this.form.relationMobileNo && /^\d{10}$/.test(this.form.relationMobileNo);
  }

  onAadhaarInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.form.aadhaarNumber = input.value.replace(/\D/g, '').slice(0, 12);
  }

  filterPrimaryConsultants(event: Event): void {
    this.filteredPrimaryConsultants.set(this.filterConsultants((event.target as HTMLInputElement).value));
  }

  filterSecondaryConsultants(event: Event): void {
    this.filteredSecondaryConsultants.set(this.filterConsultants((event.target as HTMLInputElement).value));
  }

  private filterConsultants(query: string): Consultant[] {
    const q = query.trim().toLowerCase();
    return q ? this.consultants().filter((c) => c.name.toLowerCase().includes(q)) : this.consultants();
  }

  onPhotoSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) {
      return;
    }
    this.photoFile = file;
    const reader = new FileReader();
    reader.onload = () => this.photoPreviewUrl.set(reader.result as string);
    reader.readAsDataURL(file);
  }

  submit(formRef: NgForm): void {
    if (formRef.invalid || !this.isRelationMobileValid) {
      this.attenderPanelExpanded.set(true);
      this.clinicalPanelExpanded.set(true);
      this.roomPanelExpanded.set(true);
      this.notification.error('Please fix highlighted errors before submitting.');
      setTimeout(() => this.scrollToFirstError(), 150);
      return;
    }
    const patient = this.patient();
    if (!patient || patient.id === null) {
      return;
    }
    this.submitting.set(true);
    if (this.editingAdmissionId !== null) {
      this.admissionService.updateIntake(this.editingAdmissionId, { patientId: patient.id, ...this.form }).subscribe({
        next: (admission) => {
          this.submitting.set(false);
          this.notification.success('Inpatient Admission Details updated.');
          this.router.navigate(['/ip/admissions', admission.id, 'billing']);
        },
        error: (err) => {
          this.submitting.set(false);
          this.notification.error(err.error?.message ?? 'Failed to update the admission.');
        }
      });
      return;
    }
    this.admissionService.register({ patientId: patient.id, ...this.form }).subscribe({
      next: (admission) => {
        const registeredMessage = `The Patient ${admission.patientName} has been registered with the IPID ${admission.admissionNumber} successfully.`;
        if (!this.photoFile || admission.id === null) {
          this.submitting.set(false);
          this.notification.success(registeredMessage);
          this.router.navigate(['/ip/admissions']);
          return;
        }
        this.admissionService.uploadPhoto(admission.id, this.photoFile).subscribe({
          next: () => {
            this.submitting.set(false);
            this.notification.success(registeredMessage);
            this.router.navigate(['/ip/admissions']);
          },
          error: () => {
            this.submitting.set(false);
            this.notification.error('Registered, but the photo upload failed.');
            this.router.navigate(['/ip/admissions']);
          }
        });
      },
      error: (err) => {
        this.submitting.set(false);
        this.notification.error(err.error?.message ?? 'Failed to register the admission.');
      }
    });
  }

  /** Scrolls/focuses the first invalid control (Angular stamps .ng-invalid on it directly) after an expansion panel forced back open has had a moment to lay out. */
  private scrollToFirstError(): void {
    const invalidField = this.elementRef.nativeElement.querySelector<HTMLElement>('.ng-invalid[name]');
    invalidField?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    invalidField?.focus({ preventScroll: true });
  }
}

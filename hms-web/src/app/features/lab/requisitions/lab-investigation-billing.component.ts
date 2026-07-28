import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { NotificationService } from '../../../shared/services/notification.service';
import { Consultant } from '../../masters-admin/consultants/consultant.model';
import { ConsultantService } from '../../masters-admin/consultants/consultant.service';
import { OpBillingCategory } from '../../masters-admin/op-billing-categories/op-billing-category.model';
import { OpBillingCategoryService } from '../../masters-admin/op-billing-categories/op-billing-category.service';
import { OpBillingComponent as OpBillingComponentModel } from '../../masters-admin/op-billing-components/op-billing-component.model';
import { OpBillingComponentService } from '../../masters-admin/op-billing-components/op-billing-component.service';
import { PatientVisitSummaryService } from '../../icd/patient-visit-summary.service';
import { Patient } from '../../registration/patients/patient.model';
import { PatientService } from '../../registration/patients/patient.service';
import { LabBillingType } from './lab-requisition.model';
import { LabRequisitionService } from './lab-requisition.service';

interface BillingAdviceLine {
  componentId: number;
  categoryName: string;
  componentName: string;
  amount: number;
  quantity: number;
  discount: number;
  netAmount: number;
}

/**
 * Patient Billing Advice (Investigations flow, screen 2 of 6): unlike the
 * Lab Requisition Form's fixed test catalog, this builds an ad-hoc list of
 * line items sourced from the existing OP Billing Catalog
 * (OpBillingCategory/OpBillingComponent) - reused as-is per "no module
 * create" rather than a new Investigations-specific master. Submits via the
 * same LabRequisitionService.create() as the Lab flow, just with adHocItems
 * instead of subCategoryIds, and lands on the same reused Success screen.
 */
@Component({
  selector: 'app-lab-investigation-billing',
  standalone: true,
  imports: [DecimalPipe, FormsModule, MatAutocompleteModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  templateUrl: './lab-investigation-billing.component.html',
  styleUrl: './lab-investigation-billing.component.scss'
})
export class LabInvestigationBillingComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly patientService = inject(PatientService);
  private readonly visitSummaryService = inject(PatientVisitSummaryService);
  private readonly consultantService = inject(ConsultantService);
  private readonly categoryService = inject(OpBillingCategoryService);
  private readonly componentService = inject(OpBillingComponentService);
  private readonly service = inject(LabRequisitionService);
  private readonly notification = inject(NotificationService);
  private readonly consultantSearchTerms = new Subject<string>();
  private readonly componentSearchTerms = new Subject<string>();

  private readonly patientId = Number(this.route.snapshot.paramMap.get('patientId'));

  loading = signal(true);
  saving = signal(false);
  patient = signal<Patient | null>(null);
  patientType = signal<'OP' | 'IP'>('OP');
  categories = signal<OpBillingCategory[]>([]);

  billingType: LabBillingType = 'CASH';

  consultantQuery = signal('');
  consultantResults = signal<Consultant[]>([]);
  selectedConsultant = signal<Consultant | null>(null);

  selectedCategoryId = signal<number | null>(null);
  componentQuery = signal('');
  componentResults = signal<OpBillingComponentModel[]>([]);
  selectedComponent = signal<OpBillingComponentModel | null>(null);
  quantity = signal(1);
  discount = signal(0);

  lineItems = signal<BillingAdviceLine[]>([]);

  currentNetAmount = computed(() => {
    const component = this.selectedComponent();
    if (!component) {
      return 0;
    }
    return component.amount * this.quantity() - this.discount();
  });

  totalAmount = computed(() => this.lineItems().reduce((sum, item) => sum + item.netAmount, 0));

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

    this.categoryService.list().subscribe({
      next: (categories) => {
        this.categories.set(categories);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load billing categories.');
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

    this.componentSearchTerms
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((query) => this.componentService.search(query, this.selectedCategoryId())),
        takeUntilDestroyed()
      )
      .subscribe((results) => this.componentResults.set(results));
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

  onCategoryChange(categoryId: string): void {
    this.selectedCategoryId.set(categoryId ? Number(categoryId) : null);
    this.componentQuery.set('');
    this.selectedComponent.set(null);
    this.componentResults.set([]);
  }

  onComponentQueryChange(value: string): void {
    this.componentQuery.set(value);
    this.selectedComponent.set(null);
    if (value.trim().length >= 1) {
      this.componentSearchTerms.next(value.trim());
    } else {
      this.componentResults.set([]);
    }
  }

  selectComponent(component: OpBillingComponentModel): void {
    this.selectedComponent.set(component);
    this.componentQuery.set(component.name);
    this.componentResults.set([]);
    if (this.selectedCategoryId() === null) {
      this.selectedCategoryId.set(component.categoryId);
    }
  }

  get canAddLine(): boolean {
    return this.selectedComponent() !== null && this.quantity() > 0;
  }

  addLine(): void {
    const component = this.selectedComponent();
    if (!component || this.quantity() <= 0) {
      return;
    }
    const category = this.categories().find((c) => c.id === component.categoryId);
    this.lineItems.update((items) => [
      ...items,
      {
        componentId: component.id!,
        categoryName: category?.name ?? component.categoryName ?? '—',
        componentName: component.name,
        amount: component.amount,
        quantity: this.quantity(),
        discount: this.discount(),
        netAmount: this.currentNetAmount()
      }
    ]);
    this.componentQuery.set('');
    this.selectedComponent.set(null);
    this.quantity.set(1);
    this.discount.set(0);
  }

  removeLine(index: number): void {
    this.lineItems.update((items) => items.filter((_, i) => i !== index));
  }

  get isValid(): boolean {
    return this.lineItems().length > 0;
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
        adHocItems: this.lineItems().map((item) => ({
          componentId: item.componentId,
          quantity: item.quantity,
          discount: item.discount
        }))
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.notification.success('Labtest requisition has been initiated. Receive the payment in Labtest billing.');
          this.router.navigate(['/lab/billing']);
        },
        error: (err) => {
          this.saving.set(false);
          this.notification.error(err.error?.message ?? 'Failed to submit the billing advice.');
        }
      });
  }
}

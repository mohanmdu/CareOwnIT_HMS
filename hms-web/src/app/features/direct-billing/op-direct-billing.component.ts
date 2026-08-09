import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { NotificationService } from '../../shared/services/notification.service';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import { PatientSearchComponent } from '../../shared/ui/patient-search/patient-search.component';
import { PAYMENT_MODE_LABELS, PAYMENT_MODES, PaymentMode } from '../appointments/booking/appointment.model';
import { Consultant } from '../masters-admin/consultants/consultant.model';
import { ConsultantService } from '../masters-admin/consultants/consultant.service';
import { OpBillingCategory } from '../masters-admin/op-billing-categories/op-billing-category.model';
import { OpBillingCategoryService } from '../masters-admin/op-billing-categories/op-billing-category.service';
import { OpBillingComponent } from '../masters-admin/op-billing-components/op-billing-component.model';
import { OpBillingComponentService } from '../masters-admin/op-billing-components/op-billing-component.service';
import { Patient } from '../registration/patients/patient.model';
import { OpDirectBillingReceiptDialogComponent } from './op-direct-billing-receipt-dialog.component';
import { OpDirectBillingService } from './op-direct-billing.service';

function emptyNewItem() {
  return { componentId: null as number | null, quantity: 1, amount: 0, remarks: '', consultantId: null as number | null };
}

/** One line added to the running list, not yet submitted - display-ready (names snapshotted at Add-time), mirroring how OpDirectBillingItem itself snapshots categoryName/componentName at billing time on the backend. */
interface DraftItem {
  componentId: number;
  categoryName: string | null;
  componentName: string;
  quantity: number;
  amount: number;
  remarks: string | null;
  consultantId: number | null;
  consultantName: string | null;
}

/**
 * OP Direct Billing workflow (walk-in charges not tied to a doctor
 * appointment): search-then-reveal, same pattern as the booking wizard's
 * patient step - <app-patient-search> is mounted only while no patient is
 * selected, so removing it from the DOM resets its state for free.
 *
 * Items are added one at a time to a local running list (Add), each with its
 * own optional Consultant - separate from the bill-level Consultant in the
 * Payment Details section below, which still applies to the whole bill.
 * Submit posts the entire list at once and bills immediately (no pending/
 * approval step - there's no doctor/slot to approve here).
 */
@Component({
  selector: 'app-op-direct-billing',
  standalone: true,
  imports: [
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    PageHeaderComponent,
    PatientSearchComponent
  ],
  templateUrl: './op-direct-billing.component.html',
  styleUrl: './op-direct-billing.component.scss'
})
export class OpDirectBillingComponent {
  private readonly service = inject(OpDirectBillingService);
  private readonly categoryService = inject(OpBillingCategoryService);
  private readonly componentService = inject(OpBillingComponentService);
  private readonly consultantService = inject(ConsultantService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly paymentModes = PAYMENT_MODES;
  readonly paymentModeLabels = PAYMENT_MODE_LABELS;

  patient = signal<Patient | null>(null);
  categories = signal<OpBillingCategory[]>([]);
  components = signal<OpBillingComponent[]>([]);
  consultants = signal<Consultant[]>([]);
  items = signal<DraftItem[]>([]);
  saving = signal(false);

  newItem = emptyNewItem();
  // A real signal, not a plain newItem.categoryId property - filteredComponents
  // is a computed() and only re-runs when a signal it reads changes; a plain
  // object property mutation is invisible to it, which is exactly why the
  // Component list never updated after picking a Category.
  selectedCategoryId = signal<number | null>(null);
  paymentMode: PaymentMode = 'CASH';
  remarks = '';

  filteredComponents = computed(() => {
    const categoryId = this.selectedCategoryId();
    if (!categoryId) {
      return [];
    }
    return this.components().filter((component) => component.categoryId === categoryId);
  });

  totalAmount = computed(() => this.items().reduce((sum, item) => sum + item.amount, 0));

  constructor() {
    this.categoryService.list().subscribe({
      next: (categories) => this.categories.set(categories),
      error: () => this.notification.error('Failed to load OP Billing Categories.')
    });
    this.componentService.list().subscribe({
      next: (components) => this.components.set(components),
      error: () => this.notification.error('Failed to load OP Billing Components.')
    });
    this.consultantService.list().subscribe({
      next: (consultants) => this.consultants.set(consultants),
      error: () => this.notification.error('Failed to load Consultants.')
    });
  }

  selectPatient(patient: Patient): void {
    this.patient.set(patient);
  }

  onCategoryChange(): void {
    this.newItem.componentId = null;
  }

  onComponentChange(): void {
    const component = this.components().find((c) => c.id === this.newItem.componentId);
    if (component) {
      this.newItem.amount = component.amount * this.newItem.quantity;
    }
  }

  onQuantityChange(): void {
    this.onComponentChange();
  }

  addItem(): void {
    const component = this.components().find((c) => c.id === this.newItem.componentId);
    if (!component || this.newItem.quantity <= 0 || this.newItem.amount <= 0) {
      return;
    }
    const consultant = this.consultants().find((c) => c.id === this.newItem.consultantId) ?? null;
    this.items.update((current) => [
      ...current,
      {
        componentId: component.id!,
        categoryName: component.categoryName,
        componentName: component.name,
        quantity: this.newItem.quantity,
        amount: this.newItem.amount,
        remarks: this.newItem.remarks.trim() || null,
        consultantId: consultant?.id ?? null,
        consultantName: consultant?.name ?? null
      }
    ]);
    // Only the item-entry fields reset - patient, payment mode, bill-level
    // consultant, and bill remarks apply to the whole bill and persist
    // across multiple Add clicks.
    this.newItem = emptyNewItem();
    this.selectedCategoryId.set(null);
  }

  removeItem(index: number): void {
    this.items.update((current) => current.filter((_, i) => i !== index));
  }

  submit(): void {
    const patient = this.patient();
    if (!patient?.id || this.items().length === 0) {
      return;
    }
    this.saving.set(true);
    this.service
      .create({
        patientId: patient.id,
        // Bill-level consultant removed from the UI - each item carries its
        // own optional consultant now (see items() below), which is the
        // more useful/granular signal. Backend field stays nullable/optional.
        consultantId: null,
        items: this.items().map((item) => ({
          componentId: item.componentId,
          quantity: item.quantity,
          amount: item.amount,
          remarks: item.remarks,
          consultantId: item.consultantId
        })),
        paymentMode: this.paymentMode,
        remarks: this.remarks.trim() || null
      })
      .subscribe({
        next: (receipt) => {
          this.saving.set(false);
          this.dialog.open(OpDirectBillingReceiptDialogComponent, {
            width: '640px',
            maxWidth: '95vw',
            data: { receipt }
          });
          this.reset();
        },
        error: (err) => {
          this.saving.set(false);
          this.notification.error(err.error?.message ?? 'Failed to complete billing.');
        }
      });
  }

  reset(): void {
    this.patient.set(null);
    this.newItem = emptyNewItem();
    this.selectedCategoryId.set(null);
    this.items.set([]);
    this.paymentMode = 'CASH';
    this.remarks = '';
  }
}

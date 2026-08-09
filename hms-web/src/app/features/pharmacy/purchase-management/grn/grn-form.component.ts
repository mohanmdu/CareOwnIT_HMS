import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { FormControl, FormGroupDirective, FormsModule, NgForm } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { ErrorStateMatcher } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { NotificationService } from '../../../../shared/services/notification.service';
import { Product } from '../../inventory-master/products/product.model';
import { ProductService } from '../../inventory-master/products/product.service';
import { Supplier } from '../../inventory-master/suppliers/supplier.model';
import { SupplierService } from '../../inventory-master/suppliers/supplier.service';
import { PURCHASE_TYPES, PURCHASE_TYPE_LABELS, PurchaseType, GrnWorkingItem } from './grn.model';
import { GrnService } from './grn.service';

function emptyNewItem() {
  return {
    productId: null as number | null,
    productSearch: '',
    productTypeName: null as string | null,
    packing: 1,
    qty: 1,
    totalQty: 1,
    freeQty: 0,
    batch: '',
    expiryDate: null as Date | null,
    manufactureDate: null as Date | null,
    mrp: null as number | null,
    purchaseRate: 0,
    discountPercent: 0,
    hsnSac: '',
    sgstPercent: 0,
    cgstPercent: 0
  };
}

function toIsoDate(date: Date | null): string | null {
  if (!date) {
    return null;
  }
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

type ExpiryStatus = 'expired' | 'expiring-soon' | 'fresh';

/** How far out "expiring soon" reaches - a common near-expiry alert window for pharmacy stock. */
const EXPIRING_SOON_DAYS = 90;

/**
 * Shows a field as invalid (red outline + <mat-error>) once the user has
 * either touched it directly or attempted to submit the section it belongs
 * to (add-item row vs. header) - mirrors NgForm's own default matcher
 * (invalid && (touched || form.submitted)), but this form has no native
 * <form>/(ngSubmit) to drive `submitted` since Save as Draft/Approve are two
 * independent buttons sharing one dataset. `extraInvalid` layers in
 * business-rule checks (e.g. MRP >= Purchase Rate, drug must resolve to a
 * real product) that plain HTML validators (required/min) can't express, so
 * they still drive Material's real error UI instead of a hand-rolled one.
 */
class AttemptedErrorStateMatcher implements ErrorStateMatcher {
  constructor(
    private readonly attempted: () => boolean,
    private readonly extraInvalid: () => boolean = () => false
  ) {}

  isErrorState(control: FormControl | null, _form: FormGroupDirective | NgForm | null): boolean {
    const invalid = (!!control && control.invalid) || this.extraInvalid();
    return invalid && !!(control?.touched || this.attempted());
  }
}

/**
 * GRN entry form - Supplier + header fields, a running item table (Product
 * autocomplete auto-fills Drug Type/HSN/SGST%/CGST% from Product Master,
 * still editable per row for products with no GST configured), computed Net
 * Value per row + totals footer, Invoice/GRN Amount read-only (= totals
 * sum). `readonly` suppresses the entry row and action buttons for viewing
 * a past GRN from the GRN List tab.
 */
@Component({
  selector: 'app-grn-form',
  standalone: true,
  imports: [
    DecimalPipe,
    FormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule
  ],
  templateUrl: './grn-form.component.html',
  styleUrl: './grn-form.component.scss'
})
export class GrnFormComponent {
  private readonly service = inject(GrnService);
  private readonly supplierService = inject(SupplierService);
  private readonly productService = inject(ProductService);
  private readonly notification = inject(NotificationService);

  readonly readonly = input(false);
  /** Set to view/continue-editing an existing (draft) GRN; leave unset for a brand-new one. */
  readonly grnId = input<number | null>(null);
  /** Emitted after a successful create/update (draft save or final approve), so a hosting Draft GRN list knows to refresh. */
  readonly saved = output<void>();

  readonly itemColumns = [
    'productName',
    'productTypeName',
    'packing',
    'qty',
    'totalQty',
    'freeQty',
    'batch',
    'expiryDate',
    'mrp',
    'purchaseRate',
    'discountPercent',
    'hsnSac',
    'sgstPercent',
    'cgstPercent',
    'netValue',
    'remove'
  ];
  readonly purchaseTypes = PURCHASE_TYPES;
  readonly purchaseTypeLabels = PURCHASE_TYPE_LABELS;

  suppliers = signal<Supplier[]>([]);
  products = signal<Product[]>([]);
  items = signal<GrnWorkingItem[]>([]);
  /** Which action is in flight, if any - lets each button show its own "Saving…"/"Approving…" label instead of a shared generic one. */
  savingStatus = signal<'DRAFT' | 'APPROVED' | null>(null);
  loading = signal(false);

  /** Set true on the first Save as Draft/Approve click - drives header field error visibility (see AttemptedErrorStateMatcher). */
  headerAttempted = signal(false);
  /** Set true on each Add Item click; cleared again once an item is successfully added. */
  addItemAttempted = signal(false);

  /** For resetForm() only - clears touched/dirty state after a successful add/save so the next entry doesn't show stale red borders on fields that are only empty because they were just reset. */
  private readonly headerForm = viewChild<NgForm>('headerForm');
  private readonly addItemForm = viewChild<NgForm>('addItemForm');

  readonly headerMatcher = new AttemptedErrorStateMatcher(() => this.headerAttempted());
  readonly addItemMatcher = new AttemptedErrorStateMatcher(() => this.addItemAttempted());
  readonly drugMatcher = new AttemptedErrorStateMatcher(
    () => this.addItemAttempted(),
    () => !this.selectedProduct
  );
  readonly mrpMatcher = new AttemptedErrorStateMatcher(
    () => this.addItemAttempted(),
    () => this.isMrpBelowRate
  );

  supplierId = signal<number | null>(null);
  purchaseType: PurchaseType = 'CREDIT';
  invoiceNo = '';
  invoiceDate: Date | null = null;
  poNumber = '';
  grnDate: Date | null = new Date();
  discountAmount: number | null = 0;
  creditNote = '';
  debitNote = '';
  returnAmount: number | null = 0;
  status: 'DRAFT' | 'APPROVED' = 'DRAFT';

  newItem = emptyNewItem();

  invoiceAmount = computed(() => this.items().reduce((sum, item) => sum + item.netValue, 0));
  totalDiscount = computed(() => this.items().reduce((sum, item) => sum + (item.discountAmount ?? 0), 0));
  totalSgst = computed(() => this.items().reduce((sum, item) => sum + item.sgstAmount, 0));
  totalCgst = computed(() => this.items().reduce((sum, item) => sum + item.cgstAmount, 0));

  constructor() {
    this.supplierService.list().subscribe({
      next: (suppliers) => this.suppliers.set(suppliers),
      error: () => this.notification.error('Failed to load suppliers.')
    });
    this.productService.list().subscribe({
      next: (products) => this.products.set(products),
      error: () => this.notification.error('Failed to load products.')
    });

    effect(() => {
      const id = this.grnId();
      if (id) {
        this.loadForView(id);
      }
    }, { allowSignalWrites: true });
  }

  private loadForView(id: number): void {
    this.loading.set(true);
    this.service.get(id).subscribe({
      next: (grn) => {
        this.supplierId.set(grn.supplierId);
        this.purchaseType = grn.purchaseType;
        this.invoiceNo = grn.invoiceNo;
        this.invoiceDate = new Date(grn.invoiceDate);
        this.poNumber = grn.poNumber ?? '';
        this.grnDate = new Date(grn.grnDate);
        this.discountAmount = grn.discountAmount;
        this.creditNote = grn.creditNote ?? '';
        this.debitNote = grn.debitNote ?? '';
        this.returnAmount = grn.returnAmount;
        this.items.set(
          grn.items.map((item) => ({
            productId: item.productId,
            productName: item.productName,
            productTypeName: item.productTypeName,
            packing: item.packing,
            qty: item.qty,
            totalQty: item.totalQty,
            freeQty: item.freeQty,
            batch: item.batch,
            expiryDate: item.expiryDate,
            manufactureDate: item.manufactureDate,
            mrp: item.mrp,
            purchaseRate: item.purchaseRate,
            discountPercent: item.discountPercent,
            discountAmount: item.discountAmount,
            hsnSac: item.hsnSac,
            sgstPercent: item.sgstPercent,
            sgstAmount: item.sgstAmount,
            cgstPercent: item.cgstPercent,
            cgstAmount: item.cgstAmount,
            netValue: item.netValue
          }))
        );
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load GRN.');
      }
    });
  }

  /**
   * Returns the full list until the user starts typing, not [] - an empty
   * search term isn't "no matches", it's "hasn't searched yet", and showing
   * the "no matching products" empty-state before any typing made it look
   * like Product Master data wasn't being fetched at all.
   */
  filteredProducts(): Product[] {
    const term = this.newItem.productSearch.trim().toLowerCase();
    if (!term) {
      return this.products();
    }
    return this.products().filter((product) => product.name.toLowerCase().includes(term));
  }

  onProductSelected(product: Product): void {
    this.newItem.productId = product.id;
    this.newItem.productSearch = product.name;
    this.newItem.productTypeName = product.productTypeName;
    this.newItem.hsnSac = product.hsnSac ?? '';
    this.newItem.sgstPercent = product.stateGst ?? 0;
    this.newItem.cgstPercent = product.centralGst ?? 0;
  }

  /**
   * Add is gated on this, not just newItem.productId - see RaisePoComponent's
   * identical getter for the full reasoning (productId only gets set by
   * actually picking a suggestion; this closes the gap where further typing
   * after a selection would otherwise leave a stale productId in place).
   */
  get selectedProduct(): Product | null {
    const product = this.products().find((p) => p.id === this.newItem.productId);
    return product && product.name === this.newItem.productSearch ? product : null;
  }

  get isMrpBelowRate(): boolean {
    const { mrp, purchaseRate } = this.newItem;
    return mrp !== null && mrp > 0 && purchaseRate > 0 && mrp < purchaseRate;
  }

  /** Full "+ Add Item" validation - every check from the spec's line-item list, plus the backend's own positive-integer constraints on packing/qty/totalQty so a bad row never reaches the server at all. */
  private get hasAddItemErrors(): boolean {
    const item = this.newItem;
    const mrpValid = item.mrp !== null && item.mrp > 0;
    return (
      !this.selectedProduct ||
      !item.batch.trim() ||
      !item.expiryDate ||
      !(item.packing > 0) ||
      !(item.qty > 0) ||
      !(item.totalQty > 0) ||
      item.freeQty < 0 ||
      !(item.purchaseRate > 0) ||
      !mrpValid ||
      this.isMrpBelowRate ||
      !item.hsnSac.trim() ||
      item.sgstPercent < 0 ||
      item.sgstPercent > 100 ||
      item.cgstPercent < 0 ||
      item.cgstPercent > 100
    );
  }

  expiryStatus(expiryDate: string | null): ExpiryStatus | null {
    if (!expiryDate) {
      return null;
    }
    const days = (new Date(expiryDate).getTime() - Date.now()) / 86_400_000;
    if (days < 0) {
      return 'expired';
    }
    return days <= EXPIRING_SOON_DAYS ? 'expiring-soon' : 'fresh';
  }

  private computeNetValue(
    purchaseRate: number,
    totalQty: number,
    discountPercent: number,
    sgstPercent: number,
    cgstPercent: number
  ): { discountAmount: number; sgstAmount: number; cgstAmount: number; netValue: number } {
    const gross = purchaseRate * totalQty;
    const discountAmount = (gross * discountPercent) / 100;
    const taxable = gross - discountAmount;
    const sgstAmount = (taxable * sgstPercent) / 100;
    const cgstAmount = (taxable * cgstPercent) / 100;
    return { discountAmount, sgstAmount, cgstAmount, netValue: taxable + sgstAmount + cgstAmount };
  }

  addItem(): void {
    this.addItemAttempted.set(true);
    if (this.hasAddItemErrors) {
      this.notification.error('Please fix the highlighted fields before adding this item.');
      return;
    }
    const product = this.selectedProduct!;
    const computedValues = this.computeNetValue(
      this.newItem.purchaseRate,
      this.newItem.totalQty,
      this.newItem.discountPercent,
      this.newItem.sgstPercent,
      this.newItem.cgstPercent
    );
    this.items.set([
      ...this.items(),
      {
        productId: product.id!,
        productName: product.name,
        productTypeName: this.newItem.productTypeName,
        packing: this.newItem.packing,
        qty: this.newItem.qty,
        totalQty: this.newItem.totalQty,
        freeQty: this.newItem.freeQty,
        batch: this.newItem.batch.trim() || null,
        expiryDate: toIsoDate(this.newItem.expiryDate),
        manufactureDate: toIsoDate(this.newItem.manufactureDate),
        mrp: this.newItem.mrp,
        purchaseRate: this.newItem.purchaseRate,
        discountPercent: this.newItem.discountPercent,
        discountAmount: computedValues.discountAmount,
        hsnSac: this.newItem.hsnSac.trim() || null,
        sgstPercent: this.newItem.sgstPercent,
        sgstAmount: computedValues.sgstAmount,
        cgstPercent: this.newItem.cgstPercent,
        cgstAmount: computedValues.cgstAmount,
        netValue: computedValues.netValue
      }
    ]);
    this.newItem = emptyNewItem();
    // resetForm(value) - not the no-arg form - clears each control's
    // touched/dirty state AND its displayed value in one atomic operation.
    // resetForm() alone left stale values on screen: NgForm resets each
    // control straight through its ControlValueAccessor, bypassing the
    // template's own [ngModel]="newItem.x" change-detection cache, so a
    // *separate* `newItem = emptyNewItem()` write right after it was not
    // reliably picked back up as a "changed" binding on the next tick.
    this.addItemForm()?.resetForm(this.newItemFormValue(this.newItem));
    this.addItemAttempted.set(false);
    this.notification.success(`${product.name} added to the GRN.`);
  }

  /** Flat {controlName: value} map matching the add-item <form>'s name="…" attributes, for NgForm.resetForm(value). */
  private newItemFormValue(item: ReturnType<typeof emptyNewItem>) {
    return {
      productSearch: item.productSearch,
      productTypeDisplay: item.productTypeName,
      packing: item.packing,
      qty: item.qty,
      totalQty: item.totalQty,
      freeQty: item.freeQty,
      batch: item.batch,
      expiryDate: item.expiryDate,
      manufactureDate: item.manufactureDate,
      mrp: item.mrp,
      purchaseRate: item.purchaseRate,
      discountPercent: item.discountPercent,
      hsnSac: item.hsnSac,
      sgstPercent: item.sgstPercent,
      cgstPercent: item.cgstPercent
    };
  }

  removeItem(index: number): void {
    this.items.set(this.items().filter((_, i) => i !== index));
  }

  private isHeaderValid(): boolean {
    return !!this.supplierId() && !!this.purchaseType && !!this.invoiceNo.trim() && !!this.invoiceDate && !!this.grnDate;
  }

  private submit(status: 'DRAFT' | 'APPROVED'): void {
    this.headerAttempted.set(true);
    if (this.items().length === 0) {
      this.notification.error('Add at least one line item before saving the GRN.');
      return;
    }
    if (!this.isHeaderValid()) {
      this.notification.error('Please fix the highlighted header fields before saving.');
      return;
    }
    const supplierId = this.supplierId()!;
    this.savingStatus.set(status);
    const request = {
      supplierId,
      purchaseType: this.purchaseType,
      invoiceNo: this.invoiceNo.trim(),
      invoiceDate: toIsoDate(this.invoiceDate)!,
      poNumber: this.poNumber.trim() || null,
      grnDate: toIsoDate(this.grnDate)!,
      discountAmount: this.discountAmount,
      creditNote: this.creditNote.trim() || null,
      debitNote: this.debitNote.trim() || null,
      returnAmount: this.returnAmount,
      items: this.items().map((item) => ({
        productId: item.productId,
        packing: item.packing,
        qty: item.qty,
        totalQty: item.totalQty,
        freeQty: item.freeQty,
        batch: item.batch,
        expiryDate: item.expiryDate,
        manufactureDate: item.manufactureDate,
        mrp: item.mrp,
        purchaseRate: item.purchaseRate,
        discountPercent: item.discountPercent,
        discountAmount: item.discountAmount,
        hsnSac: item.hsnSac,
        sgstPercent: item.sgstPercent,
        cgstPercent: item.cgstPercent
      })),
      status
    };

    const existingId = this.grnId();
    const request$ = existingId ? this.service.update(existingId, request) : this.service.create(request);
    request$.subscribe({
      next: () => {
        this.savingStatus.set(null);
        this.notification.success(status === 'APPROVED' ? 'GRN approved.' : 'GRN saved as draft.');
        this.reset();
        this.saved.emit();
      },
      error: (err) => {
        this.savingStatus.set(null);
        this.notification.error(err.error?.message ?? 'Failed to save GRN.');
      }
    });
  }

  approve(): void {
    this.submit('APPROVED');
  }

  saveAsDraft(): void {
    this.submit('DRAFT');
  }

  reset(): void {
    this.supplierId.set(null);
    this.items.set([]);
    this.newItem = emptyNewItem();
    this.purchaseType = 'CREDIT';
    this.invoiceNo = '';
    this.invoiceDate = null;
    this.poNumber = '';
    this.grnDate = new Date();
    this.discountAmount = 0;
    this.creditNote = '';
    this.debitNote = '';
    this.returnAmount = 0;
    this.headerAttempted.set(false);
    this.addItemAttempted.set(false);
    // resetForm(value) - see addItem()'s identical note on why the no-arg
    // form isn't reliable here.
    this.headerForm()?.resetForm({
      supplierId: null,
      purchaseType: this.purchaseType,
      invoiceNo: this.invoiceNo,
      invoiceDate: this.invoiceDate,
      poNumber: this.poNumber,
      grnDate: this.grnDate,
      invoiceAmountDisplay: 0,
      grnAmountDisplay: 0,
      discountAmount: this.discountAmount,
      creditNote: this.creditNote,
      debitNote: this.debitNote,
      returnAmount: this.returnAmount
    });
    this.addItemForm()?.resetForm(this.newItemFormValue(this.newItem));
  }
}

import { DatePipe } from '@angular/common';
import { Component, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { NotificationService } from '../../../shared/services/notification.service';
import { ClinicSettings } from '../../masters-admin/clinic-settings/clinic-settings.model';
import { ClinicSettingsService } from '../../masters-admin/clinic-settings/clinic-settings.service';
import { OpCaseSheet } from './op-case-sheet.model';
import { OP_CASE_SHEET_PRINT_STYLES } from './op-case-sheet-print-styles';
import { OpCaseSheetService } from './op-case-sheet.service';

/** Exactly one of appointmentId/directBillingId is set - matches PrescriptionWorklistEntry's own two-source shape. */
export interface OpCaseSheetViewDialogData {
  appointmentId: number | null;
  directBillingId: number | null;
}

/** Read-only, printable rendering of a saved OP Case Sheet - the "View Details" action in the Patient Prescription worklist. */
@Component({
  selector: 'app-op-case-sheet-view-dialog',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatDialogModule, MatIconModule, MatProgressBarModule, MatTableModule],
  templateUrl: './op-case-sheet-view-dialog.component.html',
  styleUrl: './op-case-sheet-view-dialog.component.scss'
})
export class OpCaseSheetViewDialogComponent implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<OpCaseSheetViewDialogComponent>);
  private readonly data = inject<OpCaseSheetViewDialogData>(MAT_DIALOG_DATA);
  private readonly service = inject(OpCaseSheetService);
  private readonly clinicSettingsService = inject(ClinicSettingsService);
  private readonly notification = inject(NotificationService);

  @ViewChild('caseSheetContent') caseSheetContent?: ElementRef<HTMLElement>;

  readonly itemColumns = ['drugName', 'qty', 'intake', 'morningDose', 'afternoonDose', 'eveningDose', 'nightDose'];

  loading = signal(true);
  caseSheet = signal<OpCaseSheet | null>(null);
  clinicSettings = signal<ClinicSettings | null>(null);

  ngOnInit(): void {
    this.clinicSettingsService.get().subscribe({
      next: (settings) => this.clinicSettings.set(settings),
      error: () => {}
    });
    const load = this.data.appointmentId !== null
      ? this.service.getByAppointment(this.data.appointmentId)
      : this.service.getByDirectBilling(this.data.directBillingId!);
    load.subscribe({
      next: (caseSheet) => {
        this.caseSheet.set(caseSheet);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load the case sheet.');
      }
    });
  }

  print(): void {
    const content = this.caseSheetContent?.nativeElement.innerHTML;
    if (!content) {
      return;
    }
    const printWindow = window.open('', '_blank', 'width=900,height=900');
    if (!printWindow) {
      this.notification.error('Please allow pop-ups to print the case sheet.');
      return;
    }
    printWindow.document.write(`
      <html>
        <head>
          <title>OP Case Sheet</title>
          <style>${OP_CASE_SHEET_PRINT_STYLES}</style>
        </head>
        <body>${content}</body>
      </html>
    `);
    printWindow.document.close();
    printWindow.focus();
    printWindow.onload = () => printWindow.print();
  }

  close(): void {
    this.dialogRef.close();
  }
}

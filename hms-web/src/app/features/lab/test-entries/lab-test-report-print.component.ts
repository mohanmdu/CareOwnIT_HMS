import { DatePipe } from '@angular/common';
import { Component, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { NotificationService } from '../../../shared/services/notification.service';
import { ClinicSettings } from '../../masters-admin/clinic-settings/clinic-settings.model';
import { ClinicSettingsService } from '../../masters-admin/clinic-settings/clinic-settings.service';
import { LabTestEntry } from './lab-test-entry.model';
import { LabTestEntryService } from './lab-test-entry.service';

/** Read-only approved lab report sheet (matches the reference's print preview + "Checked by"/"Sign of Technician" footer). */
@Component({
  selector: 'app-lab-test-report-print',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressBarModule],
  templateUrl: './lab-test-report-print.component.html',
  styleUrl: './lab-test-report-print.component.scss'
})
export class LabTestReportPrintComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(LabTestEntryService);
  private readonly clinicSettingsService = inject(ClinicSettingsService);
  private readonly notification = inject(NotificationService);

  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  @ViewChild('reportContent') reportContent?: ElementRef<HTMLElement>;

  loading = signal(true);
  entry = signal<LabTestEntry | null>(null);
  clinicSettings = signal<ClinicSettings | null>(null);

  constructor() {
    this.clinicSettingsService.get().subscribe({ next: (settings) => this.clinicSettings.set(settings), error: () => {} });
    this.service.getById(this.id).subscribe({
      next: (entry) => {
        this.entry.set(entry);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load the lab report.');
      }
    });
  }

  back(): void {
    this.router.navigate(['/lab/test-entries/approved']);
  }

  print(): void {
    const content = this.reportContent?.nativeElement.innerHTML;
    if (!content) {
      return;
    }
    const printWindow = window.open('', '_blank', 'width=900,height=1000');
    if (!printWindow) {
      this.notification.error('Please allow pop-ups to print.');
      return;
    }
    printWindow.document.write(`
      <html>
        <head>
          <title>Lab Report</title>
          <style>
            body { font-family: Arial, Helvetica, sans-serif; color: #1a1a1a; margin: 24px; }
            table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
            th, td { border: 1px solid #999; padding: 6px 8px; text-align: left; }
            th { background: #e65100; color: #fff; }
            .ltp-letterhead { position: relative; min-height: 64px; padding-bottom: 12px; padding-left: 76px; margin-bottom: 8px; border-bottom: 2px solid #ccc; }
            .ltp-logo { position: absolute; top: 0; left: 0; width: 64px; height: 64px; object-fit: contain; }
            .ltp-clinic-info { display: flex; flex-direction: column; font-size: 0.85rem; }
            .ltp-meta { display: flex; justify-content: space-between; font-size: 0.85rem; margin-top: 4px; }
            .ltp-specimen { font-weight: 600; border: 1px solid #999; padding: 4px 8px; width: fit-content; margin-top: 8px; }
            .ltp-group-row td { background: #f0f0f0; font-weight: 700; text-decoration: underline; }
            .ltp-abnormal u { color: #c62828; font-weight: 700; }
            .ltp-remarks { font-size: 0.85rem; }
            .ltp-signoff { display: flex; justify-content: space-between; margin-top: 24px; padding-top: 12px; border-top: 1px solid #999; font-weight: 700; }
          </style>
        </head>
        <body>${content}</body>
      </html>
    `);
    printWindow.document.close();
    printWindow.focus();
    printWindow.onload = () => printWindow.print();
  }
}

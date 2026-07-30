import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatTableModule } from '@angular/material/table';
import { DoctorQueueAuditLog } from '../doctor-queue.model';

export interface DoctorQueueAuditChangesDialogData {
  log: DoctorQueueAuditLog;
}

interface ChangeRow {
  field: string;
  before: string;
  after: string;
}

function parseSnapshot(json: string | null): Record<string, unknown> {
  if (!json) {
    return {};
  }
  try {
    return JSON.parse(json);
  } catch {
    return {};
  }
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return String(value);
}

/** Field-by-field before/after diff for one DoctorQueueAuditLog row - parses the JSON snapshots generically, mirroring AppointmentAuditChangesDialogComponent. */
@Component({
  selector: 'app-doctor-queue-audit-changes-dialog',
  standalone: true,
  imports: [MatButtonModule, MatDialogModule, MatTableModule],
  templateUrl: './doctor-queue-audit-changes-dialog.component.html',
  styleUrl: './doctor-queue-audit-changes-dialog.component.scss'
})
export class DoctorQueueAuditChangesDialogComponent {
  private readonly data = inject<DoctorQueueAuditChangesDialogData>(MAT_DIALOG_DATA);

  readonly log = this.data.log;
  readonly displayedColumns = ['field', 'before', 'after'];
  readonly rows: ChangeRow[] = this.buildRows();

  private buildRows(): ChangeRow[] {
    const before = parseSnapshot(this.log.previousValue);
    const after = parseSnapshot(this.log.newValue);
    const fields = new Set([...Object.keys(before), ...Object.keys(after)]);
    return Array.from(fields).map((field) => ({
      field,
      before: formatValue(before[field]),
      after: formatValue(after[field])
    }));
  }
}

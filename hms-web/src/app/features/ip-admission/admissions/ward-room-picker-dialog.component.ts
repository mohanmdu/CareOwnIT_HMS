import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { NotificationService } from '../../../shared/services/notification.service';
import { RoomType } from '../rooms/room-type.model';
import { Room, RoomStatus } from '../rooms/room.model';
import { RoomService } from '../rooms/room.service';

const STATUS_LABELS: Record<RoomStatus, string> = {
  AVAILABLE: 'Available',
  ALLOCATED: 'Occupied',
  MAINTENANCE: 'Maintenance'
};

export interface WardRoomPickerDialogData {
  roomTypeId: number | null;
  roomTypes: RoomType[];
}

interface RoomGroup {
  roomNumber: string;
  beds: Room[];
}

/**
 * "Check Room Availability" picker (Ward Allocation screen) - a read-only
 * bed-level view of every room under a ward type, color-coded by status, so
 * staff can see occupied/maintenance beds at a glance before choosing.
 * Clicking an AVAILABLE bed selects it (highlighted blue); "Select This Bed"
 * then closes the dialog and hands the Room back to the caller, which fills
 * in Room No/Bed No for them. Reuses RoomService.list() - the same flat "all
 * rooms" endpoint the plain Room No/Bed No selects already filter
 * client-side, so no new API is needed.
 */
@Component({
  selector: 'app-ward-room-picker-dialog',
  standalone: true,
  imports: [MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatProgressBarModule, MatSelectModule],
  templateUrl: './ward-room-picker-dialog.component.html',
  styleUrl: './ward-room-picker-dialog.component.scss'
})
export class WardRoomPickerDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<WardRoomPickerDialogComponent, Room>);
  private readonly roomService = inject(RoomService);
  private readonly notification = inject(NotificationService);
  readonly data = inject<WardRoomPickerDialogData>(MAT_DIALOG_DATA);

  readonly roomTypes = this.data.roomTypes;
  selectedRoomTypeId = signal<number | null>(this.data.roomTypeId);
  selectedRoom = signal<Room | null>(null);

  loading = signal(true);
  private readonly rooms = signal<Room[]>([]);

  roomGroups = computed<RoomGroup[]>(() => {
    const wardTypeId = this.selectedRoomTypeId();
    const filtered = this.rooms().filter((room) => !wardTypeId || room.roomTypeId === wardTypeId);
    const groups = new Map<string, Room[]>();
    for (const room of filtered) {
      const beds = groups.get(room.roomNumber) ?? [];
      beds.push(room);
      groups.set(room.roomNumber, beds);
    }
    return Array.from(groups.entries())
      .map(([roomNumber, beds]) => ({ roomNumber, beds }))
      .sort((a, b) => a.roomNumber.localeCompare(b.roomNumber, undefined, { numeric: true }));
  });

  constructor() {
    this.roomService.list().subscribe({
      next: (rooms) => {
        this.rooms.set(rooms);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load room availability.');
      }
    });
  }

  /** Toggles selection on an AVAILABLE bed - does not close the dialog, so the blue "selected" state is visible before committing via confirmSelection(). */
  pick(room: Room): void {
    if (room.status !== 'AVAILABLE') {
      return;
    }
    this.selectedRoom.update((current) => (current?.id === room.id ? null : room));
  }

  statusLabel(status: RoomStatus): string {
    return STATUS_LABELS[status];
  }

  confirmSelection(): void {
    const room = this.selectedRoom();
    if (room) {
      this.dialogRef.close(room);
    }
  }

  close(): void {
    this.dialogRef.close();
  }
}

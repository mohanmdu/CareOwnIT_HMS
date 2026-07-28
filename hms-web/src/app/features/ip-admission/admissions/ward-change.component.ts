import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { NotificationService } from '../../../shared/services/notification.service';
import { RoomType } from '../rooms/room-type.model';
import { RoomTypeService } from '../rooms/room-type.service';
import { Room } from '../rooms/room.model';
import { RoomService } from '../rooms/room.service';
import { Admission } from './admission.model';
import { AdmissionService } from './admission.service';

function nowForDateTimeLocal(): string {
  const now = new Date();
  now.setSeconds(0, 0);
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return now.toISOString().slice(0, 16);
}

/** Ward Change (Inpatient List "Ward Change" action): a dedicated screen, not a popup, matching the legacy "INPATIENT WARD CHANGE LIST" table. */
@Component({
  selector: 'app-ward-change',
  standalone: true,
  imports: [DatePipe, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule, MatSelectModule],
  templateUrl: './ward-change.component.html',
  styleUrl: './ward-change.component.scss'
})
export class WardChangeComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly admissionService = inject(AdmissionService);
  private readonly roomTypeService = inject(RoomTypeService);
  private readonly roomService = inject(RoomService);
  private readonly notification = inject(NotificationService);

  private readonly admissionId = Number(this.route.snapshot.paramMap.get('id'));

  admission = signal<Admission | null>(null);
  roomTypes = signal<RoomType[]>([]);
  rooms = signal<Room[]>([]);
  loading = signal(true);
  saving = signal(false);

  changedAt = nowForDateTimeLocal();
  roomTypeId: number | null = null;
  roomId: number | null = null;

  availableRooms = computed(() => {
    const typeId = this.roomTypeId;
    return this.rooms()
      .filter((r) => r.status === 'AVAILABLE' && (typeId === null || r.roomTypeId === typeId))
      .sort((a, b) => a.roomNumber.localeCompare(b.roomNumber));
  });

  constructor() {
    forkJoin({
      admission: this.admissionService.get(this.admissionId),
      roomTypes: this.roomTypeService.list(),
      rooms: this.roomService.list()
    }).subscribe({
      next: ({ admission, roomTypes, rooms }) => {
        this.admission.set(admission);
        this.roomTypes.set(roomTypes);
        this.rooms.set(rooms);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load ward change details.');
        this.router.navigate(['/ip/inpatient-list']);
      }
    });
  }

  onRoomTypeChange(): void {
    this.roomId = null;
  }

  save(): void {
    const admission = this.admission();
    if (!admission || admission.id === null || !this.roomId || this.saving()) {
      return;
    }
    const oldWard = admission.roomTypeName && admission.roomNumber ? `${admission.roomTypeName} - ${admission.roomNumber}` : 'Unassigned';
    const newRoom = this.rooms().find((r) => r.id === this.roomId);
    const newWard = newRoom ? `${newRoom.roomTypeName} - ${newRoom.roomNumber}` : '';

    this.saving.set(true);
    this.admissionService.changeRoom(admission.id, this.roomId, this.changedAt).subscribe({
      next: () => {
        this.saving.set(false);
        this.notification.success(`The Patient ${admission.patientName} has been ward changed from ${oldWard} to ${newWard}.`);
        this.router.navigate(['/ip/inpatient-list']);
      },
      error: (err) => {
        this.saving.set(false);
        this.notification.error(err.error?.message ?? 'Failed to change ward.');
      }
    });
  }
}

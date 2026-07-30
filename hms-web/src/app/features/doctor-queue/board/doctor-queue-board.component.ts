import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute } from '@angular/router';
import { EMPTY, Subject } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { DoctorQueueBoardService } from '../doctor-queue-board.service';
import { DoctorQueueNowServing } from '../doctor-queue.model';

const POLL_MS = 5000;

/**
 * Unauthenticated kiosk screen (see app.routes.ts's top-level 'board/:consultantId'
 * route, a sibling of 'login' with no authGuard) meant to run unattended on a
 * TV/monitor outside one doctor's consulting room. Polls the public
 * /api/public/doctor-queue/now-serving endpoint - deliberately PHI-free, so
 * this component only ever sees a token number, never a patient name.
 */
@Component({
  selector: 'app-doctor-queue-board',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './doctor-queue-board.component.html',
  styleUrl: './doctor-queue-board.component.scss'
})
export class DoctorQueueBoardComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(DoctorQueueBoardService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly consultantId = Number(this.route.snapshot.paramMap.get('consultantId'));
  private readonly muteKey = `doctor-queue-board-muted-${this.consultantId}`;

  readonly loading = signal(true);
  readonly notFound = signal(false);
  readonly nowServing = signal<DoctorQueueNowServing | null>(null);
  readonly muted = signal<boolean>(localStorage.getItem(this.muteKey) === 'true');
  /** Browsers block audio until a real user gesture - the kiosk operator taps once at setup time to unlock it for the whole session. */
  readonly soundLocked = signal(true);

  private readonly refresh$ = new Subject<void>();
  private hasLoadedOnce = false;
  private previousToken: string | null = null;
  private audioContext: AudioContext | null = null;

  constructor() {
    this.refresh$
      .pipe(
        switchMap(() =>
          this.service.getNowServing(this.consultantId).pipe(
            catchError(() => {
              this.loading.set(false);
              this.notFound.set(true);
              return EMPTY;
            })
          )
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((data) => this.onData(data));

    this.refresh$.next();
    const timer = setInterval(() => this.refresh$.next(), POLL_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  toggleMute(): void {
    const next = !this.muted();
    this.muted.set(next);
    localStorage.setItem(this.muteKey, String(next));
  }

  /** Tapping the "enable sound" prompt is the one user gesture browsers require before audio can play at all. */
  unlockSound(): void {
    this.soundLocked.set(false);
    this.audioContext = new AudioContext();
  }

  private onData(data: DoctorQueueNowServing): void {
    this.loading.set(false);
    this.notFound.set(false);

    const isFirstLoad = !this.hasLoadedOnce;
    this.hasLoadedOnce = true;
    const changed = data.currentTokenDisplay !== this.previousToken;
    this.previousToken = data.currentTokenDisplay;

    this.nowServing.set(data);

    if (!isFirstLoad && changed && data.currentTokenDisplay && !this.muted() && !this.soundLocked()) {
      this.announce(data);
    }
  }

  private announce(data: DoctorQueueNowServing): void {
    this.playChime();
    if (!('speechSynthesis' in window)) {
      return;
    }
    const room = data.consultingRoomLabel ? `, ${data.consultingRoomLabel}` : '';
    const utterance = new SpeechSynthesisUtterance(
      `Token ${data.currentTokenDisplay}, please proceed to ${data.consultantName}${room}`
    );
    window.speechSynthesis.speak(utterance);
  }

  private playChime(): void {
    const ctx = this.audioContext;
    if (!ctx) {
      return;
    }
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    oscillator.type = 'sine';
    oscillator.frequency.setValueAtTime(880, ctx.currentTime);
    gain.gain.setValueAtTime(0.3, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.6);
    oscillator.connect(gain);
    gain.connect(ctx.destination);
    oscillator.start();
    oscillator.stop(ctx.currentTime + 0.6);
  }
}

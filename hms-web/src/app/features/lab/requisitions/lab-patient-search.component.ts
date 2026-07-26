import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { NotificationService } from '../../../shared/services/notification.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { Patient } from '../../registration/patients/patient.model';
import { PatientService } from '../../registration/patients/patient.service';

type SearchMode = 'lab' | 'investigations';

/**
 * Lab/Investigations patient search - shared entry point for both flows.
 * Both buttons run the identical patient search; which one was clicked only
 * decides where selecting a result routes next (the Lab Requisition test-picker
 * vs the Investigations Patient Billing Advice screen).
 */
@Component({
  selector: 'app-lab-patient-search',
  standalone: true,
  imports: [
    FormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    EmptyStateComponent
  ],
  templateUrl: './lab-patient-search.component.html',
  styleUrl: './lab-patient-search.component.scss'
})
export class LabPatientSearchComponent {
  private readonly patientService = inject(PatientService);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly autocompleteTerms = new Subject<string>();

  searchQuery = '';
  loading = signal(false);
  searched = signal(false);
  results = signal<Patient[]>([]);

  /** Live suggestions shown under the field as-you-type - lets the user pick the exact UHID instead of typing it blind. */
  suggestions = signal<Patient[]>([]);
  private mode: SearchMode = 'lab';

  constructor() {
    this.autocompleteTerms
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((query) => this.patientService.search(query)),
        takeUntilDestroyed()
      )
      .subscribe({
        next: (patients) => this.suggestions.set(patients),
        error: () => this.suggestions.set([])
      });
  }

  onQueryChange(): void {
    const query = this.searchQuery.trim();
    if (!query) {
      this.suggestions.set([]);
      return;
    }
    this.autocompleteTerms.next(query);
  }

  /** Selecting a suggestion fills the field with its exact UHID - the user still picks Lab/Investigations to proceed. */
  onSuggestionSelected(event: MatAutocompleteSelectedEvent): void {
    const patient = event.option.value as Patient;
    this.searchQuery = patient.registrationNumber ?? '';
    this.suggestions.set([]);
  }

  search(mode: SearchMode = this.mode): void {
    const query = this.searchQuery.trim();
    if (!query) {
      return;
    }
    this.mode = mode;
    this.loading.set(true);
    this.patientService.search(query).subscribe({
      next: (patients) => {
        this.results.set(patients);
        this.searched.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Patient search failed.');
      }
    });
  }

  selectPatient(patient: Patient): void {
    if (patient.id === null) {
      return;
    }
    const path = this.mode === 'investigations' ? '/lab/investigations/new' : '/lab/requisitions/new';
    this.router.navigate([path, patient.id]);
  }
}

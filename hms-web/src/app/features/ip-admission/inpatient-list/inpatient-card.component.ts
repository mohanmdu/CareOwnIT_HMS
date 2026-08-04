import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { Admission } from '../admissions/admission.model';
import { PrivateImageDirective } from '../../../shared/directives/private-image.directive';

@Component({
  selector: 'app-inpatient-card',
  standalone: true,
  imports: [DatePipe, DecimalPipe, MatButtonModule, RouterLink, PrivateImageDirective],
  templateUrl: './inpatient-card.component.html',
  styleUrl: './inpatient-card.component.scss'
})
export class InpatientCardComponent {
  admission = input.required<Admission>();
  wardChange = output<Admission>();

  initials(): string {
    return (this.admission().patientName ?? '?').trim().charAt(0).toUpperCase();
  }
}

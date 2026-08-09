import { Component, input, output } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectChange, MatSelectModule } from '@angular/material/select';

/**
 * A `<mat-select>` constrained to a fixed, caller-supplied list of "HH:mm"
 * time strings (see generateTimeOptions in consultant-timing.model.ts, or
 * any future caller's own list) - replaces native `<input type="time">`
 * wherever times must snap to a discrete interval grid instead of accepting
 * free entry.
 *
 * Plain value/valueChange (not a ControlValueAccessor), matching
 * ColorFieldComponent's convention: the parent binds [value] + (valueChange)
 * the same way it already binds [(ngModel)] elsewhere (zero Reactive Forms
 * usage app-wide).
 *
 * Validity is NOT computed internally - any option in this field's own list
 * is always a legal choice in isolation; it's the relationship between two
 * sibling fields (To > From) that can be wrong, which only the parent knows.
 * The parent passes that verdict in via `invalid`.
 */
@Component({
  selector: 'app-time-select',
  standalone: true,
  imports: [MatFormFieldModule, MatSelectModule],
  templateUrl: './time-select.component.html',
  styleUrl: './time-select.component.scss'
})
export class TimeSelectComponent {
  /** "HH:mm" strings this field may be set to, in display order - this component never sorts/dedupes them. */
  options = input.required<string[]>();
  /** Currently selected "HH:mm", or '' / null for "nothing chosen yet" (shows the placeholder). */
  value = input<string | null>(null);
  placeholder = input('Select Time');
  ariaLabel = input<string | null>(null);
  invalid = input(false);
  disabled = input(false);

  valueChange = output<string>();

  onSelectionChange(change: MatSelectChange): void {
    this.valueChange.emit(change.value);
  }
}

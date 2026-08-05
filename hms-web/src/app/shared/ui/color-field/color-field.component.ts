import { Component, ElementRef, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NotificationService } from '../../services/notification.service';

const HEX_PATTERN = /^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/;

let nextInstanceId = 0;

/** #abc -> #AABBCC, #a1b2c3 -> #A1B2C3 - the native color input and every stored preset both require/use the full 6-digit form. */
function normalizeHex(raw: string): string | null {
  const trimmed = raw.trim();
  if (!HEX_PATTERN.test(trimmed)) {
    return null;
  }
  const digits = trimmed.replace('#', '');
  const expanded = digits.length === 3 ? digits.replace(/(.)/g, '$1$1') : digits;
  return `#${expanded.toUpperCase()}`;
}

/**
 * Swatch + monospace hex text + Copy/Paste, replacing a bare
 * `input[type=color]` everywhere a theme color is edited (see the Theme
 * Studio redesign proposal). The swatch itself still opens the native OS
 * color picker (a hidden input[type=color] behind it) for admins who prefer
 * to pick visually - the hex text field is the primary, copy-pasteable
 * surface for admins matching an exact brand color.
 *
 * Plain value/valueChange (not a ControlValueAccessor) - matches how the
 * rest of this form already works via template-driven [(ngModel)] on plain
 * inputs; the parent just does [(value)]="form.xxx" the same way.
 */
@Component({
  selector: 'app-color-field',
  standalone: true,
  imports: [FormsModule, MatIconModule, MatTooltipModule],
  templateUrl: './color-field.component.html',
  styleUrl: './color-field.component.scss'
})
export class ColorFieldComponent {
  private readonly notification = inject(NotificationService);

  label = input.required<string>();
  hint = input<string | null>(null);
  /** Empty/null means "not set - inherits the app default", matching every other theme field in this form. */
  value = input<string | null>(null);
  valueChange = output<string | null>();

  private readonly nativeColorInput = viewChild.required<ElementRef<HTMLInputElement>>('nativeColorInput');

  /** Unique per instance - the label text alone isn't safe to use as the input's id (two fields can share a label, e.g. across different ColorGroup tabs). */
  readonly inputId = `color-field-${++nextInstanceId}`;

  /** What the text field shows while being typed - may briefly be invalid mid-edit, so it's tracked separately from the committed `value`. */
  draftText = signal('');
  invalid = signal(false);

  constructor() {
    // Re-syncs the draft text whenever the parent's bound value changes out
    // from under this field (preset applied, Undo, Import JSON, Reset, ...)
    // - not just on this field's own edits, which go the other direction
    // (draft -> commit -> value) and shouldn't bounce back through here.
    // allowSignalWrites - writing draftText/invalid (this component's own
    // signals, never a signal read elsewhere in the same effect) here is
    // the whole point; without it Angular throws NG0600 on every value()
    // change and this sync silently never happens.
    effect(() => this.syncDraftFromValue(this.value()), { allowSignalWrites: true });
  }

  private syncDraftFromValue(value: string | null): void {
    this.draftText.set(value ?? '');
    this.invalid.set(false);
  }

  /** The swatch itself - shows the current color, or a transparent checkerboard-ish placeholder when unset, and opens the native picker on click. */
  get swatchColor(): string {
    return this.value() ?? 'transparent';
  }

  openNativePicker(): void {
    this.nativeColorInput().nativeElement.click();
  }

  onNativeColorPicked(event: Event): void {
    const picked = (event.target as HTMLInputElement).value;
    this.commit(picked);
  }

  /**
   * Only tracks the draft + live validity while typing - does NOT commit on
   * every keystroke. A partial hex can pass through a syntactically-valid
   * 3-digit-shorthand state on the way to a longer 6-digit value (typing
   * "#123ABC" passes through "#123", itself a valid "#112233" shorthand) -
   * committing there would silently mangle the value before the user
   * finishes typing. Commits happen only on blur or Enter (onCommitDraft),
   * matching how hex fields behave in Figma/VS Code/etc.
   */
  onHexInput(text: string): void {
    this.draftText.set(text);
    this.invalid.set(text.trim() !== '' && normalizeHex(text) === null);
  }

  /** Commits a finished, valid edit (blur or Enter) - an empty or still-invalid draft reverts to the last committed value instead of silently clearing it; use the explicit Clear button for that. */
  onCommitDraft(): void {
    const normalized = normalizeHex(this.draftText());
    if (normalized) {
      this.commit(normalized);
    } else {
      this.syncDraftFromValue(this.value());
    }
  }

  clear(): void {
    this.commit(null);
  }

  private commit(value: string | null): void {
    this.draftText.set(value ?? '');
    this.invalid.set(false);
    this.valueChange.emit(value);
  }

  copy(): void {
    const current = this.value();
    if (!current) {
      return;
    }
    navigator.clipboard
      .writeText(current)
      .then(() => this.notification.success(`Copied ${current}`))
      .catch(() => this.notification.error('Could not copy - your browser blocked clipboard access.'));
  }

  paste(): void {
    navigator.clipboard
      .readText()
      .then((text) => {
        const normalized = normalizeHex(text);
        if (!normalized) {
          this.notification.error(`Clipboard doesn't contain a valid hex color ("${text.trim().slice(0, 24)}").`);
          return;
        }
        this.commit(normalized);
      })
      .catch(() => this.notification.error('Could not read the clipboard - your browser blocked clipboard access.'));
  }
}

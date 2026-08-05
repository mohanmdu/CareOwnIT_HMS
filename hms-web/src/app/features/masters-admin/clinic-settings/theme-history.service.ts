import { Injectable } from '@angular/core';
import { CornerRadiusStyle, FontSizeScale, ThemeMode } from './clinic-settings.model';

/**
 * The theme-related subset of ClinicSettingsListComponent's `form` - the
 * same fields "Reset Theme & Appearance" already restores, deliberately
 * excluding hospital name/address/contact/website fields, which are a
 * different section of the same screen with their own separate Save.
 */
export interface ThemeFormSnapshot {
  themeMode: ThemeMode;
  themePrimaryColor: string;
  themeSecondaryColor: string;
  themeTertiaryColor: string;
  brandTextColor: string;
  fontFamily: string;
  fontSizeScale: FontSizeScale;
  cornerRadiusStyle: CornerRadiusStyle;
  headerBackgroundColor: string;
  footerBackgroundColor: string;
  footerText: string;
  menuBackgroundColor: string;
  menuTextColor: string;
  menuActiveBackgroundColor: string;
  menuActiveTextColor: string;
  menuHoverBackgroundColor: string;
  menuHoverTextColor: string;
  menuIconColor: string;
  menuHoverIconColor: string;
  menuChevronColor: string;
}

const MAX_DEPTH = 20;

/**
 * A plain "every applied state, in order" stack, not a before/after diff
 * log - undo() drops the current top and returns whatever's now on top,
 * so it composes naturally with push() being called after every applied
 * change (preset click, color edit, Reset, JSON import - anything that
 * already calls ClinicSettingsListComponent.previewTheme()) without each
 * call site needing to separately capture a "before" snapshot itself.
 */
@Injectable({ providedIn: 'root' })
export class ThemeHistoryService {
  private readonly stack: ThemeFormSnapshot[] = [];

  push(snapshot: ThemeFormSnapshot): void {
    this.stack.push(snapshot);
    if (this.stack.length > MAX_DEPTH + 1) {
      this.stack.shift();
    }
  }

  canUndo(): boolean {
    return this.stack.length > 1;
  }

  /** Drops the current (top) state and returns the one before it, or null if there's nothing left to undo to. */
  undo(): ThemeFormSnapshot | null {
    if (!this.canUndo()) {
      return null;
    }
    this.stack.pop();
    return this.stack[this.stack.length - 1];
  }

  /** Called once a fresh form is loaded/saved so a previous session's history never leaks into a new one. */
  reset(initial: ThemeFormSnapshot): void {
    this.stack.length = 0;
    this.stack.push(initial);
  }
}

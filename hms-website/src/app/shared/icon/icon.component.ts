import { Component, input } from '@angular/core';

/** Thin wrapper around the sprite mounted by IconSpriteComponent - `<app-icon name="heart" />`. */
@Component({
  selector: 'app-icon',
  standalone: true,
  template: `<svg class="icon" aria-hidden="true"><use [attr.href]="'#i-' + name()" /></svg>`
})
export class IconComponent {
  name = input.required<string>();
}

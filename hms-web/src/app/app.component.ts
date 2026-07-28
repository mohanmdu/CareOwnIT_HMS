import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastStackComponent } from './shared/ui/toast/toast-stack.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastStackComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {}

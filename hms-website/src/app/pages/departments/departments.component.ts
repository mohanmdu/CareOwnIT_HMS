import { Component, inject, signal } from '@angular/core';
import { PublicDepartmentService } from '../../core/services/public-department.service';
import { PublicDepartment } from '../../core/models/public.model';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { DepartmentCardComponent } from '../../shared/department-card/department-card.component';

@Component({
  selector: 'app-departments',
  standalone: true,
  imports: [PageHeaderComponent, DepartmentCardComponent],
  templateUrl: './departments.component.html',
  styleUrl: './departments.component.scss'
})
export class DepartmentsComponent {
  private readonly service = inject(PublicDepartmentService);
  departments = signal<PublicDepartment[]>([]);
  loaded = signal(false);

  constructor() {
    this.service.list().subscribe((departments) => {
      this.departments.set(departments);
      this.loaded.set(true);
    });
  }
}

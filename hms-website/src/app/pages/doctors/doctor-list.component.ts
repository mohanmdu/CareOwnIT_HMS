import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { PublicConsultantService } from '../../core/services/public-consultant.service';
import { PublicDepartmentService } from '../../core/services/public-department.service';
import { PublicConsultant, PublicDepartment } from '../../core/models/public.model';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { DoctorCardComponent } from '../../shared/doctor-card/doctor-card.component';

@Component({
  selector: 'app-doctor-list',
  standalone: true,
  imports: [PageHeaderComponent, DoctorCardComponent],
  templateUrl: './doctor-list.component.html',
  styleUrl: './doctor-list.component.scss'
})
export class DoctorListComponent {
  private readonly consultantService = inject(PublicConsultantService);
  private readonly departmentService = inject(PublicDepartmentService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  doctors = signal<PublicConsultant[]>([]);
  departments = signal<PublicDepartment[]>([]);
  loaded = signal(false);
  selectedDepartmentId = signal<number | null>(null);

  constructor() {
    this.departmentService.list().subscribe((departments) => this.departments.set(departments));
    this.route.queryParamMap.subscribe((params) => {
      const raw = params.get('departmentId');
      const departmentId = raw ? Number(raw) : undefined;
      this.selectedDepartmentId.set(departmentId ?? null);
      this.loaded.set(false);
      this.consultantService.list(departmentId).subscribe((doctors) => {
        this.doctors.set(doctors);
        this.loaded.set(true);
      });
    });
  }

  onDepartmentChange(value: string): void {
    const departmentId = value ? Number(value) : null;
    this.router.navigate([], { queryParams: departmentId ? { departmentId } : {} });
  }
}

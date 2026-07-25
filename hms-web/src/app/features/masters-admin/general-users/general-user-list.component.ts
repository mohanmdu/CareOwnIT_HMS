import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { NotificationService } from '../../../shared/services/notification.service';
import { PromptDialogService } from '../../../shared/services/prompt-dialog.service';
import { TablePagination } from '../../../shared/table/table-pagination';
import { TableSearchComponent } from '../../../shared/table/table-search.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { Role } from '../roles/role.model';
import { RoleService } from '../roles/role.service';
import { GeneralUser } from './general-user.model';
import { GeneralUserService } from './general-user.service';

/**
 * General Users: an admin-facing directory of who has system access, their
 * Role, and their real login credentials (username + password, set on
 * create; reset via the Reset Password action) - see com.pms.security on the
 * backend. One inline add/edit form + two tabs (Active / De-Activated),
 * mirroring Departments/Consultants.
 */
@Component({
  selector: 'app-general-user-list',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    MatTabsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    MatPaginatorModule,
    PageHeaderComponent,
    EmptyStateComponent,
    TableSearchComponent
  ],
  templateUrl: './general-user-list.component.html',
  styleUrl: './general-user-list.component.scss'
})
export class GeneralUserListComponent {
  private readonly service = inject(GeneralUserService);
  private readonly roleService = inject(RoleService);
  private readonly notification = inject(NotificationService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly promptDialog = inject(PromptDialogService);

  readonly activeColumns = ['sno', 'name', 'username', 'role', 'fromDate', 'createdBy', 'resetPassword', 'deactivate', 'edit'];
  readonly inactiveColumns = ['sno', 'name', 'username', 'role', 'fromDate', 'toDate', 'activate'];

  activeUsers = signal<GeneralUser[]>([]);
  inactiveUsers = signal<GeneralUser[]>([]);
  roles = signal<Role[]>([]);
  loadingActive = signal(false);
  loadingInactive = signal(false);
  saving = signal(false);

  editingUser = signal<GeneralUser | null>(null);

  searchTerm = signal('');
  filteredActiveUsers = computed(() => this.filterBySearch(this.activeUsers()));
  filteredInactiveUsers = computed(() => this.filterBySearch(this.inactiveUsers()));
  activePagination = new TablePagination(this.filteredActiveUsers);
  inactivePagination = new TablePagination(this.filteredInactiveUsers);

  form = {
    name: '',
    mobileNumber: '',
    roleId: null as number | null,
    email: '',
    username: '',
    initialPassword: ''
  };

  constructor() {
    this.refreshActive();
    this.roleService.list().subscribe({
      next: (roles) => this.roles.set(roles.filter((r) => r.active)),
      error: () => this.notification.error('Failed to load roles.')
    });
  }

  refreshActive(): void {
    this.loadingActive.set(true);
    this.service.list().subscribe({
      next: (users) => {
        this.activeUsers.set(users);
        this.activePagination.reset();
        this.loadingActive.set(false);
      },
      error: () => {
        this.loadingActive.set(false);
        this.notification.error('Failed to load users.');
      }
    });
  }

  refreshInactive(): void {
    this.loadingInactive.set(true);
    this.service.listInactive().subscribe({
      next: (users) => {
        this.inactiveUsers.set(users);
        this.inactivePagination.reset();
        this.loadingInactive.set(false);
      },
      error: () => {
        this.loadingInactive.set(false);
        this.notification.error('Failed to load de-activated users.');
      }
    });
  }

  onTabChange(index: number): void {
    if (index === 1) {
      this.refreshInactive();
    }
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.activePagination.reset();
    this.inactivePagination.reset();
  }

  private filterBySearch(users: GeneralUser[]): GeneralUser[] {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) {
      return users;
    }
    return users.filter(
      (u) => u.name.toLowerCase().includes(term) || (u.roleName ?? '').toLowerCase().includes(term)
    );
  }

  editUser(user: GeneralUser): void {
    this.editingUser.set(user);
    this.form = {
      name: user.name,
      mobileNumber: user.mobileNumber,
      roleId: user.roleId,
      email: user.email ?? '',
      username: user.username,
      initialPassword: ''
    };
  }

  cancelEdit(): void {
    this.editingUser.set(null);
    this.resetForm();
  }

  onMobileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.form.mobileNumber = input.value.replace(/\D/g, '').slice(0, 10);
  }

  get isValid(): boolean {
    const baseValid =
      this.form.name.trim().length > 0 &&
      /^\d{10}$/.test(this.form.mobileNumber) &&
      !!this.form.roleId &&
      this.form.username.trim().length > 0;
    return this.editingUser() ? baseValid : baseValid && this.form.initialPassword.trim().length > 0;
  }

  submit(): void {
    if (!this.isValid) {
      return;
    }
    this.saving.set(true);
    const profile = {
      name: this.form.name.trim(),
      mobileNumber: this.form.mobileNumber.trim(),
      roleId: this.form.roleId!,
      email: this.form.email.trim() || null,
      username: this.form.username.trim()
    };
    const editing = this.editingUser();
    const save$ = editing?.id
      ? this.service.update(editing.id, profile)
      : this.service.create({ ...profile, initialPassword: this.form.initialPassword });
    save$.subscribe({
      next: () => {
        this.saving.set(false);
        this.notification.success(editing ? 'User updated.' : 'User added.');
        this.editingUser.set(null);
        this.resetForm();
        this.refreshActive();
      },
      error: (err) => {
        this.saving.set(false);
        this.notification.error(err.error?.message ?? 'Failed to save user.');
      }
    });
  }

  private resetForm(): void {
    this.form = { name: '', mobileNumber: '', roleId: null, email: '', username: '', initialPassword: '' };
  }

  resetPassword(user: GeneralUser): void {
    if (user.id === null) {
      return;
    }
    this.promptDialog
      .prompt({
        title: `Reset password for ${user.name}?`,
        message: 'They will be required to choose a new password the next time they sign in.',
        fields: [{ key: 'newPassword', label: 'New Password', required: true }],
        confirmLabel: 'Reset Password'
      })
      .subscribe((values) => {
        if (!values || user.id === null) {
          return;
        }
        this.service.resetPassword(user.id, String(values['newPassword'])).subscribe({
          next: () => this.notification.success('Password reset.'),
          error: (err) => this.notification.error(err.error?.message ?? 'Failed to reset password.')
        });
      });
  }

  deactivate(user: GeneralUser): void {
    if (user.id === null) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: `Deactivate ${user.name}?`,
        message: 'The user will be moved to De-Activated General Users and can be restored later.',
        confirmLabel: 'Deactivate',
        destructive: true
      })
      .subscribe((confirmed) => {
        if (!confirmed || user.id === null) {
          return;
        }
        this.service.deactivate(user.id).subscribe({
          next: () => {
            this.notification.success('User deactivated.');
            this.refreshActive();
            this.refreshInactive();
          },
          error: () => this.notification.error('Failed to deactivate user.')
        });
      });
  }

  restore(user: GeneralUser): void {
    if (user.id === null) {
      return;
    }
    this.service.restore(user.id).subscribe({
      next: () => {
        this.notification.success('User restored.');
        this.refreshActive();
        this.refreshInactive();
      },
      error: () => this.notification.error('Failed to restore user.')
    });
  }
}

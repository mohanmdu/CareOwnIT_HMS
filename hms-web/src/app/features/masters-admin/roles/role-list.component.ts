import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { NAV_GROUPS } from '../../../layout/nav-config';
import { activeModuleKeys, ModuleKey } from '../../../layout/package-config';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { NotificationService } from '../../../shared/services/notification.service';
import { TablePagination } from '../../../shared/table/table-pagination';
import { TableSearchComponent } from '../../../shared/table/table-search.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge/status-badge.component';
import { Role } from './role.model';
import { RoleService } from './role.service';

interface ModulePermissionPage {
  route: string;
  label: string;
}

interface ModulePermissionOption {
  moduleKey: ModuleKey;
  label: string;
  pages: ModulePermissionPage[];
}

/**
 * One entry per distinct NAV_GROUPS module, in sidenav order, filtered to
 * the deployment's active package tier - deliberately finer-grained than the
 * OP/IP/Pharmacy/Lab/... vocabulary from the original ask, since that's
 * trivially expressed as checking the relevant group(s) and avoids inventing
 * a second vocabulary to keep in sync with nav-config.ts forever.
 *
 * 'overview' is excluded - implicitly granted to every role, since a role
 * with no landing page would strand a user immediately after login. A group
 * whose items carry their own moduleKey override (only Overview today, for
 * Cashier/CEO Dashboard) yields one entry per distinct override key instead
 * of one for the whole group, so those stay independently grantable.
 *
 * Each entry also carries its individual pages, for the expandable per-page
 * submenu - a module with only one page renders as a plain checkbox (no
 * expand arrow, nothing to narrow down), mirroring how the sidenav itself
 * renders a single-item group as a flat link instead of an accordion.
 */
function buildPermissionOptions(): ModulePermissionOption[] {
  const tierEnabled = new Set(activeModuleKeys());
  const seen = new Set<ModuleKey>();
  const options: ModulePermissionOption[] = [];

  for (const group of NAV_GROUPS) {
    const distinctKeys = [...new Set(group.items.map((item) => item.moduleKey ?? group.moduleKey))];
    for (const key of distinctKeys) {
      if (key === 'overview' || seen.has(key) || !tierEnabled.has(key)) {
        continue;
      }
      seen.add(key);
      const label = key === group.moduleKey ? group.label : (group.items.find((item) => item.moduleKey === key)?.label ?? key);
      const pages = group.items
        .filter((item) => (item.moduleKey ?? group.moduleKey) === key)
        .map((item) => ({ route: item.route, label: item.label }));
      options.push({ moduleKey: key, label, pages });
    }
  }
  return options;
}

/**
 * Roles: name + which HMS modules (and, per module, which individual pages)
 * the role can see/access (see the Role & User Management design). Custom
 * screen rather than the generic MasterCrudComponent used by
 * Departments/Specializations - the permission picker below the name field
 * doesn't fit that shared shape.
 */
@Component({
  selector: 'app-role-list',
  standalone: true,
  imports: [
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatProgressBarModule,
    MatSelectModule,
    MatPaginatorModule,
    PageHeaderComponent,
    EmptyStateComponent,
    TableSearchComponent,
    StatusBadgeComponent
  ],
  templateUrl: './role-list.component.html',
  styleUrl: './role-list.component.scss'
})
export class RoleListComponent {
  private readonly service = inject(RoleService);
  private readonly notification = inject(NotificationService);
  private readonly confirmDialog = inject(ConfirmDialogService);

  readonly permissionOptions = buildPermissionOptions();
  readonly columns = ['name', 'modules', 'status', 'actions'];

  roles = signal<Role[]>([]);
  loading = signal(false);
  saving = signal(false);
  editingRole = signal<Role | null>(null);
  expandedModules = signal<Set<ModuleKey>>(new Set());

  searchTerm = signal('');
  filteredRoles = computed(() => this.filterBySearch(this.roles()));
  pagination = new TablePagination(this.filteredRoles);

  form: { name: string; permittedModules: Set<ModuleKey>; permittedRoutes: Set<string>; defaultRoute: string | null } = {
    name: '',
    permittedModules: new Set(),
    permittedRoutes: new Set(),
    defaultRoute: null
  };

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (roles) => {
        this.roles.set(roles);
        this.pagination.reset();
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load roles.');
      }
    });
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.pagination.reset();
  }

  private filterBySearch(roles: Role[]): Role[] {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) {
      return roles;
    }
    return roles.filter((role) => role.name.toLowerCase().includes(term));
  }

  isModuleChecked(moduleKey: ModuleKey): boolean {
    return this.form.permittedModules.has(moduleKey);
  }

  isPageChecked(route: string): boolean {
    return this.form.permittedRoutes.has(route);
  }

  isExpanded(moduleKey: ModuleKey): boolean {
    return this.expandedModules().has(moduleKey);
  }

  toggleExpand(moduleKey: ModuleKey): void {
    this.expandedModules.update((current) => {
      const next = new Set(current);
      if (next.has(moduleKey)) {
        next.delete(moduleKey);
      } else {
        next.add(moduleKey);
      }
      return next;
    });
  }

  /** Checking a module grants all of its pages as a convenience bulk-action; unchecking withdraws all of them. Individual pages can then be pruned without affecting the module's own on/off state. */
  toggleModule(option: ModulePermissionOption, checked: boolean): void {
    if (checked) {
      this.form.permittedModules.add(option.moduleKey);
      for (const page of option.pages) {
        this.form.permittedRoutes.add(page.route);
      }
    } else {
      this.form.permittedModules.delete(option.moduleKey);
      for (const page of option.pages) {
        this.form.permittedRoutes.delete(page.route);
      }
    }
  }

  togglePage(route: string, checked: boolean): void {
    if (checked) {
      this.form.permittedRoutes.add(route);
    } else {
      this.form.permittedRoutes.delete(route);
    }
  }

  /**
   * Candidate pages for "default page after login" - only pages the role
   * being edited can actually reach right now, using the same "a module
   * with no page-level restriction recorded is fully open" rule as the
   * sidenav/route guard (see package-config.ts). A plain method, not a
   * computed signal - form.permittedModules/permittedRoutes are mutated
   * Sets on a plain object, not signals, so this needs to re-run on every
   * change-detection pass the same way isModuleChecked()/isPageChecked() do.
   */
  availableLandingPages(): { route: string; label: string }[] {
    const pages: { route: string; label: string }[] = [];
    for (const option of this.permissionOptions) {
      if (!this.form.permittedModules.has(option.moduleKey)) {
        continue;
      }
      const pageLevelRestricted = option.pages.some((page) => this.form.permittedRoutes.has(page.route));
      for (const page of option.pages) {
        if (!pageLevelRestricted || this.form.permittedRoutes.has(page.route)) {
          pages.push({ route: page.route, label: `${option.label} – ${page.label}` });
        }
      }
    }
    return pages;
  }

  /**
   * Counts only the modules the admin can actually configure (excludes
   * 'overview', which every role has regardless of what's picked) so this
   * never shows a numerator larger than the denominator - see
   * buildPermissionOptions().
   */
  modulesSummary(role: Role): string {
    const configurable = role.permittedModules.filter((key) => key !== 'overview').length;
    if (configurable === 0) {
      return 'No modules assigned';
    }
    return `${configurable} of ${this.permissionOptions.length} modules`;
  }

  get isValid(): boolean {
    return this.form.name.trim().length > 0;
  }

  editRole(role: Role): void {
    this.editingRole.set(role);
    this.form = {
      name: role.name,
      permittedModules: new Set(role.permittedModules),
      permittedRoutes: new Set(role.permittedRoutes),
      defaultRoute: role.defaultRoute
    };
  }

  cancelEdit(): void {
    this.editingRole.set(null);
    this.resetForm();
  }

  submit(): void {
    if (!this.isValid) {
      return;
    }
    this.saving.set(true);
    const input = {
      name: this.form.name.trim(),
      permittedModules: [...this.form.permittedModules],
      permittedRoutes: [...this.form.permittedRoutes],
      defaultRoute: this.form.defaultRoute
    };
    const editing = this.editingRole();
    const save$ = editing?.id ? this.service.update(editing.id, input) : this.service.create(input);
    save$.subscribe({
      next: () => {
        this.saving.set(false);
        this.notification.success(editing ? 'Role updated.' : 'Role added.');
        this.editingRole.set(null);
        this.resetForm();
        this.refresh();
      },
      error: (err) => {
        this.saving.set(false);
        this.notification.error(err.error?.message ?? 'Failed to save role.');
      }
    });
  }

  private resetForm(): void {
    this.form = { name: '', permittedModules: new Set(), permittedRoutes: new Set(), defaultRoute: null };
  }

  deactivate(role: Role): void {
    if (role.id === null) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: `Deactivate ${role.name}?`,
        message:
          'Existing sessions for users under this role stay valid until they expire, but new logins under this role will be blocked.',
        confirmLabel: 'Deactivate',
        destructive: true
      })
      .subscribe((confirmed) => {
        if (!confirmed || role.id === null) {
          return;
        }
        this.service.deactivate(role.id).subscribe({
          next: () => {
            this.notification.success('Role deactivated.');
            this.refresh();
          },
          error: () => this.notification.error('Failed to deactivate role.')
        });
      });
  }
}

import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { NAV_GROUPS } from '../../layout/nav-config';
import type { ModuleKey } from '../../layout/package-config';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { NotificationService } from '../../shared/services/notification.service';
import { TablePagination } from '../../shared/table/table-pagination';
import { TableSearchComponent } from '../../shared/table/table-search.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge/status-badge.component';
import { ClientService } from './client.service';
import { ClientRecord } from './client.model';

const BOOTSTRAP_FORM_DEFAULTS = { name: '', mobileNumber: '', username: '', initialPassword: '' };

interface ModuleOption {
  key: ModuleKey;
  label: string;
}

/** Always licensed for every client, never togglable off from this screen either - mirrors ClientService.ALWAYS_LICENSED on the backend, which is the real enforcement; this is just so the UI doesn't invite an admin to try. */
const ALWAYS_LICENSED: ModuleKey[] = ['overview', 'masters', 'administration'];

/**
 * Every module in the system, unfiltered - unlike role-list.component.ts's
 * buildPermissionOptions(), this deliberately does NOT filter by
 * activeModuleKeys()/package tier, since there is no tenant session (and
 * therefore no license) to filter against here at all - Super Admin is the
 * one screen that must be able to grant any module to any client.
 */
function buildModuleOptions(): ModuleOption[] {
  const seen = new Set<ModuleKey>();
  const options: ModuleOption[] = [];
  for (const group of NAV_GROUPS) {
    const distinctKeys = [...new Set(group.items.map((item) => item.moduleKey ?? group.moduleKey))];
    for (const key of distinctKeys) {
      if (seen.has(key)) {
        continue;
      }
      seen.add(key);
      const label = key === group.moduleKey ? group.label : (group.items.find((item) => item.moduleKey === key)?.label ?? key);
      options.push({ key, label });
    }
  }
  return options;
}

@Component({
  selector: 'app-client-list',
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
    MatPaginatorModule,
    PageHeaderComponent,
    EmptyStateComponent,
    TableSearchComponent,
    StatusBadgeComponent
  ],
  templateUrl: './client-list.component.html',
  styleUrl: './client-list.component.scss'
})
export class ClientListComponent {
  private readonly service = inject(ClientService);
  private readonly notification = inject(NotificationService);
  private readonly confirmDialog = inject(ConfirmDialogService);

  readonly moduleOptions = buildModuleOptions();
  readonly columns = ['name', 'code', 'status', 'modules', 'actions'];

  clients = signal<ClientRecord[]>([]);
  loading = signal(false);
  creating = signal(false);
  savingCreate = signal(false);
  managingClient = signal<ClientRecord | null>(null);
  savingModules = signal(false);
  selectedModules = new Set<ModuleKey>();
  savingDomain = signal(false);
  domainForm = { domain: '' };

  bootstrappingClient = signal<ClientRecord | null>(null);
  savingBootstrap = signal(false);
  bootstrapForm = { ...BOOTSTRAP_FORM_DEFAULTS };

  searchTerm = signal('');
  filteredClients = computed(() => this.filterBySearch(this.clients()));
  pagination = new TablePagination(this.filteredClients);

  createForm = { name: '', code: '' };

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        this.pagination.reset();
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load clients.');
      }
    });
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.pagination.reset();
  }

  private filterBySearch(clients: ClientRecord[]): ClientRecord[] {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) {
      return clients;
    }
    return clients.filter((client) => client.name.toLowerCase().includes(term) || client.code.toLowerCase().includes(term));
  }

  modulesSummary(client: ClientRecord): string {
    return `${client.licensedModules.length} of ${this.moduleOptions.length} modules`;
  }

  get isCreateValid(): boolean {
    return this.createForm.name.trim().length > 0 && this.createForm.code.trim().length > 0;
  }

  startCreate(): void {
    this.creating.set(true);
    this.createForm = { name: '', code: '' };
  }

  cancelCreate(): void {
    this.creating.set(false);
  }

  submitCreate(): void {
    if (!this.isCreateValid) {
      return;
    }
    this.savingCreate.set(true);
    this.service.create({ name: this.createForm.name.trim(), code: this.createForm.code.trim().toUpperCase() }).subscribe({
      next: () => {
        this.savingCreate.set(false);
        this.notification.success('Client added.');
        this.creating.set(false);
        this.refresh();
      },
      error: (err) => {
        this.savingCreate.set(false);
        this.notification.error(err.error?.message ?? 'Failed to add client.');
      }
    });
  }

  toggleStatus(client: ClientRecord): void {
    const suspending = client.status === 'ACTIVE';
    this.confirmDialog
      .confirm({
        title: suspending ? `Suspend ${client.name}?` : `Reactivate ${client.name}?`,
        message: suspending
          ? 'Every user at this client will be immediately unable to log in. Existing sessions stay valid until they expire.'
          : 'Users at this client will be able to log in again.',
        confirmLabel: suspending ? 'Suspend' : 'Reactivate',
        destructive: suspending
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.service.updateStatus(client.id, suspending ? 'SUSPENDED' : 'ACTIVE').subscribe({
          next: () => {
            this.notification.success(suspending ? 'Client suspended.' : 'Client reactivated.');
            this.refresh();
          },
          error: () => this.notification.error('Failed to update client status.')
        });
      });
  }

  isAlwaysLicensed(key: ModuleKey): boolean {
    return ALWAYS_LICENSED.includes(key);
  }

  isModuleChecked(key: ModuleKey): boolean {
    return this.isAlwaysLicensed(key) || this.selectedModules.has(key);
  }

  toggleModule(key: ModuleKey, checked: boolean): void {
    if (this.isAlwaysLicensed(key)) {
      return;
    }
    if (checked) {
      this.selectedModules.add(key);
    } else {
      this.selectedModules.delete(key);
    }
  }

  manageClient(client: ClientRecord): void {
    this.managingClient.set(client);
    this.selectedModules = new Set(client.licensedModules);
    this.domainForm = { domain: client.domain ?? '' };
  }

  cancelManage(): void {
    this.managingClient.set(null);
  }

  saveModules(): void {
    const client = this.managingClient();
    if (!client) {
      return;
    }
    const modules: Partial<Record<ModuleKey, boolean>> = {};
    for (const option of this.moduleOptions) {
      modules[option.key] = this.isModuleChecked(option.key);
    }
    this.savingModules.set(true);
    this.service.updateModules(client.id, modules).subscribe({
      next: () => {
        this.savingModules.set(false);
        this.notification.success('License updated.');
        this.managingClient.set(null);
        this.refresh();
      },
      error: () => {
        this.savingModules.set(false);
        this.notification.error('Failed to update license.');
      }
    });
  }

  saveDomain(): void {
    const client = this.managingClient();
    if (!client) {
      return;
    }
    this.savingDomain.set(true);
    this.service.updateDomain(client.id, this.domainForm.domain.trim()).subscribe({
      next: (updated) => {
        this.savingDomain.set(false);
        this.notification.success(updated.domain ? 'Domain updated.' : 'Domain cleared.');
        this.refresh();
      },
      error: (err) => {
        this.savingDomain.set(false);
        this.notification.error(err.error?.message ?? 'Failed to update domain.');
      }
    });
  }

  get isBootstrapValid(): boolean {
    return (
      this.bootstrapForm.name.trim().length > 0 &&
      /^\d{10}$/.test(this.bootstrapForm.mobileNumber) &&
      this.bootstrapForm.username.trim().length > 0 &&
      this.bootstrapForm.initialPassword.trim().length > 0
    );
  }

  /** Mirrors general-user-list.component.ts's onMobileInput() - digits only, capped at 10, filtered live rather than just validated on submit. */
  onBootstrapMobileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.bootstrapForm.mobileNumber = input.value.replace(/\D/g, '').slice(0, 10);
  }

  /** Only meant for a client's very first user - see ClientAdminBootstrapService on the backend. Safe to call again on a client that already has an admin (it reuses the existing "Administrator" role rather than duplicating it), but ordinary onboarding of a second+ user should go through that client's own General Users screen instead, once its first admin can log in. */
  bootstrapAdmin(client: ClientRecord): void {
    this.bootstrappingClient.set(client);
    this.bootstrapForm = { ...BOOTSTRAP_FORM_DEFAULTS };
  }

  cancelBootstrap(): void {
    this.bootstrappingClient.set(null);
  }

  submitBootstrap(): void {
    const client = this.bootstrappingClient();
    if (!client || !this.isBootstrapValid) {
      return;
    }
    this.savingBootstrap.set(true);
    this.service
      .bootstrapAdmin(client.id, {
        name: this.bootstrapForm.name.trim(),
        mobileNumber: this.bootstrapForm.mobileNumber.trim(),
        username: this.bootstrapForm.username.trim(),
        initialPassword: this.bootstrapForm.initialPassword
      })
      .subscribe({
        next: (result) => {
          this.savingBootstrap.set(false);
          this.notification.success(`Admin "${result.username}" created for ${client.name}. They'll be asked to change their password on first login.`);
          this.bootstrappingClient.set(null);
        },
        error: (err) => {
          this.savingBootstrap.set(false);
          this.notification.error(err.error?.message ?? 'Failed to create admin.');
        }
      });
  }
}

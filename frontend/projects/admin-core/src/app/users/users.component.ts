import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, AdminHeadingComponent, BadgeComponent, statusTone, AdminUser, BackendRole } from 'shared-ui';
import { LucidePlus, LucidePencil, LucideX, LucideSearch } from '@lucide/angular';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [
    CommonModule, FormsModule, AdminHeadingComponent, BadgeComponent,
    LucidePlus, LucidePencil, LucideX, LucideSearch
  ],
  template: `
    <lib-admin-heading title="Admin Users" subtitle="Manage access to the Nigson admin portal">
      <button (click)="openNewUser()" class="flex h-10 items-center gap-2 rounded-xl bg-brand px-4 text-sm font-bold text-white transition-colors hover:bg-brand-600 shadow-sm">
        <svg lucidePlus class="h-4 w-4"></svg> Assign Staff Access
      </button>
    </lib-admin-heading>

    <div class="rounded-2xl border border-hairline bg-white shadow-sm overflow-hidden">
      <!-- Search & Role Filter Bar -->
      <div class="flex flex-wrap items-center gap-3 border-b border-hairline p-4 bg-surface/50">
        <div class="relative min-w-[260px] flex-1">
          <svg lucideSearch class="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"></svg>
          <input
            type="text"
            [(ngModel)]="searchQuery"
            placeholder="Search users by name or email..."
            class="w-full rounded-xl border border-hairline bg-white pl-10 pr-4 py-2 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 shadow-sm"
          >
        </div>

        <select
          [(ngModel)]="roleFilter"
          class="rounded-xl border border-hairline bg-white px-4 py-2 text-sm font-medium focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 shadow-sm"
        >
          <option value="All roles">All roles</option>
          <option *ngFor="let r of admin.roles()" [value]="r.id">{{ r.name }}</option>
        </select>

        <select
          [(ngModel)]="statusFilter"
          class="rounded-xl border border-hairline bg-white px-4 py-2 text-sm font-medium focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 shadow-sm"
        >
          <option value="All statuses">All statuses</option>
          <option value="Active">Active</option>
          <option value="Disabled">Disabled</option>
        </select>
      </div>

      <!-- Users Table -->
      <div class="overflow-x-auto">
        <table class="w-full min-w-[720px] text-left text-sm text-ink">
          <thead class="border-b border-hairline bg-surface-alt/50 text-[10px] font-bold uppercase tracking-widest text-muted-ink">
            <tr>
              <th class="px-6 py-4">Name</th>
              <th class="px-6 py-4">Role</th>
              <th class="px-6 py-4">Status</th>
              <th class="px-6 py-4">Last Login</th>
              <th class="px-6 py-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-hairline">
            <tr *ngFor="let u of filteredUsers()" class="transition-colors hover:bg-surface-alt/30">
              <td class="px-6 py-4">
                <div class="flex items-center gap-3">
                  <div class="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-brand/10 text-xs font-bold text-brand uppercase">
                    {{ getInitials(u.name) }}
                  </div>
                  <div>
                    <p class="font-bold text-ink">{{ u.name }}</p>
                    <p class="text-xs text-muted-ink">{{ u.email }}</p>
                  </div>
                </div>
              </td>
              <td class="px-6 py-4">
                <span class="inline-flex items-center px-2.5 py-1 rounded-lg text-xs font-semibold bg-gray-100 text-gray-800">
                  {{ (u.roles || [u.roleId]).join(', ') }}
                </span>
              </td>
              <td class="px-6 py-4">
                <lib-badge [tone]="getTone(u.status)">{{ u.status }}</lib-badge>
              </td>
              <td class="px-6 py-4 text-xs text-muted-ink">{{ u.lastLogin | date:'medium' }}</td>
              <td class="px-6 py-4 text-right">
                <div class="flex items-center justify-end gap-2">
                  <button
                    (click)="editUser(u)"
                    class="grid h-8 w-8 place-items-center rounded-lg border border-hairline bg-white text-muted-ink transition-all hover:bg-brand/10 hover:text-brand hover:border-brand/30 shadow-xs"
                    title="Edit Role & Permissions"
                  >
                    <svg lucidePencil class="h-4 w-4"></svg>
                  </button>
                </div>
              </td>
            </tr>
            <tr *ngIf="filteredUsers().length === 0">
              <td colspan="5" class="p-12 text-center text-muted-ink">
                <p class="text-base font-semibold text-ink">No users found</p>
                <p class="text-xs text-muted-ink mt-1">Try adjusting your search or role filter.</p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Edit User Modal -->
    <div *ngIf="editingUser()" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
      <div class="w-full max-w-lg rounded-[24px] border border-gray-100 bg-white p-6 sm:p-8 shadow-2xl animate-in fade-in zoom-in-95 duration-200">
        <!-- Modal Header -->
        <div class="mb-6 flex items-center justify-between border-b border-hairline pb-4">
          <div class="flex items-center gap-3">
            <div class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-brand/10 text-sm font-extrabold text-brand uppercase">
              {{ getInitials(editingUser()!.name) }}
            </div>
            <div>
              <h2 class="text-lg font-extrabold text-ink">Edit Staff Role</h2>
              <p class="text-xs text-muted-ink">{{ editingUser()!.email }}</p>
            </div>
          </div>
          <button (click)="closeEditModal()" class="grid h-8 w-8 place-items-center rounded-full bg-gray-100 text-gray-500 hover:bg-gray-200 transition-colors">
            <svg lucideX class="h-4 w-4"></svg>
          </button>
        </div>

        <!-- Form Body -->
        <div class="space-y-4">
          <!-- Role Selector -->
          <div>
            <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Assigned Role</label>
            <select
              multiple [ngModel]="selectedRoles()"
              (ngModelChange)="selectedRoles.set($event)"
              class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm font-medium focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
            >
              <option *ngFor="let r of admin.roles()" [value]="r.id">{{ r.name }}</option>
            </select>
          </div>

          <!-- Role Access Preview -->
          <div class="rounded-xl border border-brand/20 bg-brand/5 p-3.5 text-xs">
            <p class="font-bold text-brand">Permissions granted by {{ roleName(selectedRole()) }}:</p>
            <p class="mt-1 text-gray-600 leading-relaxed">{{ getRoleDescription(selectedRole()) }}</p>
          </div>

          <!-- Account Status -->
          <div>
            <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Staff access</label>
            <div class="grid grid-cols-2 gap-3">
              <button
                type="button"
                (click)="selectedStatus.set('Active')"
                class="rounded-xl border px-4 py-2 text-xs font-bold transition-all text-center"
                [ngClass]="selectedStatus() === 'Active' ? 'border-emerald-500 bg-emerald-50 text-emerald-700 shadow-xs' : 'border-hairline bg-white text-muted-ink'"
              >
                Active
              </button>
              <button
                type="button"
                (click)="selectedStatus.set('Disabled')"
                class="rounded-xl border px-4 py-2 text-xs font-bold transition-all text-center"
                [ngClass]="selectedStatus() === 'Disabled' ? 'border-rose-500 bg-rose-50 text-rose-700 shadow-xs' : 'border-hairline bg-white text-muted-ink'"
              >
                Disabled (Revoked)
              </button>
            </div>
          </div>

          <!-- Reason Input -->
          <div>
            <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Reason for Change (Audit Log)</label>
            <input
              type="text"
              [ngModel]="updateReason()"
              (ngModelChange)="updateReason.set($event)"
              placeholder="e.g. Administrative role assignment update"
              class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
            >
            <p class="mt-1 text-[11px] text-muted-ink">Must be at least 10 characters for audit compliance.</p>
          </div>

          <!-- Alerts -->
          <div *ngIf="errorMessage()" class="rounded-xl bg-rose-50 border border-rose-200 p-3 text-xs text-rose-700 font-medium">
            {{ errorMessage() }}
          </div>
          <div *ngIf="successMessage()" class="rounded-xl bg-emerald-50 border border-emerald-200 p-3 text-xs text-emerald-700 font-medium">
            {{ successMessage() }}
          </div>
        </div>

        <!-- Footer Actions -->
        <div class="mt-6 flex items-center justify-end gap-3 border-t border-hairline pt-4">
          <button
            (click)="closeEditModal()"
            class="rounded-xl bg-gray-100 px-4 py-2 text-xs font-bold text-gray-700 hover:bg-gray-200 transition-colors"
          >
            Cancel
          </button>
          <button
            (click)="saveRole()"
            [disabled]="saving()"
            class="flex items-center gap-2 rounded-xl bg-brand px-5 py-2 text-xs font-bold text-white transition-colors hover:bg-brand-600 disabled:opacity-50 shadow-sm"
          >
            <span *ngIf="saving()">Saving...</span>
            <span *ngIf="!saving()">Save Role Changes</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Assign Staff Access / Promote Modal -->
    <div *ngIf="isNewUserOpen()" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
      <div class="w-full max-w-lg rounded-[24px] border border-gray-100 bg-white p-6 sm:p-8 shadow-2xl animate-in fade-in zoom-in-95 duration-200">
        <!-- Modal Header -->
        <div class="mb-4 flex items-center justify-between border-b border-hairline pb-4">
          <div>
            <h2 class="text-lg font-extrabold text-ink">Add Staff Member</h2>
            <p class="text-xs text-muted-ink">Provision administrative portal access</p>
          </div>
          <button (click)="isNewUserOpen.set(false)" class="grid h-8 w-8 place-items-center rounded-full bg-gray-100 text-gray-500 hover:bg-gray-200 transition-colors">
            <svg lucideX class="h-4 w-4"></svg>
          </button>
        </div>

        <!-- Mode Switcher Tabs -->
        <div class="mb-5 flex rounded-xl bg-gray-100 p-1">
          
          <button
            type="button"
            (click)="creationMode.set('promote')"
            class="flex-1 rounded-lg py-1.5 text-xs font-bold transition-all"
            [ngClass]="creationMode() === 'promote' ? 'bg-white text-ink shadow-xs' : 'text-muted-ink hover:text-ink'"
          >
            Promote Existing User
          </button>
        </div>

        <!-- Mode 1: Create Brand New Account -->
        <div *ngIf="creationMode() === 'create'" class="space-y-4">
          <div>
            <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Full Name *</label>
            <input
              type="text"
              [ngModel]="newName()"
              (ngModelChange)="newName.set($event)"
              placeholder="e.g. Ada Christopher"
              class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
            >
          </div>

          <div>
            <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Work Email Address *</label>
            <input
              type="email"
              [ngModel]="newEmail()"
              (ngModelChange)="newEmail.set($event)"
              placeholder="e.g. ada.christopher@nigson.com"
              class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
            >
          </div>

          <div>
            <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Temporary Password *</label>
            <input
              type="text"
              [ngModel]="newPassword()"
              (ngModelChange)="newPassword.set($event)"
              placeholder="Minimum 12 characters"
              class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm font-mono focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
            >
            <p class="mt-1 text-[11px] text-muted-ink">Created in Keycloak. Share this password with the staff member to log in.</p>
          </div>

          <div>
            <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Assign Role *</label>
            <select
              [ngModel]="newRole()"
              (ngModelChange)="newRole.set($event)"
              class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm font-medium focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
            >
              <option *ngFor="let r of admin.roles()" [value]="r.id">{{ r.name }}</option>
            </select>
          </div>

          <!-- Role Access Preview -->
          <div class="rounded-xl border border-brand/20 bg-brand/5 p-3 text-xs">
            <p class="font-bold text-brand">Permissions granted by {{ roleName(newRole()) }}:</p>
            <p class="mt-1 text-gray-600 leading-relaxed">{{ getRoleDescription(newRole()) }}</p>
          </div>
        </div>

        <!-- Mode 2: Promote Existing User -->
        <div *ngIf="creationMode() === 'promote'" class="space-y-4">
          <div>
            <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Registered account email *</label>
            <input
              type="email"
              [ngModel]="selectedPromoteEmail()"
              (ngModelChange)="selectedPromoteEmail.set($event)"
              placeholder="name@example.com"
              class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm font-medium focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
            >
            <p class="mt-1 text-[11px] text-muted-ink">
              Enter the email used to sign up. We will verify the active account before assigning a role.
            </p>
          </div>

          <div>
            <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Assign Role *</label>
            <select
              [ngModel]="newRole()"
              (ngModelChange)="newRole.set($event)"
              class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm font-medium focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
            >
              <option *ngFor="let r of admin.roles()" [value]="r.id">{{ r.name }}</option>
            </select>
          </div>

          <!-- Role Access Preview -->
          <div class="rounded-xl border border-brand/20 bg-brand/5 p-3 text-xs">
            <p class="font-bold text-brand">Permissions granted by {{ roleName(newRole()) }}:</p>
            <p class="mt-1 text-gray-600 leading-relaxed">{{ getRoleDescription(newRole()) }}</p>
          </div>
        </div>

        <!-- Provisioning Reason -->
        <div class="mt-4">
          <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Provisioning Reason</label>
          <input
            type="text"
            [ngModel]="newReason()"
            (ngModelChange)="newReason.set($event)"
            placeholder="e.g. Staff role assignment by administrator"
            class="w-full rounded-xl border border-hairline bg-white px-4 py-2 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
          >
        </div>

        <!-- Alerts -->
        <div *ngIf="newError()" class="mt-4 rounded-xl bg-rose-50 border border-rose-200 p-3 text-xs text-rose-700 font-medium">
          {{ newError() }}
        </div>
        <div *ngIf="newSuccess()" class="mt-4 rounded-xl bg-emerald-50 border border-emerald-200 p-3 text-xs text-emerald-700 font-medium">
          {{ newSuccess() }}
        </div>

        <!-- Footer Actions -->
        <div class="mt-6 flex items-center justify-end gap-3 border-t border-hairline pt-4">
          <button
            (click)="isNewUserOpen.set(false)"
            class="rounded-xl bg-gray-100 px-4 py-2 text-xs font-bold text-gray-700 hover:bg-gray-200 transition-colors"
          >
            Cancel
          </button>
          <button
            (click)="submitStaffUser()"
            [disabled]="newSaving()"
            class="flex items-center gap-2 rounded-xl bg-brand px-5 py-2 text-xs font-bold text-white transition-colors hover:bg-brand-600 disabled:opacity-50 shadow-sm"
          >
            <span *ngIf="newSaving()">Saving...</span>
            <span *ngIf="!newSaving()">{{ creationMode() === 'create' ? 'Create User' : 'Promote to Staff' }}</span>
          </button>
        </div>
      </div>
    </div>
  `
})
export class AdminUsersComponent implements OnInit {
  admin = inject(AdminService);

  searchQuery = signal('');
  roleFilter = signal('All roles');
  statusFilter = signal('All statuses');

  // Edit Modal State
  editingUser = signal<AdminUser | null>(null);
  selectedRoles=signal<string[]>([]);
  selectedRole = signal<string>('ADMIN');
  selectedStatus = signal<'Active' | 'Disabled'>('Active');
  updateReason = signal<string>('Administrative role assignment update');
  saving = signal(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  // Assign Staff Access Modal State
  isNewUserOpen = signal(false);
  creationMode = signal<'create' | 'promote'>('promote');
  newName = signal('');
  newEmail = signal('');
  newPassword = signal('NigsonAdmin@2026');
  selectedPromoteEmail = signal('');
  newRole = signal('SALES');
  newStatus = signal<'Active' | 'Disabled'>('Active');
  newReason = signal('Staff provisioning by administrator');
  newSaving = signal(false);
  newError = signal<string | null>(null);
  newSuccess = signal<string | null>(null);

  ngOnInit() {
    this.admin.loadStaffUsers().subscribe();
  }

  filteredUsers = computed(() => {
    let users = this.admin.admins();
    const q = this.searchQuery().toLowerCase().trim();
    if (q) {
      users = users.filter(u => u.name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q));
    }
    const rf = this.roleFilter();
    if (rf !== 'All roles') {
      users = users.filter(u => u.roleId === rf);
    }
    const sf = this.statusFilter();
    if (sf !== 'All statuses') {
      users = users.filter(u => u.status === sf);
    }
    return users;
  });

    roleName(id: string): string {
      return this.admin.roles().find((r) => r.id === id)?.name ?? id;
    }

    getTone(status: string) {
      return statusTone(status);
    }

    getInitials(name: string): string {
      if (!name) return 'AU';
      const parts = name.trim().split(/\s+/);
      if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
      return name.slice(0, 2).toUpperCase();
    }

    getRoleDescription(roleId: string): string {
      switch (roleId) {
        case 'ADMIN':
          return 'Full access: All modules (Inventory, Sales, Content, Leads, Analytics, Users, Roles).';
        case 'SALES':
          return 'Sales & Leads access: Dashboard, Orders, Customers, Distributor Applications, Quotes, and Inquiries.';
        case 'OPERATIONS':
          return 'Logistics access: Dashboard, Orders, Inventory Overview, Products, Stock In, Stock Adjustments, Movements, Low/Out of Stock.';
        case 'FINANCE':
          return 'Finance access: Dashboard, Orders, and Business Analytics.';
        case 'CONTENT_EDITOR':
          return 'Content Editor access: Dashboard, CMS Pages, Reviews, and Blog Posts.';
        case 'CONTENT_PUBLISHER':
          return 'Content Publisher access: Dashboard, CMS Pages, Reviews, and Blog Posts.';
        case 'ANALYST':
          return 'Analyst access: Dashboard and Business Analytics.';
        default:
          return 'Standard staff access.';
      }
    }

    openNewUser() {
      this.isNewUserOpen.set(true);
      this.creationMode.set('promote');
      this.newName.set('');
      this.newEmail.set('');
      this.newPassword.set('NigsonAdmin@2026');
      this.selectedPromoteEmail.set('');
      this.newRole.set('SALES');
      this.newStatus.set('Active');
      this.newReason.set('Staff provisioning by administrator');
      this.newError.set(null);
      this.newSuccess.set(null);
    }

    submitStaffUser() {
      if (this.creationMode() === 'create') {
        this.createStaffUser();
      } else {
        this.promoteStaffUser();
      }
    }

    createStaffUser() {
      const name = this.newName().trim();
      const email = this.newEmail().trim().toLowerCase();
      const password = this.newPassword().trim();

        if (!name) {
          this.newError.set('Please enter the user full name.');
          return;
        }
        if (!email || !email.includes('@')) {
          this.newError.set('Please enter a valid work email address.');
          return;
        }
        if (!password || password.length < 12) {
          this.newError.set('Temporary password must be at least 12 characters.');
          return;
        }
        if (this.admin.admins().some(a => a.email.toLowerCase() === email)) {
          this.newError.set('An admin user with this email already exists.');
          return;
        }

        this.newSaving.set(true);
        this.newError.set(null);

        const newUser: AdminUser = {
          id: crypto.randomUUID(),
          name: name,
          email: email,
          roleId: this.newRole(),
          status: this.newStatus(),
          lastLogin: new Date().toISOString(),
          version: undefined
        };

      this.admin.addStaffUser(newUser, password, this.newReason()).subscribe({
        next: () => {
          this.newSaving.set(false);
          this.newSuccess.set(`Account created! Temporary password: ${password}`);
          setTimeout(() => {
            this.isNewUserOpen.set(false);
            this.newSuccess.set(null);
          }, 1200);
        },
        error: (e) => {
          this.newSaving.set(false);
          this.newError.set('Failed to create user in Keycloak.');
        }
      });
    }

    promoteStaffUser() {
      const email = this.selectedPromoteEmail().trim();
      if (!email) {
        this.newError.set('Please select a registered user to promote.');
        return;
      }
      this.newSaving.set(true);
      this.newError.set(null);

        const promotedUser: AdminUser = {
          id: '',
          name: email,
          email,
          roleId: this.newRole(),
          status: 'Active',
          lastLogin: new Date().toISOString(),
          version: undefined
        };

        this.admin.addStaffUser(promotedUser, undefined, this.newReason()).subscribe({
          next: () => {
            this.newSaving.set(false);
            this.newSuccess.set(`${email} was assigned staff access successfully.`);
            setTimeout(() => {
              this.isNewUserOpen.set(false);
              this.newSuccess.set(null);
            }, 1000);
          },
          error: (e) => {
            this.newSaving.set(false);
            this.newError.set(this.admin.message(e));
          }
        });
      }

  editUser(u: AdminUser) {
    this.editingUser.set(u);
    this.selectedRole.set(u.roleId);
    this.selectedRoles.set([...(u.roles||[u.roleId])]);
    this.selectedStatus.set(u.status || 'Active');
    this.updateReason.set('Administrative role assignment update');
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  closeEditModal() {
    this.editingUser.set(null);
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  saveRole() {
    const user = this.editingUser();
    if (!user) return;
    const reason = this.updateReason().trim();
    if (!reason || reason.length < 10) {
      this.errorMessage.set('Please provide a reason of at least 10 characters for audit compliance.');
      return;
    }

    this.saving.set(true);
    this.errorMessage.set(null);
    const updatedUser: AdminUser = {
      ...user,
      roleId: this.selectedRoles()[0],
      roles: this.selectedRoles(),
      status: this.selectedStatus()
    };

    this.admin.updateStaffUser(updatedUser, reason).subscribe({
      next: (ok) => {
        this.saving.set(false);
        if (ok) {
          this.successMessage.set('User role and status updated successfully.');
          setTimeout(() => {
            this.closeEditModal();
          }, 600);
        } else {
          this.errorMessage.set('Failed to update user role on server.');
        }
      },
      error: (e) => {
        this.saving.set(false);
        this.errorMessage.set(this.admin.message(e));
      }
    });
  }
}

import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, AdminHeadingComponent, BadgeComponent, statusTone, StatCardComponent, AdminInquiry, isPartnerSubmission } from 'shared-ui';
import { LucideInfo, LucideX, LucideSearch } from '@lucide/angular';

@Component({
  selector: 'app-admin-inquiries',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    AdminHeadingComponent,
    BadgeComponent,
    StatCardComponent,
    LucideInfo,
    LucideX,
    LucideSearch
  ],
  template: `
    <lib-admin-heading title="Contact Inquiries" subtitle="Customer questions, complaints and support requests"></lib-admin-heading>
    
    <div class="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <lib-stat-card label="Total Inquiries" [value]="contactInquiries().length.toString()"></lib-stat-card>
      <lib-stat-card label="New" [value]="getStat('New').toString()"></lib-stat-card>
      <lib-stat-card label="In Progress" [value]="getStat('In Progress').toString()"></lib-stat-card>
      <lib-stat-card label="Resolved" [value]="getStat('Resolved').toString()"></lib-stat-card>
    </div>

    <!-- Filters -->
    <div class="mb-5 flex flex-wrap gap-3">
      <div class="relative w-[320px]">
        <svg lucideSearch class="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"></svg>
        <input
          type="text"
          [(ngModel)]="searchQuery"
          placeholder="Search inquiries..."
          class="w-full rounded-full border border-gray-200 bg-white py-2 pl-10 pr-4 text-sm text-ink shadow-sm outline-none transition-all focus:border-gray-300 focus:ring-4 focus:ring-gray-100"
        />
      </div>
      <select
        [(ngModel)]="statusFilter"
        class="rounded-full border border-gray-200 bg-white px-5 py-2 text-sm text-ink shadow-sm outline-none focus:border-gray-300 focus:ring-4 focus:ring-gray-100"
      >
        <option *ngFor="let s of statuses" [value]="s">{{ s }}</option>
      </select>
    </div>

    <div class="rounded-2xl border border-hairline bg-white shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[720px] text-left text-sm text-ink">
          <thead class="border-b border-hairline bg-surface-alt/50 text-[10px] font-bold uppercase tracking-widest text-muted-ink">
            <tr>
              <th class="px-6 py-4">Sender</th>
              <th class="px-6 py-4">Subject & Message</th>
              <th class="px-6 py-4">Status</th>
              <th class="px-6 py-4">Date</th>
              <th class="px-6 py-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-hairline">
            <tr *ngFor="let i of filteredList()" class="transition-colors hover:bg-surface-alt/30">
              <td class="px-6 py-4">
                <p class="font-bold text-ink">{{ i.name }}</p>
                <p class="text-xs text-muted-ink">{{ i.email }}</p>
                <p class="text-xs text-muted-ink" *ngIf="i.phone">{{ i.phone }}</p>
              </td>
              <td class="px-6 py-4">
                <p class="font-semibold text-ink">{{ i.subject }}</p>
                <p class="mt-1 line-clamp-2 max-w-md text-xs text-muted-ink whitespace-pre-line">{{ i.message }}</p>
              </td>
              <td class="px-6 py-4">
                <lib-badge [tone]="getTone(i.status)">{{ i.status }}</lib-badge>
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-xs text-muted-ink">{{ i.date | date:'mediumDate' }}</td>
              <td class="px-6 py-4 text-right">
                <div class="flex items-center justify-end gap-2">
                  <button
                    (click)="viewDetail(i)"
                    class="grid h-8 w-8 place-items-center rounded-full border border-gray-200 bg-white text-gray-500 hover:bg-brand/10 hover:text-brand hover:border-brand/30 transition-all shadow-sm"
                    title="View Complete Details"
                  >
                    <svg lucideInfo class="h-4 w-4"></svg>
                  </button>
                  <button
                    *ngIf="i.status !== 'Resolved'"
                    (click)="resolve(i.id)"
                    class="inline-flex items-center rounded-full bg-surface-alt px-3 py-1.5 text-xs font-semibold hover:bg-green-50 hover:text-green-600 transition-colors"
                  >
                    Mark Resolved
                  </button>
                </div>
              </td>
            </tr>
            <tr *ngIf="filteredList().length === 0">
              <td colspan="5" class="p-8 text-center text-muted-ink">No contact inquiries found matching criteria.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Complete Inquiry Details Modal -->
    <div *ngIf="view()" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 backdrop-blur-sm p-4 animate-in fade-in duration-200">
      <div class="w-full max-w-2xl rounded-[24px] border border-gray-100 bg-white p-6 sm:p-8 shadow-2xl animate-in zoom-in-95 duration-200">
        <!-- Header -->
        <div class="mb-6 flex items-center justify-between border-b border-gray-100 pb-4">
          <div class="flex items-center gap-3">
            <div class="grid h-10 w-10 place-items-center rounded-full bg-brand/10 text-brand">
              <svg lucideInfo class="h-5 w-5"></svg>
            </div>
            <div>
              <h2 class="text-lg font-extrabold text-ink">Inquiry Details</h2>
              <p class="text-xs text-muted-ink">Received {{ view()?.date | date:'medium' }}</p>
            </div>
          </div>
          <button (click)="view.set(null)" class="grid h-8 w-8 place-items-center rounded-full bg-gray-50 text-gray-500 hover:bg-gray-100 transition-colors">
            <svg lucideX class="h-4 w-4"></svg>
          </button>
        </div>

        <!-- Details Grid -->
        <div class="space-y-4 text-sm" *ngIf="view() as inq">
          <div class="grid gap-3 sm:grid-cols-2">
            <div class="rounded-xl border border-gray-100 px-4 py-3 bg-surface-alt/40">
              <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Sender Name</p>
              <p class="mt-1 font-semibold text-ink">{{ inq.name }}</p>
            </div>
            <div class="rounded-xl border border-gray-100 px-4 py-3 bg-surface-alt/40">
              <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Status</p>
              <div class="mt-1">
                <lib-badge [tone]="getTone(inq.status)">{{ inq.status }}</lib-badge>
              </div>
            </div>
            <div class="rounded-xl border border-gray-100 px-4 py-3 bg-surface-alt/40">
              <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Email Address</p>
              <a [href]="'mailto:' + inq.email" class="mt-1 block font-semibold text-brand hover:underline">{{ inq.email }}</a>
            </div>
            <div class="rounded-xl border border-gray-100 px-4 py-3 bg-surface-alt/40">
              <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Phone Number</p>
              <p class="mt-1 font-semibold text-ink">{{ inq.phone || 'Not provided' }}</p>
            </div>
          </div>

          <!-- Subject -->
          <div class="rounded-xl border border-gray-100 px-4 py-3 bg-surface-alt/40">
            <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Subject</p>
            <p class="mt-1 font-semibold text-ink">{{ inq.subject }}</p>
          </div>

          <!-- Full Message Body -->
          <div>
            <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink mb-1.5">Complete Message Content</p>
            <div class="rounded-2xl border border-gray-200 bg-gray-50/70 p-5 text-sm text-ink leading-relaxed whitespace-pre-wrap max-h-60 overflow-y-auto">
              {{ inq.message }}
            </div>
          </div>

          <!-- Action Footer -->
          <div class="flex flex-wrap items-center justify-between gap-3 pt-4 border-t border-gray-100">
            <div class="flex items-center gap-2">
              <span class="text-xs font-semibold text-muted-ink">Status:</span>
              <select
                [ngModel]="inq.status"
                (ngModelChange)="setStatus(inq.id, $event)"
                class="rounded-full border border-gray-200 bg-white px-3.5 py-1.5 text-xs font-semibold text-ink shadow-sm outline-none focus:border-brand focus:ring-2 focus:ring-brand/20"
              >
                <option value="New">New</option>
                <option value="In Progress">In Progress</option>
                <option value="Resolved">Resolved</option>
              </select>
            </div>
            <div class="flex items-center gap-2">
              <button
                *ngIf="inq.status !== 'Resolved'"
                (click)="resolve(inq.id)"
                class="rounded-full bg-emerald-600 px-5 py-2 text-xs font-bold text-white hover:bg-emerald-700 shadow-sm transition-colors"
              >
                Mark Resolved
              </button>
              <button
                (click)="view.set(null)"
                class="rounded-full border border-gray-200 bg-white px-5 py-2 text-xs font-semibold text-ink hover:bg-gray-50 transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminInquiriesComponent implements OnInit {
  admin = inject(AdminService);

  searchQuery = signal('');
  statusFilter = signal('All statuses');
  statuses = ['All statuses', 'New', 'In Progress', 'Resolved'];
  view = signal<AdminInquiry | null>(null);

  ngOnInit() {
    this.admin.hydrateFromStorage();
    this.admin.loadStaffInquiries().subscribe();
  }

  /**
   * Strictly filters out any partner applications (Wholesaler, Distributor, Affiliate Marketer).
   * Only genuine contact messages, support inquiries, and complaints are included.
   */
  contactInquiries = computed(() => {
    return this.admin.inquiries().filter(i => !isPartnerSubmission(i));
  });

  filteredList = computed(() => {
    let list = this.contactInquiries();
    const q = this.searchQuery().toLowerCase().trim();
    const s = this.statusFilter();

    if (q) {
      list = list.filter(i =>
        (i.name + ' ' + i.email + ' ' + (i.phone || '') + ' ' + i.subject + ' ' + i.message).toLowerCase().includes(q)
      );
    }

    if (s !== 'All statuses') {
      list = list.filter(i => i.status === s);
    }

    return list;
  });

  getTone(status: string) {
    return statusTone(status);
  }

  getStat(status: string) {
    return this.contactInquiries().filter(i => i.status === status).length;
  }

  viewDetail(inq: AdminInquiry) {
    this.view.set({ ...inq });
  }

  setStatus(id: string, status: 'New' | 'In Progress' | 'Resolved') {
    this.admin.setInquiryStatus(id, status);
    if (this.view()?.id === id) {
      this.view.update(v => v ? { ...v, status } : null);
    }
  }

  resolve(id: string) {
    this.setStatus(id, 'Resolved');
  }
}

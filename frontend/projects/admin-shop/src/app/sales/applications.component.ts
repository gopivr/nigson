import { Component, computed, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize, Subscription } from 'rxjs';
import { AdminService, AdminHeadingComponent, BadgeComponent, StatCardComponent, statusTone, distributorActionBlock, distributorDisplayState, leadDisplayState, partnerLeadActionAllowed, applicationFailure } from 'shared-ui';
import type { AdminApplication } from 'shared-ui';

@Component({
  selector: 'app-admin-applications', standalone: true,
  imports: [CommonModule, FormsModule, AdminHeadingComponent, BadgeComponent, StatCardComponent],
  template: `
  <div class="flex flex-wrap items-center justify-between gap-3">
   <lib-admin-heading title="Partner Applications" subtitle="Review partner applications and legacy lead requests."></lib-admin-heading>
   <button type="button" (click)="refresh()" [disabled]="admin.loadingApplications() || !!busy()" class="rounded-full border border-hairline bg-white px-4 py-2 text-sm font-semibold disabled:opacity-50">{{ admin.loadingApplications() ? 'Refreshing...' : 'Refresh status' }}</button>
  </div>
  <p *ngIf="admin.applicationError()" role="alert" class="mb-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">{{ admin.applicationError() }}</p>
  <div class="mb-6 flex flex-wrap gap-3 border-b border-hairline" role="group" aria-label="Partner request type">
   <button *ngFor="let name of tabs" type="button" (click)="changeTab(name)" [attr.aria-pressed]="tab()===name" [class.text-brand]="tab()===name" class="border-b-2 px-2 py-3 text-sm font-bold">{{ name }}</button>
  </div>
  <p class="mb-4 text-sm text-muted-ink">{{ tab()!=='Affiliate Marketers' ? 'New applications require document review and independent approval. Legacy contact requests remain leads; resolving a lead grants no business access.' : 'New affiliate applications can be approved or rejected here. Legacy contact leads do not grant access.' }}</p>
  <div class="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
   <lib-stat-card *ngFor="let stat of stats()" [label]="stat.label" [value]="stat.value"></lib-stat-card>
  </div>
  <div class="mb-5 flex flex-wrap gap-3">
   <input type="search" [(ngModel)]="searchQuery" aria-label="Search partner requests" placeholder="Search business, contact or reference..." class="w-full max-w-sm rounded-full border border-hairline bg-white px-4 py-2 text-sm">
   <select [(ngModel)]="catFilter" aria-label="Filter by category" class="rounded-full border border-hairline bg-white px-4 py-2 text-sm"><option value="All categories">All categories</option><option *ngFor="let category of categories()" [value]="category">{{ category }}</option></select>
   <select [(ngModel)]="statusFilter" aria-label="Filter by status" class="rounded-full border border-hairline bg-white px-4 py-2 text-sm"><option value="All">All statuses</option><option *ngFor="let status of statuses()" [value]="status">{{ status }}</option></select>
  </div>
  <div class="overflow-x-auto rounded-2xl border border-hairline bg-white">
   <table class="w-full min-w-[720px] text-left text-sm">
    <thead class="border-b border-hairline text-xs uppercase text-muted-ink"><tr><th class="p-4">Reference</th><th class="p-4">Business</th><th class="p-4">Contact</th><th class="p-4">Submitted</th><th class="p-4">Status</th><th class="p-4">Action</th></tr></thead>
    <tbody class="divide-y divide-hairline">
     <tr *ngFor="let application of filteredList()"><td class="p-4">{{ application.number }}</td><td class="p-4 font-semibold">{{ application.businessName }}</td><td class="p-4">{{ application.contactPerson }}<br><span class="text-xs text-muted-ink">{{ application.email }}</span></td><td class="p-4">{{ application.date | date:'mediumDate' }}</td><td class="p-4"><lib-badge [tone]="getTone(application.status)">{{ application.status }}</lib-badge></td><td class="p-4"><button type="button" (click)="viewDetail(application)" [disabled]="!!busy()" class="rounded-full border border-hairline px-4 py-2 font-semibold disabled:opacity-50">Open {{ application.source==='lead' ? 'request' : 'application' }}</button></td></tr>
     <tr *ngIf="!filteredList().length"><td colspan="6" class="p-10 text-center text-muted-ink">{{ admin.loadingApplications() ? 'Loading partner requests...' : 'No records match your filters.' }}</td></tr>
    </tbody>
   </table>
  </div>

  <div *ngIf="view(); let record" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-4">
   <section role="dialog" aria-modal="true" aria-labelledby="partner-dialog-title" class="max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl">
    <header class="mb-5 flex items-start justify-between gap-4 border-b border-hairline pb-4"><div><h2 id="partner-dialog-title" class="text-xl font-extrabold">{{ record.businessName || record.number }}</h2><p class="mt-1 text-sm text-muted-ink">{{ record.source==='lead' ? 'Partner lead request' : (record.partnerType==='AFFILIATE'?'Affiliate application':'Business application') }} ? {{ record.number }}</p></div><button type="button" (click)="close()" [disabled]="!!busy()" class="rounded-full border border-hairline px-4 py-2 text-sm disabled:opacity-50">Close</button></header>
    <p *ngIf="loadingDetail()" role="status" class="mb-4 text-sm">Loading the latest server record...</p>
    <p *ngIf="actionError()" role="alert" class="mb-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">{{ actionError() }}</p>
    <p *ngIf="success()" role="status" class="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">{{ success() }}</p>
    <dl class="mb-5 grid gap-4 text-sm sm:grid-cols-2"><div><dt class="text-muted-ink">Current status</dt><dd class="mt-1 font-semibold">{{ record.status }}</dd></div><div><dt class="text-muted-ink">Business registration</dt><dd>{{ record.businessReference || 'Not supplied' }}</dd></div><div><dt class="text-muted-ink">Contact</dt><dd>{{ record.contactPerson || 'Not supplied' }}<br>{{ record.email }}<br>{{ record.phone }}</dd></div><div><dt class="text-muted-ink">Location</dt><dd>{{ record.location || 'Not supplied' }}</dd></div></dl>
    <div *ngIf="record.note" class="mb-5 rounded-xl bg-surface-alt p-4 text-sm whitespace-pre-wrap">{{ record.note }}</div>

    <ng-container *ngIf="record.source!=='lead'">
     <section *ngIf="record.partnerType!=='AFFILIATE'" class="mb-5 rounded-xl border border-hairline p-4"><h3 class="font-bold">Submitted documents</h3>
      <p *ngIf="!record.documents?.length" class="mt-2 text-sm text-red-700">No document records were returned. Review cannot be completed.</p>
      <ul class="mt-3 space-y-3"><li *ngFor="let document of record.documents" class="rounded-lg bg-surface-alt p-3 text-sm"><strong>{{ document.type || 'Registration document' }}</strong><p class="break-all font-mono text-xs">{{ document.fileId || 'Missing file ID' }}</p><p class="mt-1">Scan: {{ document.scanStatus || 'Unknown' }}</p></li></ul>
      <p class="mt-3 text-xs text-muted-ink">Document contents cannot currently be opened by staff from this page. Confirm review only after inspecting the original documents; a CLEAN scan alone is not document verification.</p>
     </section>
     <section *ngIf="record.partnerType!=='AFFILIATE'" class="mb-5 rounded-xl border border-hairline p-4"><h3 class="font-bold">Recorded review</h3><p class="mt-2 text-sm">Reviewer: {{ reviewer(record) }}</p><p class="mt-2 whitespace-pre-wrap text-sm">{{ record.reviewReason || 'No review note recorded.' }}</p><p class="mt-2 text-xs text-muted-ink">This shows the latest recorded review.</p></section>
     <section *ngIf="record.decisionReason || record.rawState==='REJECTED'" class="mb-5 rounded-xl border border-hairline p-4"><h3 class="font-bold">Latest decision reason</h3><p class="mt-2 whitespace-pre-wrap text-sm">{{ record.decisionReason || 'Decision reason unavailable. Refresh this record or contact an administrator.' }}</p></section>
     <p *ngIf="processing(record)" role="status" class="mb-5 rounded-xl bg-amber-50 p-4 text-sm">{{ record.status }}: awaiting backend confirmation. Refresh status before attempting another decision.</p>
     <p *ngIf="(record.rawState==='APPROVED' && record.partnerType!=='AFFILIATE') || record.rawState==='REJECTED' || record.rawState==='SUSPENDED'" class="mb-5 text-sm">This application is {{ record.status.toLowerCase() }}. Review and approval actions are unavailable.</p>
    </ng-container>

    <div *ngIf="!loadingDetail() && editable(record)" class="border-t border-hairline pt-4">
     <label class="block text-sm font-bold" for="staff-action-note">{{ record.source==='lead' ? 'Lead update note' : 'Decision / review note' }} (required)</label>
     <textarea id="staff-action-note" [(ngModel)]="reviewerNote" maxlength="500" rows="3" [disabled]="!!busy()" class="mt-2 w-full rounded-xl border border-hairline p-3 text-sm" placeholder="Describe what you checked or why you are making this decision."></textarea>
     <ng-container *ngIf="record.source!=='lead'">
      <label *ngIf="record.rawState==='SUBMITTED' && record.partnerType!=='AFFILIATE'" class="mt-3 flex gap-2 text-sm"><input type="checkbox" [(ngModel)]="documentsVerified" [disabled]="!!busy() || !!block('Under Review')"> I inspected the registration documents and verified their contents.</label>
      <p *ngIf="record.rawState==='SUBMITTED' && record.partnerType!=='AFFILIATE' && block('Under Review')" class="mt-2 text-sm text-amber-800">{{ block('Under Review') }}</p>
      <p *ngIf="record.rawState!=='APPROVED' && record.rawState!=='SUSPENSION_PENDING' && block('Approved')" class="mt-3 text-sm text-muted-ink">{{ block('Approved') }}</p>
      <div class="mt-4 flex flex-wrap gap-3">
       <button *ngIf="record.partnerType==='AFFILIATE' && record.rawState==='APPROVED'" type="button" (click)="act('Suspended')" [disabled]="!!busy() || !validNote(reviewerNote)" class="rounded-full bg-red-700 px-5 py-2 text-white">Suspend affiliate</button>
       <button *ngIf="record.rawState==='ACTIVATION_PENDING' || record.partnerType==='AFFILIATE' && record.rawState==='SUSPENSION_PENDING'" type="button" (click)="reviewerNote='Retry saved activation';act('Retry activation')" [disabled]="!!busy() || !!block('Retry activation')" class="rounded-full bg-brand px-5 py-2.5 text-white">Retry saved decision</button><p *ngIf="record.rawState==='ACTIVATION_PENDING' || record.partnerType==='AFFILIATE' && record.rawState==='SUSPENSION_PENDING'" class="text-sm text-muted-ink">The original approver must retry. The saved decision and activation command are reused.</p>
       <button *ngIf="record.rawState==='SUBMITTED' && record.partnerType!=='AFFILIATE'" type="button" (click)="act('Under Review')" [disabled]="!!busy() || !!block('Under Review') || !documentsVerified || !validNote(reviewerNote)" class="rounded-full bg-amber-600 px-5 py-2.5 text-sm font-bold text-white disabled:opacity-40">{{ busy()==='Under Review' ? 'Recording review...' : 'Complete review' }}</button>
       <button *ngIf="record.rawState!=='APPROVED'" type="button" (click)="act('Approved')" [disabled]="!!busy() || !!block('Approved') || !validNote(reviewerNote)" class="rounded-full bg-emerald-700 px-5 py-2.5 text-sm font-bold text-white disabled:opacity-40">{{ busy()==='Approved' ? 'Submitting approval...' : 'Approve application' }}</button>
       <button *ngIf="record.rawState!=='APPROVED'" type="button" (click)="rejecting.set(true)" [disabled]="!!busy() || !!block('Rejected')" class="rounded-full border border-red-300 px-5 py-2.5 text-sm font-bold text-red-700 disabled:opacity-40">Reject...</button>
      </div>
      <section *ngIf="rejecting()" aria-label="Confirm rejection" class="mt-4 rounded-xl border border-red-200 bg-red-50 p-4"><h3 class="font-bold">Reject this application?</h3><label for="rejection-reason" class="mt-2 block text-sm">Rejection reason (required)</label><textarea id="rejection-reason" [(ngModel)]="rejectionReason" maxlength="500" rows="2" [disabled]="!!busy()" class="mt-2 w-full rounded-lg border border-red-200 p-3"></textarea><div class="mt-3 flex gap-3"><button type="button" (click)="act('Rejected')" [disabled]="!!busy() || !validNote(rejectionReason)" class="rounded-full bg-red-700 px-4 py-2 text-sm font-bold text-white disabled:opacity-40">Confirm rejection</button><button type="button" (click)="rejecting.set(false)" [disabled]="!!busy()" class="rounded-full border border-hairline bg-white px-4 py-2 text-sm">Cancel</button></div></section>
     </ng-container>
     <div *ngIf="record.source==='lead'" class="mt-4"><p class="mb-3 text-sm text-muted-ink">These actions update this lead only. They do not approve a distributor account.</p><div class="flex flex-wrap gap-3"><button *ngFor="let action of leadActions" type="button" (click)="act(action)" [disabled]="!!busy() || !validNote(reviewerNote) || !leadAllowed(record,action)" class="rounded-full border border-hairline px-5 py-2.5 text-sm font-bold disabled:opacity-40">{{ busy()===action ? 'Saving...' : action }}</button></div></div>
    </div>
    <div class="mt-5 border-t border-hairline pt-4"><button type="button" (click)="reloadDetail()" [disabled]="!!busy() || loadingDetail()" class="rounded-full border border-hairline px-4 py-2 text-sm disabled:opacity-40">Refresh this record</button></div>
   </section>
  </div>
 `
})
export class AdminApplicationsComponent implements OnInit, OnDestroy {
  private detailRequest?: Subscription;
  readonly detailReady = signal(false);
  readonly admin = inject(AdminService);
  readonly tabs = ['Distributors', 'Wholesalers', 'Affiliate Marketers'];
  readonly leadActions = ['Start work', 'Resolve', 'Close'];
  readonly tab = signal('Distributors'); readonly searchQuery = signal(''); readonly statusFilter = signal('All'); readonly catFilter = signal('All categories');
  readonly view = signal<AdminApplication | null>(null); readonly busy = signal<string | null>(null); readonly loadingDetail = signal(false);
  readonly actionError = signal(''); readonly success = signal(''); readonly rejecting = signal(false);
  reviewerNote = ''; rejectionReason = ''; documentsVerified = false;
  readonly tabRows = computed(() => this.admin.applications().filter(row => row.type === (this.tab() === 'Distributors' ? 'Distributor' : this.tab() === 'Wholesalers' ? 'Wholesaler' : 'Affiliate Marketer')));
  readonly categories = computed(() => [...new Set(this.tabRows().map(row => row.category).filter(Boolean))]);
  readonly statuses = computed(() => ['New', 'Assigned', 'In Progress', 'Resolved', 'Closed', 'Pending', 'Under Review', 'Activation Pending', 'Approved', 'Rejected', 'Suspension Pending', 'Suspended', 'Unknown']);
  readonly stats = computed(() => {
    const rows = this.tabRows(); const count = (states: string[]) => rows.filter(row => states.includes(row.rawState ?? '')).length;
    return [{ label: 'Applications', value: rows.length }, { label: 'Awaiting review', value: count(['SUBMITTED']) }, { label: 'Under review', value: count(['UNDER_REVIEW']) }, { label: 'Processing', value: count(['ACTIVATION_PENDING', 'SUSPENSION_PENDING']) }, { label: 'Approved', value: count(['APPROVED']) }, { label: 'Rejected', value: count(['REJECTED']) }];
  });
  readonly filteredList = computed(() => { const query = this.searchQuery().toLowerCase(); return this.tabRows().filter(row => (this.statusFilter() === 'All' || row.status === this.statusFilter()) && (this.catFilter() === 'All categories' || row.category === this.catFilter()) && [row.businessName, row.contactPerson, row.email, row.phone, row.location, row.number, row.businessReference ?? ''].join(' ').toLowerCase().includes(query)); });
  ngOnInit(): void { this.refresh(); }
  ngOnDestroy(): void { this.detailRequest?.unsubscribe(); }
  changeTab(tab: string): void { if (this.busy()) return; this.tab.set(tab); this.statusFilter.set('All'); this.catFilter.set('All categories'); }
  refresh(): void { if (this.busy()) return; this.admin.loadStaffApplications().subscribe({ error: error => this.actionError.set(applicationFailure(error)) }); }
  getTone(status: string) { return statusTone(status); }
  validNote(note: string): boolean { return !!note.trim() && note.trim().length <= 500; }
  processing(record: AdminApplication): boolean { return ['ACTIVATION_PENDING', 'SUSPENSION_PENDING'].includes(record.rawState ?? ''); }
  editable(record: AdminApplication): boolean { if (!this.detailReady()) return false; return record.source === 'lead' ? ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED'].includes(record.rawState ?? '') : (['SUBMITTED', 'UNDER_REVIEW', 'ACTIVATION_PENDING'].includes(record.rawState ?? '') || record.partnerType === 'AFFILIATE' && ['APPROVED', 'SUSPENSION_PENDING'].includes(record.rawState ?? '')); }
  reviewer(record: AdminApplication): string { const id = record.reviewerPartyId; if (!id) return 'Not recorded'; if (id === this.admin.session.userId()) return 'You (staff ID ' + id + ')'; return this.admin.admins().find(user => user.id === id)?.name ?? ('Staff ID ' + id); }
  block(action: string): string | null { const row = this.view(); if (!row) return 'Open an application first.'; if (!this.admin.session.hasRole('SALES')) return 'Sales access is required.'; return distributorActionBlock({ partnerType: row.partnerType, state: row.rawState, reviewerPartyId: row.reviewerPartyId, documents: row.documents }, action, this.admin.session.userId(), this.admin.distributorMarket()?.id); }
  leadAllowed(record: AdminApplication, action: string): boolean { return this.admin.session.hasRole('SALES') && partnerLeadActionAllowed(record.rawState ?? '', action); }
  close(): void { if (this.busy()) return; this.detailRequest?.unsubscribe(); this.loadingDetail.set(false); this.detailReady.set(false); this.view.set(null); this.actionError.set(''); this.success.set(''); }
  viewDetail(record: AdminApplication): void { if (this.busy()) return; this.view.set({ ...record }); this.reviewerNote = ''; this.rejectionReason = ''; this.documentsVerified = false; this.rejecting.set(false); this.actionError.set(''); this.success.set(''); this.reloadDetail(); }
  reloadDetail(): void { const row = this.view(); if (!row || this.busy()) return; this.detailRequest?.unsubscribe(); this.detailReady.set(false); this.documentsVerified = false; this.loadingDetail.set(true); this.actionError.set(''); this.detailRequest = this.admin.refreshApplication(row.id).pipe(finalize(() => this.loadingDetail.set(false))).subscribe({ next: record => { if (this.view()?.id === record.id) { this.view.set({ ...record }); this.detailReady.set(true); } }, error: error => this.actionError.set(applicationFailure(error)) }); }
  act(action: string): void {
    const row = this.view(); if (!row || this.busy() || this.loadingDetail() || !this.detailReady()) return;
    if (action === 'Rejected' && !this.rejecting()) { this.actionError.set('Confirm rejection and enter its reason first.'); return; }
    const note = action === 'Rejected' ? this.rejectionReason : this.reviewerNote;
    const blocked = row.source === 'lead' ? (this.leadAllowed(row, action) ? null : 'This lead action is not available.') : this.block(action);
    if (blocked || !this.validNote(note) || (action === 'Under Review' && !this.documentsVerified)) { this.actionError.set(blocked ?? 'Enter a staff reason and confirm document inspection where required.'); return; }
    this.actionError.set(''); this.success.set(''); this.busy.set(action);
    this.admin.setApplicationStatus(row.id, action, note, this.documentsVerified, row.version).pipe(finalize(() => this.busy.set(null))).subscribe({
      next: result => {
        const latest = this.admin.applications().find(item => item.id === row.id);
        if (latest && (result?.version == null || (latest.version ?? -1) >= result.version)) this.view.set({ ...latest });
        else if (result?.state) this.view.set({ ...row, rawState: result.state, status: row.source === 'lead' ? leadDisplayState(result.state) : distributorDisplayState(result.state), version: result.version });
        this.documentsVerified = false; this.reviewerNote = ''; this.rejectionReason = ''; this.rejecting.set(false);
        const current = this.view(); this.success.set(current && this.processing(current) ? 'The backend is processing this decision; approval is not yet confirmed.' : 'Server update confirmed: ' + (current?.status ?? 'updated') + '.');
      }, error: error => { this.detailReady.set(false); this.actionError.set(applicationFailure(error)); }
    });
  }
}

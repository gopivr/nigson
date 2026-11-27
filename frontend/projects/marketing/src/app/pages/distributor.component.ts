import { Component, effect, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom, tap, throwError, finalize, catchError } from 'rxjs';
import { SessionService, MarketingService, BackendDistributorApplication, DistributorApplicationPayload, apiFailureDetail, normalizePhoneNumber } from 'shared-ui';
import {
  LucideCheckCircle2, LucideBuilding,
  LucideUsers, LucideLoader2, LucideShieldCheck,
  LucideLayers
} from '@lucide/angular';

interface FileUploadSession {
  assetId: string;
  uploadUrl: string;
  requiredHeaders: Record<string, string>;
}

interface UploadedFile {
  id: string;
  state: string;
}

interface FileDownload {
  url: string;
}

function problemField(error: unknown, field: string): string | undefined {
  if (!(error instanceof HttpErrorResponse) || typeof error.error !== 'object' || error.error === null) {
    return undefined;
  }
  const value = (error.error as Record<string, unknown>)[field];
  return typeof value === 'string' ? value : undefined;
}

@Component({
  selector: 'app-distributor',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    LucideCheckCircle2, LucideBuilding,
    LucideUsers, LucideLoader2, LucideShieldCheck,
    LucideLayers
  ],
  template: `
    <div class="bg-surface min-h-screen">
      <!-- Page Header -->
      <section class="border-b border-border bg-background py-8 md:py-12">
        <div class="container-page max-w-5xl text-center">
          <span class="inline-flex items-center gap-1.5 rounded-full bg-brand/10 px-3.5 py-1 text-xs font-bold text-brand uppercase tracking-wider mb-4">
            Commercial Partner Network
          </span>
          <h1 class="text-4xl md:text-5xl font-extrabold tracking-tight text-ink">Become a Nigson Partner</h1>
          <p class="mt-4 text-base md:text-lg text-muted-foreground max-w-2xl mx-auto">
            Choose your partnership model. Expand your business with West Africa's leading supplier of consumer electronics, power solutions, and fast-moving accessories.
          </p>

          <!-- 3-Way Tab Switcher: Wholesaler | Distributor | Affiliate Marketer -->
          <div class="mt-10 inline-flex flex-wrap p-1.5 rounded-2xl bg-surface-alt border border-hairline shadow-sm gap-1">
            <button
              type="button"
              (click)="switchTab('wholesaler')"
              [ngClass]="activeTab() === 'wholesaler' ? 'bg-white text-brand shadow-sm font-bold' : 'text-muted-ink hover:text-ink font-semibold'"
              class="flex items-center gap-2 rounded-xl px-5 py-3 text-sm transition-all"
            >
              <svg lucideBuilding class="h-4 w-4"></svg>
              <span>Wholesaler</span>
            </button>
            <button
              type="button"
              (click)="switchTab('distributor')"
              [ngClass]="activeTab() === 'distributor' ? 'bg-white text-brand shadow-sm font-bold' : 'text-muted-ink hover:text-ink font-semibold'"
              class="flex items-center gap-2 rounded-xl px-5 py-3 text-sm transition-all"
            >
              <svg lucideLayers class="h-4 w-4"></svg>
              <span>Distributor</span>
            </button>
            <button
              type="button"
              (click)="switchTab('affiliate')"
              [ngClass]="activeTab() === 'affiliate' ? 'bg-white text-brand shadow-sm font-bold' : 'text-muted-ink hover:text-ink font-semibold'"
              class="flex items-center gap-2 rounded-xl px-5 py-3 text-sm transition-all"
            >
              <svg lucideUsers class="h-4 w-4"></svg>
              <span>Affiliate Marketer</span>
            </button>
          </div>
        </div>
      </section>

      <section class="container-page py-14 grid gap-12 lg:grid-cols-3 max-w-7xl">
        <!-- Sidebar Benefits -->
        <div class="space-y-6 lg:col-span-1">
          <div class="rounded-2xl border border-hairline bg-white p-6 shadow-sm">
            <!-- Wholesaler Benefits -->
            <div *ngIf="activeTab() === 'wholesaler'">
              <span class="text-xs font-bold uppercase tracking-wider text-brand">Trade & Bulk Sourcing</span>
              <h2 class="text-xl font-bold text-ink mt-1">Wholesale Benefits</h2>
              <p class="mt-2 text-xs text-muted-foreground">Apply for wholesale purchasing. Approval and pricing assignment are separate steps.</p>
              <ul class="mt-6 space-y-3.5 text-sm text-ink/80">
                <li class="flex gap-3">
                  <svg lucideCheckCircle2 class="h-5 w-5 text-brand flex-none mt-0.5"></svg>
                  <span><strong>Assigned wholesale prices:</strong> Staff configure your applicable prices after approval.</span>
                </li>
                <li class="flex gap-3">
                  <svg lucideCheckCircle2 class="h-5 w-5 text-brand flex-none mt-0.5"></svg>
                  <span><strong>Purchase quantities:</strong> Product minimums and pack quantities apply to business orders.</span>
                </li>
                <li class="flex gap-3">
                  <svg lucideCheckCircle2 class="h-5 w-5 text-brand flex-none mt-0.5"></svg>
                  <span><strong>Application tracking:</strong> Follow the review status from your customer account.</span>
                </li>
                <li class="flex gap-3">
                  <svg lucideCheckCircle2 class="h-5 w-5 text-brand flex-none mt-0.5"></svg>
                  <span><strong>Verified business:</strong> Submit registration proof for document review and independent approval.</span>
                </li>
              </ul>
            </div>

            <!-- Distributor Credentials -->
            <div *ngIf="activeTab() === 'distributor'">
              <span class="text-xs font-bold uppercase tracking-wider text-brand">Regional Enterprise Network</span>
              <h2 class="text-xl font-bold text-ink mt-1">Distributor Privileges</h2>
              <p class="mt-2 text-xs text-muted-foreground">Apply for distributor purchasing. Approval does not automatically grant territory exclusivity or credit.</p>
              <ul class="mt-6 space-y-3.5 text-sm text-ink/80">
                <li class="flex gap-3">
                  <svg lucideCheckCircle2 class="h-5 w-5 text-brand flex-none mt-0.5"></svg>
                  <span><strong>Business review:</strong> Staff review your company registration and requested territory.</span>
                </li>
                <li class="flex gap-3">
                  <svg lucideCheckCircle2 class="h-5 w-5 text-brand flex-none mt-0.5"></svg>
                  <span><strong>Distributor pricing:</strong> Your approved organization receives its assigned price book.</span>
                </li>
                <li class="flex gap-3">
                  <svg lucideCheckCircle2 class="h-5 w-5 text-brand flex-none mt-0.5"></svg>
                  <span><strong>Account-linked access:</strong> Partner access stays linked to your existing customer account.</span>
                </li>
                <li class="flex gap-3">
                  <svg lucideCheckCircle2 class="h-5 w-5 text-brand flex-none mt-0.5"></svg>
                  <span><strong>Explicit commercial terms:</strong> Staff assign prices and minimum quantities; approval alone does not enable credit.</span>
                </li>
              </ul>
            </div>

            <!-- Affiliate Benefits -->
            <div *ngIf="activeTab() === 'affiliate'">
              <span class="text-xs font-bold uppercase tracking-wider text-brand">Creators & Promoters</span>
              <h2 class="text-xl font-bold text-ink mt-1">Affiliate Benefits</h2>
              <p class="mt-2 text-xs text-muted-foreground">Applications are open. Referral links and earnings will become available when the programme launches.</p>
              <ul class="mt-6 space-y-3.5 text-sm text-ink/80">
                <li class="flex gap-3">
                  <svg lucideCheckCircle2 class="h-5 w-5 text-brand flex-none mt-0.5"></svg>
                  <span><strong>5% Commission:</strong> Programme rate on eligible retail merchandise after discounts, excluding shipping and tax.</span>
                </li>
                <li class="flex gap-3">
                  <svg lucideCheckCircle2 class="h-5 w-5 text-brand flex-none mt-0.5"></svg>
                  <span><strong>7-Day Referral Window:</strong> The latest valid referral link in the same browser receives credit.</span>
                </li>
                <li class="flex gap-3">
                  <svg lucideCheckCircle2 class="h-5 w-5 text-brand flex-none mt-0.5"></svg>
                  <span><strong>Monthly Bank Payouts:</strong> Available commissions will be paid together after payout details are reviewed.</span>
                </li>
                <li class="flex gap-3">
                  <svg lucideCheckCircle2 class="h-5 w-5 text-brand flex-none mt-0.5"></svg>
                  <span><strong>After Delivery:</strong> Commission becomes available seven days after delivery, subject to refund rules.</span>
                </li>
              </ul>
            </div>
          </div>

          <div class="rounded-2xl border border-hairline bg-surface-alt p-5 text-xs text-muted-ink space-y-2">
            <div class="flex items-center gap-2 font-bold text-ink">
              <svg lucideShieldCheck class="h-4 w-4 text-brand"></svg>
              <span>Direct Commercial Review</span>
            </div>
            <p>Applications are reviewed directly by our commercial operations desk with 24-hour response SLA across Nigerian trade zones.</p>
          </div>
        </div>

        <!-- Forms Area -->
        <div class="lg:col-span-2">
          <!-- ERROR BANNER -->
          <div *ngIf="submitError()" class="mb-6 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-800 space-y-2">
            <div class="flex items-start justify-between">
              <div class="flex items-start gap-2">
                <span class="text-base font-bold text-red-600">✕</span>
                <div>
                  <p class="font-bold">Submission Notice</p>
                  <p class="text-xs text-red-700 mt-0.5 whitespace-pre-line">{{ submitError() }}</p>
                </div>
              </div>
              <button type="button" (click)="submitError.set(null)" class="text-red-600 font-bold ml-2 hover:text-red-800">✕</button>
            </div>
          </div>

          <!-- ================= TAB 1: WHOLESALER FORM ================= -->
          <div *ngIf="activeTab() === 'wholesaler'" class="rounded-2xl border border-hairline bg-white p-6 md:p-8 shadow-sm">
            <ng-container *ngIf="wholesaleSubmitted(); else wholesaleFormFields">
              <div class="text-center py-12 space-y-4">
                <div class="mx-auto grid h-16 w-16 place-items-center rounded-full bg-emerald-50 text-emerald-600 border border-emerald-100">
                  <svg lucideCheckCircle2 class="h-9 w-9"></svg>
                </div>
                <h3 class="text-2xl font-extrabold text-ink">Wholesale Application Received</h3>
                <p class="text-sm md:text-base text-muted-foreground max-w-lg mx-auto">
                  Thank you, <strong>{{ f.contact || f.company }}</strong>! Your application for <strong>{{ f.company }}</strong> has been recorded in the database. Track your application below. Business prices become available after review, approval and pricing assignment.
                </p>
                <div class="pt-4">
                  <button
                    type="button"
                    (click)="resetWholesale()"
                    class="inline-flex items-center gap-2 rounded-full border border-hairline px-6 py-2.5 text-xs font-semibold hover:border-brand hover:text-brand transition-colors"
                  >
                    Submit another wholesale application
                  </button>
                </div>
              </div>
            </ng-container>

            <ng-template #wholesaleFormFields>
              <div>
                <span class="text-xs font-bold uppercase tracking-wider text-brand">Bulk Trade Merchants</span>
                <h2 class="text-2xl font-bold text-ink mt-0.5">Wholesaler Application</h2>
                <p class="mt-1 text-sm text-muted-foreground">Register your shop, electronics retail outlet, or market distribution stall.</p>
              </div>

              <form (submit)="onWholesaleSubmit($event)" novalidate class="mt-6 space-y-5">
                <div class="grid gap-5 md:grid-cols-2">
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Business / Store Name <span class="text-brand">*</span></label>
                    <input type="text" [(ngModel)]="f.company" name="company" (input)="validateWholesaleField('company')" (blur)="validateWholesaleField('company')" placeholder="e.g. Alaba Electronics Mart" [ngClass]="wholesaleErrors()['company'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="wholesaleErrors()['company']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ wholesaleErrors()['company'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Contact Person Name <span class="text-brand">*</span></label>
                    <input type="text" [(ngModel)]="f.contact" name="contact" (input)="validateWholesaleField('contact')" (blur)="validateWholesaleField('contact')" placeholder="Full name" [ngClass]="wholesaleErrors()['contact'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="wholesaleErrors()['contact']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ wholesaleErrors()['contact'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Email Address <span class="text-brand">*</span></label>
                    <input type="email" [(ngModel)]="f.email" name="email" (input)="validateWholesaleField('email')" (blur)="validateWholesaleField('email')" placeholder="name@company.com" [ngClass]="wholesaleErrors()['email'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="wholesaleErrors()['email']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ wholesaleErrors()['email'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Phone / WhatsApp (Numbers Only) <span class="text-brand">*</span></label>
                    <input type="tel" [(ngModel)]="f.phone" name="phone" (keypress)="onPhoneKeyPress($event)" (input)="onPhoneInput('wholesale')" (blur)="validateWholesaleField('phone')" placeholder="+234 800 000 0000" [ngClass]="wholesaleErrors()['phone'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="wholesaleErrors()['phone']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ wholesaleErrors()['phone'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Trading Territory / State <span class="text-brand">*</span></label>
                    <input type="text" [(ngModel)]="f.state" name="state" (input)="validateWholesaleField('state')" (blur)="validateWholesaleField('state')" placeholder="e.g. Lagos (Alaba), Kano, Onitsha" [ngClass]="wholesaleErrors()['state'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="wholesaleErrors()['state']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ wholesaleErrors()['state'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Years of Operation</label>
                    <input type="text" [(ngModel)]="f.years" name="years" (keypress)="onDigitOnlyKeyPress($event)" (input)="onYearsInput('wholesale')" placeholder="e.g. 3" class="w-full rounded-xl border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                  </div>
                </div>

                <div>
                  <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Store / Warehouse Delivery Address <span class="text-brand">*</span></label>
                  <input type="text" [(ngModel)]="f.address" name="address" (input)="validateWholesaleField('address')" (blur)="validateWholesaleField('address')" placeholder="Physical shop or warehouse location for shipments" [ngClass]="wholesaleErrors()['address'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                  <p *ngIf="wholesaleErrors()['address']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ wholesaleErrors()['address'] }}</p>
                </div>

                <div class="grid gap-5 md:grid-cols-2">
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Primary Product Interest</label>
                    <select [(ngModel)]="f.category" name="category" class="w-full rounded-xl border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:outline-none focus:border-brand">
                      <option>Phone accessories (Chargers, Cables, Earbuds)</option>
                      <option>Power solutions (Power banks, Extension boxes)</option>
                      <option>Motion lights & smart home devices</option>
                      <option>Full Nigson product catalogue</option>
                    </select>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Estimated Monthly Order Volume</label>
                    <select [(ngModel)]="f.volume" name="volume" class="w-full rounded-xl border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:outline-none focus:border-brand">
                      <option>₦1,000,000 - ₦3,000,000 / month</option>
                      <option>₦3,000,000 - ₦10,000,000 / month</option>
                      <option>₦10,000,000 - ₦25,000,000 / month</option>
                      <option>₦25,000,000+ / month (Pallet volumes)</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Preferred Settlement Terms</label>
                  <select [(ngModel)]="f.paymentTerms" name="paymentTerms" class="w-full rounded-xl border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:outline-none focus:border-brand">
                    <option>Cash / POS on delivery at Lagos hub</option>
                    <option>Bank wire transfer pre-payment</option>
                    <option>14-Day trade credit terms (Subject to vetting)</option>
                  </select>
                </div>

                <div *ngIf="!session.authenticated()" class="rounded-xl border border-hairline p-4 text-sm"><p>Sign in to submit and track a wholesale application.</p><button type="button" (click)="session.signIn('/distributor?tab=wholesaler')" class="mt-2 rounded-full bg-brand px-4 py-2 text-white">Sign in to apply</button></div>
                <section *ngIf="session.authenticated()" class="space-y-3 rounded-xl border border-hairline bg-surface-alt p-4" aria-labelledby="wholesale-registration-proof-label">
                  <div>
                    <label id="wholesale-registration-proof-label" for="wholesale-registration-proof" class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">
                      Business registration proof <span class="text-brand">*</span>
                    </label>
                    <input
                      id="wholesale-registration-proof"
                      type="file"
                      accept="application/pdf,image/jpeg,image/png"
                      (change)="onDocumentSelected($event)"
                      [disabled]="uploadingDocument() || checkingDocument() || submittingWholesale()"
                      class="block w-full text-sm text-muted-ink file:mr-3 file:rounded-lg file:border-0 file:bg-brand file:px-3 file:py-2 file:text-sm file:font-semibold file:text-white disabled:opacity-60"
                    />
                    <p class="mt-1 text-xs text-muted-foreground">PDF, JPEG, or PNG; maximum 10 MB. The document must pass the server-side security scan before you can submit.</p>
                  </div>
                  <p *ngIf="selectedDocumentName()" class="text-sm text-ink">{{ selectedDocumentName() }}</p>
                  <p *ngIf="documentUploadState() === 'QUARANTINED'" class="text-sm text-amber-700" aria-live="polite">
                    Upload complete. The document is awaiting security-scan approval.
                  </p>
                  <p *ngIf="documentUploadState() === 'CLEAN'" class="text-sm text-green-700" aria-live="polite">
                    The document passed the security scan and is ready to submit.
                  </p>
                  <div class="flex flex-wrap items-center gap-3">
                    <button
                      *ngIf="selectedDocumentId && documentUploadState() === 'QUARANTINED'"
                      type="button"
                      (click)="checkDocumentStatus()"
                      [disabled]="checkingDocument()"
                      class="rounded-lg border border-hairline bg-white px-3 py-2 text-sm font-semibold text-ink disabled:opacity-60"
                    >
                      {{ checkingDocument() ? 'Checking scan status...' : 'Check scan status' }}
                    </button>
                    <a *ngIf="documentDownloadUrl()" [href]="documentDownloadUrl()" target="_blank" rel="noopener noreferrer" class="text-sm font-semibold text-brand underline">
                      Download uploaded document
                    </a>
                  </div>
                  <p *ngIf="documentUploadError()" class="text-sm text-red-700" role="alert">{{ documentUploadError() }}</p>
                </section>

                <button
                  type="submit"
                  [disabled]="submittingWholesale()"
                  class="w-full inline-flex items-center justify-center gap-2 rounded-full bg-brand text-white py-3.5 text-sm font-bold hover:bg-brand-deep disabled:opacity-60 transition-colors shadow-sm"
                >
                  <svg *ngIf="submittingWholesale()" lucideLoader2 class="h-4 w-4 animate-spin"></svg>
                  <span>{{ submittingWholesale() ? 'Submitting to Backend...' : 'Submit Wholesaler Application' }}</span>
                </button>
              </form>
            </ng-template>
          </div>

          <!-- ================= TAB 2: DISTRIBUTOR FORM ================= -->
          <div *ngIf="activeTab() === 'distributor'" class="rounded-2xl border border-hairline bg-white p-6 md:p-8 shadow-sm">
            <ng-container *ngIf="distributorSubmitted(); else distributorFormFields">
              <div class="text-center py-12 space-y-4">
                <div class="mx-auto grid h-16 w-16 place-items-center rounded-full bg-emerald-50 text-emerald-600 border border-emerald-100">
                  <svg lucideCheckCircle2 class="h-9 w-9"></svg>
                </div>
                <h3 class="text-2xl font-extrabold text-ink">
                  Distributor Application Submitted
                </h3>
                <p class="text-sm md:text-base text-muted-foreground max-w-lg mx-auto">
                  The backend recorded your corporate application for <strong>{{ dist.organizationName }}</strong>. Our commercial partnerships team will contact your executive office.
                </p>

                <div class="pt-4">
                  <button
                    type="button"
                    (click)="resetDistributor()"
                    class="inline-flex items-center gap-2 rounded-full border border-hairline px-6 py-2.5 text-xs font-semibold hover:border-brand hover:text-brand transition-colors"
                  >
                    Submit another distributor enquiry
                  </button>
                </div>
              </div>
            </ng-container>

            <ng-template #distributorFormFields>
              <div>
                <span class="text-xs font-bold uppercase tracking-wider text-brand">Exclusive Corporate Channel</span>
                <h2 class="text-2xl font-bold text-ink mt-0.5">Corporate Distributor Application</h2>
                <p class="mt-1 text-sm text-muted-foreground">For incorporated trading companies, regional logistics networks, and major electronics distributors.</p>
                <p class="mt-2 text-sm text-muted-foreground">Sign in and upload genuine business-registration proof. Your application must pass document review and independent approval. Prices and minimum quantities are assigned separately.</p>
              </div>

              <div *ngIf="!session.authenticated()" class="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p class="text-sm font-bold text-amber-950">Account Sign-In Required for Distributor Application</p>
                  <p class="text-xs text-amber-800 mt-0.5">Sign in with your Nigson customer account to upload your CAC registration certificate and track your distributor application status.</p>
                </div>
                <button
                  type="button"
                  (click)="session.signIn('/distributor')"
                  class="rounded-full bg-brand px-5 py-2 text-xs font-bold text-white hover:bg-brand-deep transition-colors shadow-sm"
                >
                  Sign In to Apply
                </button>
              </div>

              <form (submit)="onDistributorSubmit($event)" novalidate class="mt-6 space-y-5">
                <div class="grid gap-5 md:grid-cols-2">
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Registered Legal Entity Name <span class="text-brand">*</span></label>
                    <input type="text" [(ngModel)]="dist.organizationName" name="organizationName" (input)="validateDistributorField('organizationName')" (blur)="validateDistributorField('organizationName')" placeholder="e.g. Nigson West Africa Distribution Ltd" maxlength="200" [ngClass]="distributorErrors()['organizationName'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="distributorErrors()['organizationName']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ distributorErrors()['organizationName'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">CAC RC / Business Reg Number <span class="text-brand">*</span></label>
                    <input type="text" [(ngModel)]="dist.businessReference" name="businessReference" (input)="validateDistributorField('businessReference')" (blur)="validateDistributorField('businessReference')" placeholder="e.g. RC-1849204" maxlength="100" [ngClass]="distributorErrors()['businessReference'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="distributorErrors()['businessReference']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ distributorErrors()['businessReference'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Designated Exclusive Territory <span class="text-brand">*</span></label>
                    <input type="text" [(ngModel)]="dist.territory" name="territory" (input)="validateDistributorField('territory')" (blur)="validateDistributorField('territory')" placeholder="e.g. South-West Zone (Oyo, Ogun, Osun)" [ngClass]="distributorErrors()['territory'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="distributorErrors()['territory']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ distributorErrors()['territory'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Managing Director / Commercial Lead <span class="text-brand">*</span></label>
                    <input type="text" [(ngModel)]="dist.directorName" name="directorName" (input)="validateDistributorField('directorName')" (blur)="validateDistributorField('directorName')" placeholder="Full legal name" [ngClass]="distributorErrors()['directorName'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="distributorErrors()['directorName']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ distributorErrors()['directorName'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Official Corporate Email <span class="text-brand">*</span></label>
                    <input type="email" [(ngModel)]="dist.email" name="email" (input)="validateDistributorField('email')" (blur)="validateDistributorField('email')" placeholder="corporate@company.com" [ngClass]="distributorErrors()['email'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="distributorErrors()['email']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ distributorErrors()['email'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Executive Direct Phone (Numbers Only) <span class="text-brand">*</span></label>
                    <input type="tel" [(ngModel)]="dist.phone" name="phone" (keypress)="onPhoneKeyPress($event)" (input)="onPhoneInput('distributor')" (blur)="validateDistributorField('phone')" placeholder="+234 800 000 0000" [ngClass]="distributorErrors()['phone'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="distributorErrors()['phone']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ distributorErrors()['phone'] }}</p>
                  </div>
                </div>

                <div>
                  <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Registered Corporate Office Address <span class="text-brand">*</span></label>
                  <input type="text" [(ngModel)]="dist.headOfficeAddress" name="headOfficeAddress" (input)="validateDistributorField('headOfficeAddress')" (blur)="validateDistributorField('headOfficeAddress')" placeholder="Physical office & logistics depot address" [ngClass]="distributorErrors()['headOfficeAddress'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                  <p *ngIf="distributorErrors()['headOfficeAddress']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ distributorErrors()['headOfficeAddress'] }}</p>
                </div>

                <div class="grid gap-5 md:grid-cols-2">
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Annual Distribution Turnover</label>
                    <select [(ngModel)]="dist.annualTurnover" name="annualTurnover" class="w-full rounded-xl border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:outline-none focus:border-brand">
                      <option>₦50,000,000 - ₦200,000,000</option>
                      <option>₦200,000,000 - ₦500,000,000</option>
                      <option>₦50,000,000 - ₦1,500,000,000</option>
                      <option>₦1,500,000,000+ (Tier-1 Regional Enterprise)</option>
                    </select>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Warehouse Logistics Capacity</label>
                    <select [(ngModel)]="dist.logisticsCapacity" name="logisticsCapacity" class="w-full rounded-xl border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:outline-none focus:border-brand">
                      <option>Dedicated warehouse (1,000 - 5,000 sq ft)</option>
                      <option>Central logistics hub (5,000 - 20,000 sq ft)</option>
                      <option>Fleet distribution vans & trucks (3 - 10 vehicles)</option>
                      <option>Nationwide multi-hub facilities (10+ vehicles)</option>
                    </select>
                  </div>
                </div>

                <section *ngIf="session.authenticated()" class="space-y-3 rounded-xl border border-hairline bg-surface-alt p-4" aria-labelledby="registration-proof-label">
                  <div>
                    <label id="registration-proof-label" for="registration-proof" class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">
                      Business registration proof <span class="text-brand">*</span>
                    </label>
                    <input
                      id="registration-proof"
                      type="file"
                      accept="application/pdf,image/jpeg,image/png"
                      (change)="onDocumentSelected($event)"
                      [disabled]="uploadingDocument() || checkingDocument() || submittingDistributor()"
                      class="block w-full text-sm text-muted-ink file:mr-3 file:rounded-lg file:border-0 file:bg-brand file:px-3 file:py-2 file:text-sm file:font-semibold file:text-white disabled:opacity-60"
                    />
                    <p class="mt-1 text-xs text-muted-foreground">PDF, JPEG, or PNG; maximum 10 MB. The document must pass the server-side security scan before you can submit.</p>
                  </div>
                  <p *ngIf="selectedDocumentName()" class="text-sm text-ink">{{ selectedDocumentName() }}</p>
                  <p *ngIf="documentUploadState() === 'QUARANTINED'" class="text-sm text-amber-700" aria-live="polite">
                    Upload complete. The document is awaiting security-scan approval.
                  </p>
                  <p *ngIf="documentUploadState() === 'CLEAN'" class="text-sm text-green-700" aria-live="polite">
                    The document passed the security scan and is ready to submit.
                  </p>
                  <div class="flex flex-wrap items-center gap-3">
                    <button
                      *ngIf="selectedDocumentId && documentUploadState() === 'QUARANTINED'"
                      type="button"
                      (click)="checkDocumentStatus()"
                      [disabled]="checkingDocument()"
                      class="rounded-lg border border-hairline bg-white px-3 py-2 text-sm font-semibold text-ink disabled:opacity-60"
                    >
                      {{ checkingDocument() ? 'Checking scan status...' : 'Check scan status' }}
                    </button>
                    <a *ngIf="documentDownloadUrl()" [href]="documentDownloadUrl()" target="_blank" rel="noopener noreferrer" class="text-sm font-semibold text-brand underline">
                      Download uploaded document
                    </a>
                  </div>
                  <p *ngIf="documentUploadError()" class="text-sm text-red-700" role="alert">{{ documentUploadError() }}</p>
                </section>

                <button
                  type="submit"
                  [disabled]="submittingDistributor() || uploadingDocument() || checkingDocument()"
                  class="w-full inline-flex items-center justify-center gap-2 rounded-full bg-brand text-white py-3.5 text-sm font-bold hover:bg-brand-deep disabled:opacity-60 transition-colors shadow-sm"
                >
                  <svg *ngIf="submittingDistributor()" lucideLoader2 class="h-4 w-4 animate-spin"></svg>
                  <span>{{ submittingDistributor() ? 'Submitting to Backend...' : 'Submit Distributor Application' }}</span>
                </button>
              </form>
            </ng-template>


          </div>

            <div *ngIf="session.authenticated() && activeTab() !== 'affiliate'" class="mt-8 border-t border-hairline pt-6 space-y-3">
              <div class="flex items-center justify-between">
                <h3 class="text-sm font-bold uppercase tracking-wider text-ink">Your Business Applications</h3>
                <button
                  type="button"
                  (click)="loadMyApplications()"
                  class="text-xs font-semibold text-brand hover:underline"
                >
                  Refresh Status
                </button>
              </div>
              <p *ngIf="marketingSvc.myApplicationsError?.()" class="text-xs text-red-600">{{ marketingSvc.myApplicationsError?.() }}</p>
              <p *ngIf="loadingMyApplications()" class="text-xs text-muted-foreground">Loading your applications from database...</p>
              <div *ngIf="!loadingMyApplications() && myApplications().length === 0" class="rounded-xl border border-hairline bg-surface-alt px-4 py-3 text-xs text-muted-foreground">
                No business applications found for your account yet.
              </div>
              <div *ngFor="let app of myApplications()" class="rounded-xl border border-hairline bg-surface-alt px-4 py-3 flex flex-wrap items-center justify-between gap-2 text-xs">
                <div>
                  <p class="font-bold text-ink">{{ app.partnerType === 'AFFILIATE' ? 'Affiliate' : app.partnerType === 'WHOLESALE' ? 'Wholesale' : 'Distributor' }} application {{ app.id.slice(0, 8).toUpperCase() }}</p>
                  <p class="text-muted-foreground">Submitted {{ app.submittedAt | date:'medium' }}</p>
                </div>
                <span class="rounded-full px-3 py-1 font-bold text-[11px] bg-white border border-hairline text-ink">
                  {{ app.state }}
                </span>
              </div>
            </div>

          <!-- ================= TAB 3: AFFILIATE MARKETER FORM ================= -->
          <div *ngIf="activeTab() === 'affiliate'" class="rounded-2xl border border-hairline bg-white p-6 md:p-8 shadow-sm">
            <ng-container *ngIf="affiliateSubmitted(); else affiliateFormFields">
              <div class="text-center py-12 space-y-4">
                <div class="mx-auto grid h-16 w-16 place-items-center rounded-full bg-emerald-50 text-emerald-600 border border-emerald-100">
                  <svg lucideCheckCircle2 class="h-9 w-9"></svg>
                </div>
                <h3 class="text-2xl font-extrabold text-ink">Affiliate Application Received!</h3>
                <p class="text-sm md:text-base text-muted-foreground max-w-lg mx-auto">
                  Welcome to the Nigson Creator Network, <strong>{{ aff.name }}</strong>! Your application has been logged into the partner database. Our affiliate manager will send your onboarding guide and custom referral link within 24 hours.
                </p>
                <div class="pt-4">
                  <button
                    type="button"
                    (click)="resetAffiliate()"
                    class="inline-flex items-center gap-2 rounded-full border border-hairline px-6 py-2.5 text-xs font-semibold hover:border-brand hover:text-brand transition-colors"
                  >
                    Submit another affiliate application
                  </button>
                </div>
              </div>
            </ng-container>

            <ng-template #affiliateFormFields>
              <div>
                <span class="text-xs font-bold uppercase tracking-wider text-brand">Digital Creators & Affiliates</span>
                <h2 class="text-2xl font-bold text-ink mt-0.5">Affiliate Marketer / Creator Application</h2>
                <p class="mt-1 text-sm text-muted-foreground">Monetize your followers, tech blogs, social media pages, and WhatsApp retail groups.</p>
              </div>

              <form (submit)="onAffiliateSubmit($event)" novalidate class="mt-6 space-y-5">
                <div class="grid gap-5 md:grid-cols-2">
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Full Legal Name <span class="text-brand">*</span></label>
                    <input type="text" [(ngModel)]="aff.name" name="name" (input)="validateAffiliateField('name')" (blur)="validateAffiliateField('name')" placeholder="First & Last Name" [ngClass]="affiliateErrors()['name'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="affiliateErrors()['name']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ affiliateErrors()['name'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Primary Email <span class="text-brand">*</span></label>
                    <input type="email" [(ngModel)]="aff.email" name="email" (input)="validateAffiliateField('email')" (blur)="validateAffiliateField('email')" placeholder="you@domain.com" [ngClass]="affiliateErrors()['email'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="affiliateErrors()['email']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ affiliateErrors()['email'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">WhatsApp Number (Numbers Only) <span class="text-brand">*</span></label>
                    <input type="tel" [(ngModel)]="aff.phone" name="phone" (keypress)="onPhoneKeyPress($event)" (input)="onPhoneInput('affiliate')" (blur)="validateAffiliateField('phone')" placeholder="+234 800 000 0000" [ngClass]="affiliateErrors()['phone'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="affiliateErrors()['phone']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ affiliateErrors()['phone'] }}</p>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">State / City of Operation <span class="text-brand">*</span></label>
                    <input type="text" [(ngModel)]="aff.location" name="location" (input)="validateAffiliateField('location')" (blur)="validateAffiliateField('location')" placeholder="e.g. Lagos, Abuja, Port Harcourt" [ngClass]="affiliateErrors()['location'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                    <p *ngIf="affiliateErrors()['location']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ affiliateErrors()['location'] }}</p>
                  </div>
                </div>

                <div>
                  <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Primary Social Channel Links / Handles <span class="text-brand">*</span></label>
                  <input type="text" [(ngModel)]="aff.channels" name="channels" (input)="validateAffiliateField('channels')" (blur)="validateAffiliateField('channels')" placeholder="e.g. @tech_reviewer (TikTok), youtube.com/c/gadgetreviews, blog.com" [ngClass]="affiliateErrors()['channels'] ? 'border-red-500 bg-red-50/20' : 'border-hairline bg-surface-alt'" class="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                  <p *ngIf="affiliateErrors()['channels']" class="mt-1 text-xs text-red-600 font-medium flex items-center gap-1"><span>⚠</span> {{ affiliateErrors()['channels'] }}</p>
                </div>

                <div class="grid gap-5 md:grid-cols-2">
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Audience Reach / Followers</label>
                    <select [(ngModel)]="aff.audience" name="audience" class="w-full rounded-xl border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:outline-none focus:border-brand">
                      <option>Under 5,000 followers</option>
                      <option>5,000 - 25,000 followers</option>
                      <option>25,000 - 100,000 followers</option>
                      <option>100,000 - 500,000 followers</option>
                      <option>500,000+ followers (Top Tier Creator)</option>
                    </select>
                  </div>
                  <div>
                    <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Content Focus / Niche</label>
                    <select [(ngModel)]="aff.niche" name="niche" class="w-full rounded-xl border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:outline-none focus:border-brand">
                      <option>Mobile phones & charging gear</option>
                      <option>Gaming & PC hardware</option>
                      <option>Student lifestyle & tech campus deals</option>
                      <option>General gadgets & smart home</option>
                    </select>
                  </div>
                </div>

                <p class="text-sm text-muted-ink">Sign in to apply. Bank details are not needed to apply.</p>

                <div>
                  <label class="block text-xs font-bold uppercase tracking-wider text-muted-ink mb-1.5">Promotion Strategy</label>
                  <textarea rows="2" [(ngModel)]="aff.strategy" name="strategy" placeholder="Describe how you plan to showcase Nigson products to your audience..." class="w-full rounded-xl border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:outline-none focus:border-brand"></textarea>
                </div>

                <button
                  type="submit"
                  [disabled]="submittingAffiliate()"
                  class="w-full inline-flex items-center justify-center gap-2 rounded-full bg-brand text-white py-3.5 text-sm font-bold hover:bg-brand-deep disabled:opacity-60 transition-colors shadow-sm"
                >
                  <svg *ngIf="submittingAffiliate()" lucideLoader2 class="h-4 w-4 animate-spin"></svg>
                  <span>{{ submittingAffiliate() ? 'Submitting to Backend...' : 'Submit Affiliate Application' }}</span>
                </button>
              </form>
            </ng-template>
          </div>

        </div>
      </section>
    </div>
  `
})
export class DistributorComponent {
  private route = inject(ActivatedRoute, { optional: true });
  private router = inject(Router, { optional: true });

  session = inject(SessionService);
  marketingSvc = inject(MarketingService);
  private readonly http = inject(HttpClient);

  // 3 distinct tabs: wholesaler | distributor | affiliate
  activeTab = signal<'wholesaler' | 'distributor' | 'affiliate'>('wholesaler');

  myApplications = signal<BackendDistributorApplication[]>([]);
  loadingMyApplications = signal(false);

  switchTab(tab: 'wholesaler' | 'distributor' | 'affiliate') {
    this.activeTab.set(tab);
    this.submitError.set(null);
    if (this.session.authenticated()) {
      this.loadMyApplications();
    }
    if (this.router && this.route) {
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { tab },
        queryParamsHandling: 'merge'
      });
    }
  }

  constructor() {
    if (this.route) {
      const dataTab = this.route.snapshot?.data?.['tab'];
      if (dataTab === 'affiliate' || dataTab === 'distributor' || dataTab === 'wholesaler') {
        this.activeTab.set(dataTab);
      }
      this.route.queryParamMap?.subscribe(params => {
        const tab = params.get('tab')?.toLowerCase();
        if (tab === 'affiliate') this.activeTab.set('affiliate');
        else if (tab === 'distributor') this.activeTab.set('distributor');
        else if (tab === 'wholesaler' || tab === 'wholesale') this.activeTab.set('wholesaler');
      });
    }

    try {
      effect(() => {
        if (this.session.authenticated()) {
          this.loadMyApplications();
        } else {
          this.myApplications.set([]);
        }
      });
    } catch {
      if (this.session.authenticated()) {
        this.loadMyApplications();
      }
    }
  }

  loadMyApplications(): void {
    if (!this.session.authenticated() || !this.marketingSvc.getMyDistributorApplications) {
      this.myApplications.set([]);
      return;
    }
    this.loadingMyApplications.set(true);
    this.marketingSvc.getMyDistributorApplications().subscribe({
      next: apps => {
        this.myApplications.set(apps);
        this.loadingMyApplications.set(false);
      },
      error: () => {
        this.loadingMyApplications.set(false);
      }
    });
  }

  wholesaleSubmitted = signal(false);
  distributorSubmitted = signal(false);
  distributorApplicationSubmitted = signal(false);
  affiliateSubmitted = signal(false);

  submittingWholesale = signal(false);
  submittingDistributor = signal(false);
  submittingAffiliate = signal(false);

  submitError = signal<string | null>(null);

  selectedDocumentId?: string;
  selectedDocumentName = signal<string | null>(null);
  documentUploadState = signal<'QUARANTINED' | 'CLEAN' | null>(null);
  documentDownloadUrl = signal<string | null>(null);
  documentUploadError = signal<string | null>(null);
  uploadingDocument = signal(false);
  checkingDocument = signal(false);

  // ─── TAB 1: WHOLESALER DATA MODEL ──────────────────────────────────────────
  f = {
    company: '',
    contact: '',
    phone: '',
    email: '',
    state: '',
    years: '',
    address: '',
    category: 'Phone accessories (Chargers, Cables, Earbuds)',
    volume: '₦1,000,000 - ₦3,000,000 / month',
    paymentTerms: 'Cash / POS on delivery at Lagos hub'
  };

  // ─── TAB 2: DISTRIBUTOR DATA MODEL ─────────────────────────────────────────
  dist = {
    organizationName: '',
    countryCode: 'NG',
    businessReference: '',
    territory: '',
    directorName: '',
    email: '',
    phone: '',
    headOfficeAddress: '',
    annualTurnover: '₦50,000,000 - ₦200,000,000',
    logisticsCapacity: 'Dedicated warehouse (1,000 - 5,000 sq ft)'
  };

  // ─── TAB 3: AFFILIATE DATA MODEL ───────────────────────────────────────────
  aff = {
    name: '',
    email: '',
    phone: '',
    location: '',
    channels: '',
    audience: '5,000 - 25,000 followers',
    niche: 'Mobile phones & charging gear',
    strategy: ''
  };

  wholesaleErrors = signal<Record<string, string>>({});
  distributorErrors = signal<Record<string, string>>({});
  affiliateErrors = signal<Record<string, string>>({});

  // Keypress handler to allow only numbers (and optional '+' at the start for phone)
  onPhoneKeyPress(event: KeyboardEvent): boolean {
    const input = event.target as HTMLInputElement;
    const value = input?.value || '';
    const char = event.key;

    // Allow control keys
    if (['Backspace', 'ArrowLeft', 'ArrowRight', 'Tab', 'Delete', 'Enter'].includes(char)) {
      return true;
    }
    // Allow '+' only at the very first position if not already present
    if (char === '+' && input.selectionStart === 0 && !value.includes('+')) {
      return true;
    }
    // Allow digits 0-9
    if (/[0-9]/.test(char)) {
      return true;
    }
    event.preventDefault();
    return false;
  }

  // Keypress handler for strict digits only (no letters, no symbols, e.g. for NUBAN Account Number)
  onDigitOnlyKeyPress(event: KeyboardEvent): boolean {
    const char = event.key;
    if (['Backspace', 'ArrowLeft', 'ArrowRight', 'Tab', 'Delete', 'Enter'].includes(char)) {
      return true;
    }
    if (/[0-9]/.test(char)) {
      return true;
    }
    event.preventDefault();
    return false;
  }

  // Input sanitizer when typing or pasting
  onPhoneInput(tab: 'wholesale' | 'distributor' | 'affiliate'): void {
    if (tab === 'wholesale') {
      const hasPlus = this.f.phone.trim().startsWith('+');
      const digits = this.f.phone.replace(/[^0-9]/g, '');
      this.f.phone = (hasPlus ? '+' : '') + digits;
      if (this.wholesaleErrors()['phone']) this.validateWholesaleField('phone');
    } else if (tab === 'distributor') {
      const hasPlus = this.dist.phone.trim().startsWith('+');
      const digits = this.dist.phone.replace(/[^0-9]/g, '');
      this.dist.phone = (hasPlus ? '+' : '') + digits;
      if (this.distributorErrors()['phone']) this.validateDistributorField('phone');
    } else if (tab === 'affiliate') {
      const hasPlus = this.aff.phone.trim().startsWith('+');
      const digits = this.aff.phone.replace(/[^0-9]/g, '');
      this.aff.phone = (hasPlus ? '+' : '') + digits;
      if (this.affiliateErrors()['phone']) this.validateAffiliateField('phone');
    }
  }


  onYearsInput(tab: string): void {
    this.f.years = (this.f.years || '').toString().replace(/[^0-9]/g, '').slice(0, 2);
  }

  validateEmail(email: string): boolean {
    const re = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    return re.test((email || '').trim());
  }

  validatePhone(phone: string): boolean {
    const digits = (phone || '').replace(/[^0-9]/g, '');
    return digits.length >= 7 && digits.length <= 15;
  }

  validateWholesaleField(field: string): void {
    const errors = { ...this.wholesaleErrors() };
    if (field === 'company') {
      if (!this.f.company.trim()) errors['company'] = 'Business / Store Name is required';
      else if (this.f.company.trim().length < 2) errors['company'] = 'Name must be at least 2 characters';
      else delete errors['company'];
    }
    if (field === 'contact') {
      if (!this.f.contact.trim()) errors['contact'] = 'Contact Person Name is required';
      else if (this.f.contact.trim().length < 2) errors['contact'] = 'Name must be at least 2 characters';
      else delete errors['contact'];
    }
    if (field === 'email') {
      if (!this.f.email.trim()) errors['email'] = 'Email address is required';
      else if (!this.validateEmail(this.f.email)) errors['email'] = 'Please enter a valid email address';
      else delete errors['email'];
    }
    if (field === 'phone') {
      if (!this.f.phone.trim()) errors['phone'] = 'Phone / WhatsApp number is required';
      else if (!this.validatePhone(this.f.phone)) errors['phone'] = 'Please enter a valid phone number (digits only, 7-15 digits)';
      else delete errors['phone'];
    }
    if (field === 'state') {
      if (!this.f.state.trim()) errors['state'] = 'Trading territory / state is required';
      else delete errors['state'];
    }
    if (field === 'address') {
      if (!this.f.address.trim()) errors['address'] = 'Store / warehouse delivery address is required';
      else delete errors['address'];
    }
    this.wholesaleErrors.set(errors);
  }

  validateWholesale(): boolean {
    ['company', 'contact', 'email', 'phone', 'state', 'address'].forEach(f => this.validateWholesaleField(f));
    return Object.keys(this.wholesaleErrors()).length === 0;
  }

  validateDistributorField(field: string): void {
    const errors = { ...this.distributorErrors() };
    if (field === 'organizationName') {
      if (!this.dist.organizationName.trim()) errors['organizationName'] = 'Registered Legal Entity Name is required';
      else if (this.dist.organizationName.trim().length < 2) errors['organizationName'] = 'Name must be at least 2 characters';
      else delete errors['organizationName'];
    }
    if (field === 'businessReference') {
      if (!this.dist.businessReference.trim()) errors['businessReference'] = 'CAC RC / Registration Number is required';
      else delete errors['businessReference'];
    }
    if (field === 'territory') {
      if (!this.dist.territory.trim()) errors['territory'] = 'Designated Exclusive Territory is required';
      else delete errors['territory'];
    }
    if (field === 'directorName') {
      if (!this.dist.directorName.trim()) errors['directorName'] = 'Managing Director / Commercial Lead name is required';
      else if (this.dist.directorName.trim().length < 2) errors['directorName'] = 'Name must be at least 2 characters';
      else delete errors['directorName'];
    }
    if (field === 'email') {
      if (!this.dist.email.trim()) errors['email'] = 'Official corporate email is required';
      else if (!this.validateEmail(this.dist.email)) errors['email'] = 'Please enter a valid email address';
      else delete errors['email'];
    }
    if (field === 'phone') {
      if (!this.dist.phone.trim()) errors['phone'] = 'Executive direct phone is required';
      else if (!this.validatePhone(this.dist.phone)) errors['phone'] = 'Please enter a valid phone number (digits only, 7-15 digits)';
      else delete errors['phone'];
    }
    if (field === 'headOfficeAddress') {
      if (!this.dist.headOfficeAddress.trim()) errors['headOfficeAddress'] = 'Registered corporate office address is required';
      else delete errors['headOfficeAddress'];
    }
    this.distributorErrors.set(errors);
  }

  validateDistributor(): boolean {
    ['organizationName', 'businessReference', 'territory', 'directorName', 'email', 'phone', 'headOfficeAddress'].forEach(f => this.validateDistributorField(f));
    return Object.keys(this.distributorErrors()).length === 0;
  }

  validateAffiliateField(field: string): void {
    const errors = { ...this.affiliateErrors() };
    if (field === 'name') {
      if (!this.aff.name.trim()) errors['name'] = 'Full legal name is required';
      else if (this.aff.name.trim().length < 2) errors['name'] = 'Name must be at least 2 characters';
      else delete errors['name'];
    }
    if (field === 'email') {
      if (!this.aff.email.trim()) errors['email'] = 'Primary email is required';
      else if (!this.validateEmail(this.aff.email)) errors['email'] = 'Please enter a valid email address';
      else delete errors['email'];
    }
    if (field === 'phone') {
      if (!this.aff.phone.trim()) errors['phone'] = 'WhatsApp phone number is required';
      else if (!normalizePhoneNumber(this.aff.phone)) errors['phone'] = 'Please enter a valid phone number (digits only, 7-15 digits)';
      else delete errors['phone'];
    }
    if (field === 'location') {
      if (!this.aff.location.trim()) errors['location'] = 'State / City of operation is required';
      else delete errors['location'];
    }
    if (field === 'channels') {
      if (!this.aff.channels.trim()) errors['channels'] = 'Social channel handles or URLs are required';
      else delete errors['channels'];
    }
    this.affiliateErrors.set(errors);
  }

  validateAffiliate(): boolean {
    ['name', 'email', 'phone', 'location', 'channels'].forEach(f => this.validateAffiliateField(f));
    return Object.keys(this.affiliateErrors()).length === 0;
  }

  private readonly pendingApplications = new Map<string,{body:string;key:string}>();
  private submitBusinessApplication(payload: DistributorApplicationPayload) {
    const scope=this.session.userId()+':'+payload.partnerType;
    const body=JSON.stringify(payload);const previous=this.pendingApplications.get(scope);
    if(previous && previous.body!==body)return throwError(()=>new Error('Your earlier submission is unconfirmed. Restore its details and retry, or refresh application status before starting another application.'));
    const request=previous??{body,key:crypto.randomUUID()};this.pendingApplications.set(scope,request);
    return this.marketingSvc.submitDistributorApplication(payload,request.key).pipe(tap(()=>this.pendingApplications.delete(scope)),catchError(error=>{if([400,401,403,404,422].includes(error?.status))this.pendingApplications.delete(scope);return throwError(()=>error);}));
  }

  onWholesaleSubmit(event: Event) {
    event.preventDefault();
    this.submitError.set(null);

    if (!this.validateWholesale()) {
      this.submitError.set('Please fill out all required fields correctly before submitting.');
      return;
    }

    this.submittingWholesale.set(true);

    const fullMessage = [
      `Wholesale Trade Distributor Application`,
      `Store / Company: ${this.f.company}`,
      `Contact Person: ${this.f.contact}`,
      `Email: ${this.f.email}`,
      `Phone: ${this.f.phone}`,
      `Trading Hub / State: ${this.f.state}`,
      `Delivery Address: ${this.f.address}`,
      `Years in Business: ${this.f.years || 'New Merchant'}`,
      `Product Category: ${this.f.category}`,
      `Estimated Monthly Volume: ${this.f.volume}`,
      `Preferred Settlement: ${this.f.paymentTerms}`
    ].join('\n');

    if (!this.session.authenticated()) {this.submittingWholesale.set(false);this.submitError.set('Sign in to submit a business application linked to your account.');return;}
    if (!this.selectedDocumentId || this.documentUploadState() !== 'CLEAN') {this.submittingWholesale.set(false);this.submitError.set('Upload your registration proof and wait for a clean security scan.');return;}
    this.submitBusinessApplication({organizationName:this.f.company.trim(),countryCode:'NG',partnerType:'WHOLESALE',documentIds:[this.selectedDocumentId],businessDetails:fullMessage}).subscribe({
      next:()=>{this.submittingWholesale.set(false);this.wholesaleSubmitted.set(true);this.selectedDocumentId=undefined;this.loadMyApplications();},
      error:(error:any)=>{this.submittingWholesale.set(false);this.submitError.set(apiFailureDetail(error,'Wholesale application was not submitted. Please try again.'));}
    });
  }

  onDistributorSubmit(event: Event) {
    event.preventDefault();
    this.submitError.set(null);

    if (!this.validateDistributor()) {
      this.submitError.set('Please fill out all required fields correctly before submitting.');
      return;
    }

    const authenticated = this.session.authenticated();
    const documentId = this.selectedDocumentId?.trim();
    if (authenticated && (!documentId || this.documentUploadState() !== 'CLEAN')) {
      this.submitError.set(documentId
        ? 'The business-registration document must pass the security scan before you can submit the application.'
        : 'Upload a business-registration document and wait for its security scan before submitting the application.');
      return;
    }

    this.submittingDistributor.set(true);

    const businessRef = this.dist.businessReference.trim() || undefined;

    const fullMessage = [
      `Corporate Regional Distributor Application`,
      `Legal Entity: ${this.dist.organizationName}`,
      `CAC RC Number: ${this.dist.businessReference}`,
      `Assigned Territory: ${this.dist.territory}`,
      `Managing Director: ${this.dist.directorName}`,
      `Corporate Email: ${this.dist.email}`,
      `Corporate Phone: ${this.dist.phone}`,
      `Head Office: ${this.dist.headOfficeAddress}`,
      `Annual Turnover: ${this.dist.annualTurnover}`,
      `Logistics Capacity: ${this.dist.logisticsCapacity}`
    ].join('\n');

    if (!authenticated || !documentId) {this.submittingDistributor.set(false);this.submitError.set('Sign in and upload your registration proof to submit an application.');return;}
    this.submitBusinessApplication({organizationName:this.dist.organizationName.trim(),countryCode:'NG',partnerType:'DISTRIBUTOR',documentIds:[documentId],businessReference:businessRef,businessDetails:fullMessage}).subscribe({
      next:()=>{this.submittingDistributor.set(false);this.distributorApplicationSubmitted.set(true);this.distributorSubmitted.set(true);this.selectedDocumentId=undefined;this.loadMyApplications();},
      error:(error:any)=>{this.submittingDistributor.set(false);this.submitError.set(apiFailureDetail(error,'Distributor application was not submitted. Please try again.'));}
    });
  }

  async onDocumentSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    this.documentUploadError.set(null);
    this.selectedDocumentId = undefined;
    this.selectedDocumentName.set(null);
    this.documentUploadState.set(null);
    this.documentDownloadUrl.set(null);

    if (!file) return;
    if (
      !['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)
      || file.size < 1
      || file.size > 10 * 1024 * 1024
      || file.name.length > 120
      || file.name.includes('/')
      || file.name.includes('\\')
      || /[\u0000-\u001f\u007f]/.test(file.name)
    ) {
      this.documentUploadError.set('Choose a PDF, JPEG, or PNG file no larger than 10 MB with a valid filename.');
      if (input) input.value = '';
      return;
    }

    this.uploadingDocument.set(true);
    this.selectedDocumentName.set(file.name);
    try {
      const checksumSha256 = await this.sha256(file);
      const upload = await firstValueFrom(this.http.post<FileUploadSession>(
        '/api/v1/experience/files/uploads',
        {
          purpose: 'DISTRIBUTOR_PROOF',
          filename: file.name,
          mimeType: file.type,
          size: file.size,
          checksumSha256
        },
        { headers: { 'Idempotency-Key': crypto.randomUUID() } }
      ));

      if (!upload.uploadUrl.startsWith('https://s3-mock.invalid')) {
        const storageResponse = await fetch(upload.uploadUrl, {
          method: 'PUT',
          headers: upload.requiredHeaders,
          body: file,
          credentials: 'omit'
        });
        if (!storageResponse.ok) {
          throw new Error(`Document storage upload failed with HTTP ${storageResponse.status}.`);
        }
      }

      const completed = await firstValueFrom(this.http.post<UploadedFile>(
        `/api/v1/experience/files/${encodeURIComponent(upload.assetId)}/completion`,
        { checksumSha256 },
        { headers: { 'If-Match': '"0"', 'Idempotency-Key': crypto.randomUUID() } }
      ));
      if (!['QUARANTINED', 'CLEAN'].includes(completed.state)) {
        throw new Error(`Unexpected document scan state: ${completed.state}.`);
      }
      this.selectedDocumentId = completed.id;
      this.documentUploadState.set(completed.state as any);
    } catch (error) {
      this.documentUploadError.set(apiFailureDetail(error, 'The document upload could not be completed.'));
    } finally {
      this.uploadingDocument.set(false);
      if (input) input.value = '';
    }
  }

  async checkDocumentStatus(): Promise<void> {
    if (!this.selectedDocumentId || this.documentUploadState() !== 'QUARANTINED') return;

    this.checkingDocument.set(true);
    this.documentUploadError.set(null);
    try {
      const download = await firstValueFrom(this.http.get<FileDownload>(
        `/api/v1/experience/files/${encodeURIComponent(this.selectedDocumentId)}/download`
      ));
      this.documentDownloadUrl.set(download.url);
      this.documentUploadState.set('CLEAN');
    } catch (error) {
      this.documentDownloadUrl.set(null);
      if (problemField(error, 'code') === 'INVALID_TRANSITION') {
        this.documentUploadError.set('The document is not available for download yet. It may still be scanning or may have been rejected; try again later or upload a different file.');
      } else {
        this.documentUploadError.set(apiFailureDetail(error, 'Could not check the document scan status.'));
      }
    } finally {
      this.checkingDocument.set(false);
    }
  }

  private async sha256(file: File): Promise<string> {
    const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
    return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
  }

  onAffiliateSubmit(event: Event) {
    event.preventDefault();this.submitError.set(null);
    if(!this.session.authenticated()){this.session.signIn('/affiliate');return;}
    if(!this.validateAffiliate()){this.submitError.set('Please complete the required application details.');return;}
    if(this.submittingAffiliate())return;this.submittingAffiliate.set(true);
    const details={email:this.aff.email.trim(),phone:normalizePhoneNumber(this.aff.phone)!,location:this.aff.location.trim(),channels:this.aff.channels.trim(),...(this.aff.strategy.trim()?{strategy:this.aff.strategy.trim()}:{}),audience:this.aff.audience,niche:this.aff.niche};
    this.submitBusinessApplication({partnerType:'AFFILIATE',organizationName:this.aff.name.trim(),countryCode:'NG',documentIds:[],businessDetails:JSON.stringify(details)}).pipe(finalize(()=>this.submittingAffiliate.set(false))).subscribe({next:()=>{this.affiliateSubmitted.set(true);this.loadMyApplications();},error:(error:any)=>this.submitError.set(error?.error?.detail||error?.message||'Application could not be confirmed. Retry the unchanged details to safely check its outcome.')});
  }

  resetWholesale() {
    this.wholesaleSubmitted.set(false);
    this.f = {
      company: '',
      contact: '',
      phone: '',
      email: '',
      state: '',
      years: '',
      address: '',
      category: 'Phone accessories (Chargers, Cables, Earbuds)',
      volume: '₦1,000,000 - ₦3,000,000 / month',
      paymentTerms: 'Cash / POS on delivery at Lagos hub'
    };
  }

  resetDistributor() {
    this.distributorSubmitted.set(false);
    this.distributorApplicationSubmitted.set(false);
    this.dist = {
      organizationName: '',
      countryCode: 'NG',
      businessReference: '',
      territory: '',
      directorName: '',
      email: '',
      phone: '',
      headOfficeAddress: '',
      annualTurnover: '₦50,000,000 - ₦200,000,000',
      logisticsCapacity: 'Dedicated warehouse (1,000 - 5,000 sq ft)'
    };
  }

  resetAffiliate() {
    this.affiliateSubmitted.set(false);
    this.aff = {
      name: '',
      email: '',
      phone: '',
      location: '',
      channels: '',
      audience: '5,000 - 25,000 followers',
      niche: 'Mobile phones & charging gear',
      strategy: ''
    };
  }
}

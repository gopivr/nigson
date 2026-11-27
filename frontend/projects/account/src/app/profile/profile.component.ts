import {PartnerStatusComponent} from './partner-status.component';
import { PhoneInputComponent, normalizePhoneNumber, apiFailureDetail } from 'shared-ui';
import { Component, inject, signal, ViewChild, ElementRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccountService, OrderService, QuoteService, ShopService } from 'shared-ui';
import { 
  LucideUpload, LucideTrash2, LucidePencil, LucideSave, LucideX, 
  LucidePackage, LucideFileText, LucideCalendarClock,
  LucideGift, LucideShare2, LucideCopy, LucideCheck, LucideAward, LucideLoader2
} from '@lucide/angular';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    CommonModule, FormsModule, PhoneInputComponent, PartnerStatusComponent,
    LucideUpload, LucideTrash2, LucidePencil, LucideSave, LucideX,
    LucidePackage, LucideFileText, LucideCalendarClock,
    LucideGift, LucideShare2, LucideCopy, LucideCheck, LucideAward, LucideLoader2
  ],
  template: `
    <div class="space-y-6" *ngIf="user()">
      <!-- Header card -->
      <div class="rounded-2xl border border-hairline bg-white p-6 md:p-8">
        <div class="flex flex-wrap items-start justify-between gap-6">
          <div class="flex items-center gap-5">
            <div class="relative">
              <img *ngIf="avatarUrl()" [src]="avatarUrl()" alt="Avatar" class="h-20 w-20 rounded-full object-cover border border-hairline" />
              <div *ngIf="!avatarUrl()" class="grid h-20 w-20 place-items-center rounded-full text-white text-2xl font-extrabold" [ngStyle]="{'background': avatarBg()}">
                {{ initialsOf(form().fullName) }}
              </div>
              
              <button
                *ngIf="editing()"
                (click)="fileInput.click()"
                class="absolute -bottom-1 -right-1 grid h-8 w-8 place-items-center rounded-full bg-brand text-white shadow-md hover:bg-brand-deep"
              >
                <svg lucideUpload class="h-4 w-4"></svg>
              </button>
              <input #fileInput type="file" accept="image/*" hidden (change)="onAvatar($event)" />
            </div>
            
            <div>
              <p class="text-[10px] font-semibold uppercase tracking-widest text-brand">Profile</p>
              <h2 class="text-2xl font-extrabold text-ink">{{ form().fullName || 'Customer' }}</h2>
              <p class="text-sm text-muted-ink">{{ form().email }}</p>
              
              <button
                *ngIf="editing() && avatarUrl()"
                (click)="removePhoto()"
                class="mt-2 inline-flex items-center gap-1 text-xs font-semibold text-brand hover:underline"
              >
                <svg lucideTrash2 class="h-3 w-3"></svg> Remove photo
              </button>
            </div>
          </div>
          
          <div class="flex gap-2">
            <button *ngIf="!editing()" (click)="startEditing()" class="inline-flex items-center gap-2 rounded-full bg-ink text-white px-5 py-2.5 text-xs font-semibold hover:bg-brand transition-colors">
              <svg lucidePencil class="h-3.5 w-3.5"></svg> Edit Profile
            </button>
            
            <ng-container *ngIf="editing()">
              <button (click)="cancelEdit()" class="inline-flex items-center gap-2 rounded-full border border-hairline px-5 py-2.5 text-xs font-semibold hover:border-brand hover:text-brand">
                <svg lucideX class="h-3.5 w-3.5"></svg> Cancel
              </button>
              <button (click)="save()" [disabled]="saving()" class="inline-flex items-center gap-2 rounded-full bg-brand text-white px-5 py-2.5 text-xs font-semibold hover:bg-brand-deep disabled:opacity-50">
                <svg *ngIf="!saving()" lucideSave class="h-3.5 w-3.5"></svg>
                <svg *ngIf="saving()" lucideLoader2 class="h-3.5 w-3.5 animate-spin"></svg>
                {{ saving() ? 'Saving...' : 'Save Changes' }}
              </button>
            </ng-container>
          </div>
        </div>

        <p *ngIf="saveError()" role="alert" class="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">{{saveError()}}</p>
        <div *ngIf="saveSuccess()" class="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-2 text-xs font-medium text-emerald-800">
          Profile updated successfully.
        </div>
      </div>

      <!-- Stats / Overview (TASK-47, TASK-50) -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div class="rounded-2xl border border-hairline bg-white p-4">
          <svg lucidePackage class="h-4 w-4 text-brand"></svg>
          <p class="mt-3 text-2xl font-extrabold text-ink">{{ orderSvc.totalElements() || ordersCount() }}</p>
          <p class="text-[10px] font-semibold uppercase tracking-widest text-muted-ink mt-1">Total Orders</p>
        </div>
        <div class="rounded-2xl border border-hairline bg-white p-4">
          <svg lucideFileText class="h-4 w-4 text-brand"></svg>
          <p class="mt-3 text-2xl font-extrabold text-ink">{{ quoteSvc.totalElements() || quoteSvc.quotes().length }}</p>
          <p class="text-[10px] font-semibold uppercase tracking-widest text-muted-ink mt-1">Quotes</p>
        </div>
        <div class="rounded-2xl border border-hairline bg-white p-4">
          <svg lucideAward class="h-4 w-4 text-brand"></svg>
          <p class="mt-3 text-2xl font-extrabold text-ink">{{ rewardBalance() }}</p>
          <p class="text-[10px] font-semibold uppercase tracking-widest text-muted-ink mt-1">Reward Points</p>
        </div>
        <div class="rounded-2xl border border-hairline bg-white p-4">
          <svg lucideCalendarClock class="h-4 w-4 text-brand"></svg>
          <p class="mt-3 text-2xl font-extrabold text-ink">{{ memberSince() }}</p>
          <p class="text-[10px] font-semibold uppercase tracking-widest text-muted-ink mt-1">Member Since</p>
        </div>
      </div>

      <!-- Personal Info Form (TASK-48) - placed under the counts -->
      <div class="rounded-2xl border border-hairline bg-white p-6 md:p-8">
        <h3 class="text-sm font-extrabold uppercase tracking-widest text-ink">Personal Information</h3>
        <div class="mt-5 grid gap-4 md:grid-cols-2">
          <label class="block">
            <span class="text-[10px] font-semibold uppercase tracking-widest text-muted-ink">Full Name</span>
            <input 
              [(ngModel)]="form().fullName" 
              [disabled]="!editing()"
              class="mt-1.5 w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:outline-none focus:border-brand disabled:bg-surface-alt" 
            />
          </label>
          <label class="block">
            <span class="text-[10px] font-semibold uppercase tracking-widest text-muted-ink">
              Email Address
              <span class="ml-1 text-muted-ink font-normal normal-case tracking-normal">(immutable)</span>
            </span>
            <input 
              [value]="form().email" 
              disabled
              class="mt-1.5 w-full rounded-xl border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:outline-none text-muted-ink cursor-not-allowed" 
            />
          </label>
          <div class="block">
            <span class="text-[10px] font-semibold uppercase tracking-widest text-muted-ink">Phone Number</span>
            <ui-phone-input [(ngModel)]="form().phone" [disabled]="!editing() || saving()"></ui-phone-input>
          </div>
        </div>
      </div>

      <!-- Rewards & Referral Widgets (TASK-50, TASK-51) -->
      <div class="grid gap-6 md:grid-cols-2">
        <!-- Rewards Card -->
        <div class="rounded-2xl border border-hairline bg-white p-6">
          <div class="flex items-center justify-between border-b border-hairline pb-4">
            <div class="flex items-center gap-2">
              <svg lucideGift class="h-5 w-5 text-brand"></svg>
              <h3 class="text-sm font-extrabold uppercase tracking-widest text-ink">Nigson Rewards</h3>
            </div>
            <span class="rounded-full bg-brand/10 px-3 py-1 text-xs font-bold text-brand">
              {{ currentTier() }} Tier
            </span>
          </div>
          
          <div class="mt-4 space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-xs text-muted-ink">Available Points Balance:</span>
              <span class="text-lg font-black text-ink">{{ rewardBalance() }}</span>
            </div>
            <p class="text-xs text-muted-ink leading-relaxed">
              Earn points on every wholesale purchase. Redeem points toward shipping or invoice discounts at checkout.
            </p>
          </div>
        </div>

        <!-- Referral Program Card (TASK-51) -->
        <div class="rounded-2xl border border-hairline bg-white p-6">
          <div class="flex items-center justify-between border-b border-hairline pb-4">
            <div class="flex items-center gap-2">
              <svg lucideShare2 class="h-5 w-5 text-brand"></svg>
              <h3 class="text-sm font-extrabold uppercase tracking-widest text-ink">Referral Program</h3>
            </div>
            <span *ngIf="accountSvc.referral()" class="text-xs font-semibold text-muted-ink">
              {{ accountSvc.referral()?.qualifiedReferralCount ?? 0 }} Qualified Referrals
            </span>
          </div>

          <div class="mt-4 space-y-3">
            <p class="text-xs text-muted-ink">
              Share your personal referral link with other businesses to earn rewards on their first qualifying order.
            </p>
            
            <div *ngIf="accountSvc.referral()" class="flex items-center gap-2">
              <input 
                [value]="accountSvc.referral()?.link || accountSvc.referral()?.code" 
                readonly 
                class="flex-1 rounded-xl border border-hairline bg-surface-alt px-3 py-2 text-xs font-mono text-ink"
              />
              <button 
                (click)="copyReferral()" 
                class="inline-flex items-center gap-1 rounded-xl bg-brand text-white px-3 py-2 text-xs font-semibold hover:bg-brand-deep transition-colors"
              >
                <svg *ngIf="!copied()" lucideCopy class="h-3.5 w-3.5"></svg>
                <svg *ngIf="copied()" lucideCheck class="h-3.5 w-3.5"></svg>
                {{ copied() ? 'Copied' : 'Copy' }}
              </button>
            </div>

            <div *ngIf="!accountSvc.referral()" class="mt-1 space-y-3">
              <p class="text-xs text-muted-ink">
                Create your referral code to get a shareable link. Rewards are credited once a referred business places its first qualifying order.
              </p>
              <button
                (click)="createReferral()"
                [disabled]="accountSvc.loadingReferral() || creatingReferral()"
                class="inline-flex items-center gap-1.5 rounded-xl bg-brand text-white px-3 py-2 text-xs font-semibold hover:bg-brand-deep transition-colors disabled:opacity-50"
              >
                <svg *ngIf="creatingReferral()" lucideLoader2 class="h-3.5 w-3.5 animate-spin"></svg>
                {{ creatingReferral() ? 'Creating…' : 'Create referral code' }}
              </button>
              <p *ngIf="accountSvc.referralError()" class="text-xs font-semibold text-rose-600">
                {{ accountSvc.referralError() }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Partner Applications (moved to bottom) -->
      <app-partner-status />
    </div>
  `
})
export class ProfileComponent implements OnInit {
  shop = inject(ShopService);
  accountSvc = inject(AccountService);
  orderSvc = inject(OrderService);
  quoteSvc = inject(QuoteService);
  
  user = this.shop.user;
  editing = signal(false);
  saving = signal(false);
  saveSuccess = signal(false);
  saveError = signal<string | null>(null);
  copied = signal(false);
  creatingReferral = signal(false);
  avatarUrl = signal<string | null>(null);

  form = signal<{ fullName: string; email: string; phone: string }>({
    fullName: '',
    email: '',
    phone: ''
  });
  
  ordersCount = signal(0);
  memberSince = signal('2026');

  rewardBalance = signal('0 pts');
  currentTier = signal('Standard');

  ngOnInit() {
    this.resetForm();
    // TASK-47: Load profile from GET /api/v1/me
    this.accountSvc.loadProfile().subscribe(p => {
      if (p) {
        this.form.set({
          fullName: p.displayName,
          email: p.email,
          phone: p.phone ?? ''
        });
      }
    });

    // TASK-41 & TASK-47: Load orders & quotes count
    this.orderSvc.loadOrders(0, 1).subscribe();
    this.quoteSvc.loadQuotes(0, 1).subscribe();

    // TASK-50: Load rewards
    this.accountSvc.loadRewards().subscribe(r => {
      if (r && r.availableByCurrency && r.availableByCurrency.length > 0) {
        const bal = r.availableByCurrency[0];
        this.rewardBalance.set(`${bal.availableMinorUnits.toLocaleString()} pts`);
        if (bal.tierName) this.currentTier.set(bal.tierName);
      }
    });

    // TASK-51: Load referral
    this.accountSvc.loadReferral().subscribe();

    if (this.user()?.createdAt) {
      const date = new Date(this.user()!.createdAt);
      this.memberSince.set(date.toLocaleDateString(undefined, { month: "short", year: "numeric" }));
    }
  }

  resetForm() {
    const u = this.user();
    if (u) {
      this.form.set({
        fullName: u.fullName,
        email: u.email,
        phone: u.phone ?? ""
      });
    }
  }

  startEditing() {
    this.editing.set(true);
    this.saveSuccess.set(false);
    this.saveError.set(null);
  }

  cancelEdit() {
    this.saveError.set(null);
    this.editing.set(false);
    this.resetForm();
  }

  onAvatar(event: any) {
    const file = event.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      this.avatarUrl.set(String(reader.result));
    };
    reader.readAsDataURL(file);
  }

  removePhoto() {
    this.avatarUrl.set(null);
  }

  save() {
    if (this.saving()) return;
    this.saveSuccess.set(false);
    this.saveError.set(null);
    const raw = this.form().phone.trim();
    const phone = raw ? normalizePhoneNumber(raw) : null;
    if (raw && !phone) {
      this.saveError.set('Enter a complete phone number, for example 08012345678 or +2348012345678.');
      return;
    }
    this.saving.set(true);
    this.accountSvc.updateProfile({displayName: this.form().fullName, phone}).subscribe({
      next: updated => {
        this.saving.set(false);
        if (!updated) { this.saveError.set('Profile changes were not confirmed. Please reload before retrying.'); return; }
        this.form.set({fullName:updated.displayName,email:updated.email,phone:updated.phone??''});
        this.editing.set(false);
        this.saveSuccess.set(true);
        setTimeout(() => this.saveSuccess.set(false), 3000);
      },
      error: error => {
        this.saving.set(false);
        this.saveError.set(apiFailureDetail(error,'Unable to save your profile. Please retry.'));
      }
    });
  }

  copyReferral() {
    const link = this.accountSvc.referral()?.link || this.accountSvc.referral()?.code;
    if (link) {
      navigator.clipboard?.writeText(link);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    }
  }

  /** API-161: create the stable own referral code; failures render inside the card. */
  createReferral() {
    if (this.creatingReferral()) return;
    this.creatingReferral.set(true);
    this.accountSvc.createReferral().subscribe(ref => {
      this.creatingReferral.set(false);
      if (ref) this.copied.set(false);
    });
  }

  initialsOf(name?: string) {
    if (!name) return 'U';
    return name.split(' ').map((n: string) => n[0]).join('').substring(0, 2).toUpperCase();
  }

  avatarBg(): string {
    const name = this.user()?.fullName ?? "";
    const colors = ["#10B981","#2563EB","#7C3AED","#DB2777","#D97706"];
    let h = 0;
    for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
    return colors[h % colors.length];
  }
}

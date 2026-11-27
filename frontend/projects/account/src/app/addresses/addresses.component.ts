import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccountService, AddressDTO, AddressInput } from 'shared-ui';
import { 
  LucideMapPin, LucidePlus, LucidePencil, LucideTrash2, LucideCheck, 
  LucideX, LucideLoader2, LucideStar
} from '@lucide/angular';

@Component({
  selector: 'app-addresses',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    LucideMapPin, LucidePlus, LucidePencil, LucideTrash2, LucideCheck,
    LucideX, LucideLoader2, LucideStar
  ],
  template: `
    <div class="space-y-6">
      <div class="rounded-2xl border border-hairline bg-white p-6 md:p-8">
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p class="text-[10px] font-semibold uppercase tracking-widest text-brand">Delivery</p>
            <h2 class="text-2xl font-extrabold text-ink">Saved Addresses</h2>
          </div>
          <button
            *ngIf="!showForm()"
            (click)="openAddForm()"
            class="inline-flex items-center gap-2 rounded-full bg-brand text-white px-5 py-2.5 text-sm font-semibold hover:bg-brand-deep transition-colors shadow-sm"
          >
            <svg lucidePlus class="h-4 w-4"></svg> Add New Address
          </button>
        </div>
      </div>

      <!-- Add/Edit Address Form -->
      <div *ngIf="showForm()" class="rounded-2xl border border-hairline bg-white p-6 md:p-8 shadow-sm">
        <div class="flex items-center justify-between border-b border-hairline pb-4 mb-6">
          <h3 class="text-base font-bold text-ink">
            {{ editingId() ? 'Edit Address' : 'New Address' }}
          </h3>
          <button (click)="cancelForm()" class="text-muted-ink hover:text-ink">
            <svg lucideX class="h-5 w-5"></svg>
          </button>
        </div>

        <div *ngIf="formError()" class="mb-4 rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-700">
          {{ formError() }}
        </div>

        <form (submit)="saveAddress($event)" class="space-y-4">
          <div class="grid gap-4 md:grid-cols-2">
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-muted-ink mb-1">
                Recipient Name <span class="text-brand">*</span>
              </label>
              <input
                required
                [(ngModel)]="formData.recipient"
                name="recipient"
                placeholder="Full Name"
                class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:outline-none focus:border-brand"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-muted-ink mb-1">
                Phone Number <span class="text-brand">*</span>
              </label>
              <input
                required
                [(ngModel)]="formData.phone"
                name="phone"
                placeholder="+234 ..."
                class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:outline-none focus:border-brand"
              />
            </div>
          </div>

          <div>
            <label class="block text-xs font-semibold uppercase tracking-wider text-muted-ink mb-1">
              Address Label (optional)
            </label>
            <input
              [(ngModel)]="formData.label"
              name="label"
              placeholder="e.g. Lagos Warehouse, Office, Home"
              class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:outline-none focus:border-brand"
            />
          </div>

          <div>
            <label class="block text-xs font-semibold uppercase tracking-wider text-muted-ink mb-1">
              Street Address <span class="text-brand">*</span>
            </label>
            <input
              required
              [(ngModel)]="formData.line1"
              name="line1"
              placeholder="Plot 123, Industrial Avenue"
              class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:outline-none focus:border-brand"
            />
          </div>

          <div>
            <label class="block text-xs font-semibold uppercase tracking-wider text-muted-ink mb-1">
              Floor, door and delivery instructions (optional)
            </label>
            <input
              [(ngModel)]="formData.line2"
              name="line2"
              maxlength="200"
              placeholder="2nd Floor, Door B – leave at the door"
              class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:outline-none focus:border-brand"
            />
            <p class="mt-1 text-xs text-muted-ink">Only floor, door and extra delivery instructions. The rider will see these notes.</p>
          </div>

          <div class="grid gap-4 md:grid-cols-3">
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-muted-ink mb-1">
                City <span class="text-brand">*</span>
              </label>
              <input
                required
                [(ngModel)]="formData.city"
                name="city"
                placeholder="Ikeja"
                class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:outline-none focus:border-brand"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-muted-ink mb-1">
                State / Region
              </label>
              <input
                [(ngModel)]="formData.region"
                name="region"
                placeholder="Lagos"
                class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:outline-none focus:border-brand"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-muted-ink mb-1">
                Postal Code
              </label>
              <input
                [(ngModel)]="formData.postalCode"
                name="postalCode"
                placeholder="100001"
                class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:outline-none focus:border-brand"
              />
            </div>
          </div>

          <div class="grid gap-4 md:grid-cols-2">
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-muted-ink mb-1">
                Delivery Latitude (optional)
              </label>
              <input
                type="number"
                step="any"
                min="-90"
                max="90"
                [(ngModel)]="formData.latitude"
                name="latitude"
                placeholder="6.5244"
                class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:outline-none focus:border-brand"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-muted-ink mb-1">
                Delivery Longitude (optional)
              </label>
              <input
                type="number"
                step="any"
                min="-180"
                max="180"
                [(ngModel)]="formData.longitude"
                name="longitude"
                placeholder="3.3792"
                class="w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:outline-none focus:border-brand"
              />
            </div>
            <p class="text-xs text-muted-ink md:col-span-2">
              Add both coordinates to request PandaGo delivery quotes. Coordinates are sent to the delivery provider only when you request a quote.
            </p>
          </div>

          <div class="flex items-center gap-2 pt-2">
            <input
              type="checkbox"
              id="isDefault"
              [(ngModel)]="formData.isDefault"
              name="isDefault"
              class="h-4 w-4 rounded border-gray-300 text-brand focus:ring-brand"
            />
            <label for="isDefault" class="text-sm text-ink cursor-pointer">
              Set as primary delivery address
            </label>
          </div>

          <div class="flex justify-end gap-3 pt-4 border-t border-hairline">
            <button
              type="button"
              (click)="cancelForm()"
              class="rounded-full border border-hairline px-5 py-2.5 text-xs font-semibold hover:border-brand hover:text-brand transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              [disabled]="saving()"
              class="inline-flex items-center gap-1.5 rounded-full bg-brand text-white px-6 py-2.5 text-xs font-semibold hover:bg-brand-deep transition-colors disabled:opacity-50"
            >
              <svg *ngIf="!saving()" lucideCheck class="h-3.5 w-3.5"></svg>
              <svg *ngIf="saving()" lucideLoader2 class="h-3.5 w-3.5 animate-spin"></svg>
              {{ saving() ? 'Saving...' : 'Save Address' }}
            </button>
          </div>
        </form>
      </div>

      <!-- Loading State -->
      <div *ngIf="accountSvc.loadingAddresses()" class="rounded-2xl border border-hairline bg-white p-16 text-center">
        <svg lucideLoader2 class="mx-auto h-8 w-8 animate-spin text-brand"></svg>
        <p class="mt-4 text-sm text-muted-ink">Loading addresses...</p>
      </div>

      <!-- Empty State -->
      <div *ngIf="!accountSvc.loadingAddresses() && accountSvc.addresses().length === 0 && !showForm()" class="rounded-2xl border border-hairline bg-white p-16 text-center">
        <div class="mx-auto grid h-16 w-16 place-items-center rounded-full bg-surface-alt">
          <svg lucideMapPin class="h-8 w-8 text-muted-ink"></svg>
        </div>
        <p class="mt-4 text-lg font-bold text-ink">No saved addresses</p>
        <p class="mt-2 text-sm text-muted-ink">Add your warehouse, office, or delivery locations for faster order fulfillment.</p>
        <button (click)="openAddForm()" class="mt-6 inline-flex items-center gap-2 rounded-full bg-brand text-white px-6 py-3 text-sm font-semibold hover:bg-brand-deep transition-colors shadow-sm">
          <svg lucidePlus class="h-4 w-4"></svg> Add Address
        </button>
      </div>

      <!-- Address Cards Grid -->
      <div *ngIf="!accountSvc.loadingAddresses() && accountSvc.addresses().length > 0" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div *ngFor="let addr of accountSvc.addresses()" class="rounded-2xl border border-hairline bg-white p-6 flex flex-col justify-between shadow-sm relative">
          <div>
            <div class="flex items-center justify-between gap-2">
              <span class="font-bold text-sm text-ink truncate">
                {{ addr.label || addr.recipient }}
              </span>
              <span *ngIf="addr.isDefault" class="inline-flex items-center gap-1 rounded-full bg-brand/10 px-2.5 py-0.5 text-[10px] font-bold text-brand">
                <svg lucideStar class="h-3 w-3 fill-brand"></svg> Primary
              </span>
            </div>

            <p class="mt-3 text-sm font-semibold text-ink">{{ addr.recipient }}</p>
            <p class="mt-1 text-xs text-muted-ink leading-relaxed">
              {{ addr.line1 }}<br/>
              <span *ngIf="addr.line2">{{ addr.line2 }}<br/></span>
              {{ addr.city }}<span *ngIf="addr.region">, {{ addr.region }}</span> <span *ngIf="addr.postalCode">{{ addr.postalCode }}</span><br/>
              Nigeria
            </p>
            <p class="mt-2 text-xs text-muted-ink">
              Phone: <strong class="text-ink font-mono">{{ addr.phone }}</strong>
            </p>
          </div>

          <div class="mt-6 flex items-center justify-between border-t border-hairline pt-4">
            <button
              (click)="editAddress(addr)"
              class="inline-flex items-center gap-1 text-xs font-semibold text-ink hover:text-brand transition-colors"
            >
              <svg lucidePencil class="h-3.5 w-3.5"></svg> Edit
            </button>
            <button
              (click)="deleteAddress(addr)"
              class="inline-flex items-center gap-1 text-xs font-semibold text-rose-600 hover:text-rose-700 transition-colors"
            >
              <svg lucideTrash2 class="h-3.5 w-3.5"></svg> Delete
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AddressesComponent implements OnInit {
  accountSvc = inject(AccountService);

  showForm = signal(false);
  editingId = signal<string | null>(null);
  saving = signal(false);
  formError = signal<string | null>(null);

  formData: AddressInput = {
    recipient: '',
    phone: '',
    line1: '',
    line2: '',
    city: '',
    region: '',
    postalCode: '',
    countryCode: 'NG',
    latitude: null,
    longitude: null,
    isDefault: false
  };

  ngOnInit() {
    this.accountSvc.loadAddresses().subscribe();
  }

  openAddForm() {
    this.editingId.set(null);
    this.formData = {
      recipient: '',
      phone: '',
      line1: '',
      line2: '',
      city: '',
      region: '',
      postalCode: '',
      countryCode: 'NG',
      latitude: null,
      longitude: null,
      isDefault: this.accountSvc.addresses().length === 0
    };
    this.formError.set(null);
    this.showForm.set(true);
  }

  editAddress(addr: AddressDTO) {
    this.editingId.set(addr.id);
    this.formData = {
      label: addr.label ?? '',
      recipient: addr.recipient,
      phone: addr.phone,
      line1: addr.line1,
      line2: addr.line2 ?? '',
      city: addr.city,
      region: addr.region ?? '',
      postalCode: addr.postalCode ?? '',
      countryCode: addr.countryCode || 'NG',
      latitude: addr.latitude ?? null,
      longitude: addr.longitude ?? null,
      isDefault: addr.isDefault
    };
    this.formError.set(null);
    this.showForm.set(true);
  }

  cancelForm() {
    this.showForm.set(false);
    this.editingId.set(null);
  }

  saveAddress(event: Event) {
    event.preventDefault();
    this.formError.set(null);

    if (!this.formData.recipient || !this.formData.phone || !this.formData.line1 || !this.formData.city) {
      this.formError.set('Please fill in all required fields.');
      return;
    }
    const latitude = this.formData.latitude;
    const longitude = this.formData.longitude;
    if ((latitude == null) !== (longitude == null) ||
        latitude != null && (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) ||
        longitude != null && (!Number.isFinite(longitude) || longitude < -180 || longitude > 180)) {
      this.formError.set('Enter both valid delivery coordinates, or leave both blank.');
      return;
    }

    this.saving.set(true);
    const id = this.editingId();

    if (id) {
      const current = this.accountSvc.addresses().find(a => a.id === id);
      this.accountSvc.updateAddress(id, this.formData, current?.version).subscribe({
        next: (updated) => {
          this.saving.set(false);
          if (updated) {
            this.showForm.set(false);
          } else {
            this.formError.set('Failed to update address. Please check your details.');
          }
        },
        error: () => {
          this.saving.set(false);
          this.formError.set('An error occurred while saving the address.');
        }
      });
    } else {
      this.accountSvc.addAddress(this.formData).subscribe({
        next: (created) => {
          this.saving.set(false);
          if (created) {
            this.showForm.set(false);
          } else {
            this.formError.set('Failed to create address. Please check your details.');
          }
        },
        error: () => {
          this.saving.set(false);
          this.formError.set('An error occurred while creating the address.');
        }
      });
    }
  }

  deleteAddress(addr: AddressDTO) {
    if (!confirm(`Delete address "${addr.label || addr.line1}"?`)) return;
    this.accountSvc.deleteAddress(addr.id, addr.version).subscribe();
  }
}

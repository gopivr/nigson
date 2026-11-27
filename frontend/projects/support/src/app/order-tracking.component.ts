import { Component, signal, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OrderService, OrderTracking } from 'shared-ui';

@Component({
  selector: 'app-order-tracking',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="max-w-3xl mx-auto px-4 py-12">
      <div class="mb-8 text-center">
        <h1 class="text-3xl font-bold text-gray-900 tracking-tight">Shipment Tracking</h1>
        <p class="text-gray-600 mt-2">Enter your Order ID to track real-time delivery status from the delivery provider.</p>
        <div class="mt-4">
          <a routerLink="/support" class="inline-flex items-center text-sm font-semibold text-blue-600 hover:text-blue-800">
            &larr; Need help? Submit a support request
          </a>
        </div>
      </div>

      <div class="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-8">
        <form (ngSubmit)="track()" class="flex flex-col sm:flex-row gap-4">
          <input
            type="text"
            [(ngModel)]="orderId"
            name="orderId"
            placeholder="Enter Order ID (e.g. ord-12345 or UUID)"
            required
            class="flex-1 px-4 py-3 border border-gray-300 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" />
          <button
            type="submit"
            [disabled]="loading() || !orderId"
            class="px-6 py-3 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white font-semibold rounded-xl text-sm transition">
            {{ loading() ? 'Tracking...' : 'Track Order' }}
          </button>
        </form>
      </div>

      <div *ngIf="error()" class="p-4 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-sm mb-6">
        {{ error() }}
      </div>

      <div *ngIf="tracking()" class="bg-white border border-gray-200 rounded-2xl p-8 shadow-sm space-y-6">
        <div class="flex flex-col sm:flex-row sm:items-center justify-between border-b pb-6 gap-2">
          <div>
            <span class="text-xs font-semibold tracking-wider uppercase text-gray-500">Order ID</span>
            <h2 class="text-xl font-bold text-gray-900">{{ tracking()?.orderId }}</h2>
          </div>
          <div>
            <span [class]="getStatusBadgeClass(tracking()?.status || '')" class="px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider">
              {{ tracking()?.status }}
            </span>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 bg-gray-50 p-4 rounded-xl text-sm">
          <div>
            <span class="text-gray-500 block text-xs">Courier</span>
            <span class="font-semibold text-gray-800">{{ tracking()?.carrier || 'Carrier not assigned' }}</span>
          </div>
          <div>
            <span class="text-gray-500 block text-xs">Tracking Number</span>
            <span class="font-mono font-semibold text-gray-800">{{ tracking()?.trackingNumber || 'Pending Dispatch' }}</span>
          </div>
          <div *ngIf="tracking()?.estimatedDelivery">
            <span class="text-gray-500 block text-xs">Estimated Delivery</span>
            <span class="font-semibold text-gray-800">{{ tracking()?.estimatedDelivery | date:'mediumDate' }}</span>
          </div>
        </div>

        <div *ngIf="tracking()?.scans?.length" class="mt-6">
          <h3 class="text-sm font-bold text-gray-900 mb-4 uppercase tracking-wider">Tracking Timeline</h3>
          <div class="space-y-4 border-l-2 border-blue-500 pl-4 ml-2">
            <div *ngFor="let s of tracking()?.scans" class="relative">
              <div class="absolute -left-[21px] top-1 w-2.5 h-2.5 rounded-full bg-blue-600"></div>
              <div class="font-semibold text-sm text-gray-900">{{ s.status }}</div>
              <div class="text-xs text-gray-500">{{ s.timestamp | date:'medium' }} &bull; {{ s.location || 'Lagos Hub' }}</div>
              <div *ngIf="s.description" class="text-xs text-gray-600 mt-1">{{ s.description }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class OrderTrackingComponent implements OnInit {
  private orderService = inject(OrderService);
  private route = inject(ActivatedRoute);

  orderId = '';
  loading = signal(false);
  tracking = signal<OrderTracking | null>(null);
  error = signal<string | null>(null);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.orderId = id;
      this.track();
    }
  }

  track() {
    if (!this.orderId.trim()) return;
    this.loading.set(true);
    this.error.set(null);
    this.tracking.set(null);

    this.orderService.getTracking(this.orderId.trim()).subscribe({
      next: (data: OrderTracking | null) => {
        this.loading.set(false);
        if (data) {
          this.tracking.set(data);
        } else {
          this.error.set('No tracking information available for this order yet.');
        }
      },
      error: (err: any) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || 'Unable to retrieve tracking information for this order.');
      }
    });
  }

  getStatusBadgeClass(status: string) {
    switch (status.toUpperCase()) {
      case 'DELIVERED': return 'bg-emerald-100 text-emerald-800';
      case 'SHIPPED': return 'bg-blue-100 text-blue-800';
      case 'PROCESSING': return 'bg-purple-100 text-purple-800';
      case 'CANCELLED': return 'bg-red-100 text-red-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  }
}

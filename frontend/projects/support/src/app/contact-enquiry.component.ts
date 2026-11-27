import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MarketingService } from 'shared-ui';

@Component({
  selector: 'app-contact-enquiry',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="max-w-3xl mx-auto px-4 py-12">
      <div class="mb-8 text-center">
        <h1 class="text-3xl font-bold text-gray-900 tracking-tight">Customer Support & Help Center</h1>
        <p class="text-gray-600 mt-2">Submit an enquiry, log a complaint, or track your delivery.</p>
        <div class="mt-4">
          <a routerLink="/support/tracking" class="inline-flex items-center text-sm font-semibold text-blue-600 hover:text-blue-800">
            Have an existing order? Track your shipment &rarr;
          </a>
        </div>
      </div>

      <div *ngIf="submitted()" class="bg-emerald-50 border border-emerald-200 rounded-xl p-6 text-center text-emerald-800 shadow-sm">
        <div class="w-12 h-12 bg-emerald-100 rounded-full flex items-center justify-center mx-auto mb-3 text-emerald-600 text-xl font-bold">&#10003;</div>
        <h3 class="font-bold text-lg">Thank you for reaching out!</h3>
        <p class="text-sm mt-1">Your message has been logged. Our Nigerian support team will respond within 24 hours.</p>
        <button (click)="resetForm()" class="mt-4 px-4 py-2 bg-emerald-600 text-white rounded-lg text-sm font-semibold hover:bg-emerald-700">
          Submit Another Request
        </button>
      </div>

      <form *ngIf="!submitted()" (ngSubmit)="onSubmit()" class="bg-white border border-gray-200 rounded-2xl p-8 shadow-sm space-y-6">
        <div>
          <label class="block text-sm font-semibold text-gray-700 mb-2">Request Type</label>
          <div class="grid grid-cols-2 gap-4">
            <button
              type="button"
              (click)="type.set('CONTACT')"
              [class]="type() === 'CONTACT' ? 'border-blue-600 bg-blue-50 text-blue-700 font-semibold' : 'border-gray-200 bg-gray-50 text-gray-600'"
              class="border rounded-xl p-4 text-center text-sm transition">
              General Enquiry
            </button>
            <button
              type="button"
              (click)="type.set('COMPLAINT')"
              [class]="type() === 'COMPLAINT' ? 'border-blue-600 bg-blue-50 text-blue-700 font-semibold' : 'border-gray-200 bg-gray-50 text-gray-600'"
              class="border rounded-xl p-4 text-center text-sm transition">
              Formal Complaint
            </button>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label class="block text-sm font-semibold text-gray-700 mb-1">Full Name</label>
            <input
              type="text"
              [(ngModel)]="name"
              name="name"
              required
              placeholder="e.g. Adebayo Ogunlesi"
              class="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" />
          </div>
          <div>
            <label class="block text-sm font-semibold text-gray-700 mb-1">Email Address</label>
            <input
              type="email"
              [(ngModel)]="email"
              name="email"
              required
              placeholder="adebayo@example.com"
              class="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" />
          </div>
        </div>

        <div>
          <label class="block text-sm font-semibold text-gray-700 mb-1">Subject</label>
          <input
            type="text"
            [(ngModel)]="subject"
            name="subject"
            required
            placeholder="Brief summary of your inquiry or issue"
            class="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" />
        </div>

        <div>
          <label class="block text-sm font-semibold text-gray-700 mb-1">Message</label>
          <textarea
            [(ngModel)]="message"
            name="message"
            required
            rows="5"
            placeholder="Describe your issue or question in detail..."
            class="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"></textarea>
        </div>

        <div *ngIf="error()" class="text-sm text-red-600 bg-red-50 p-3 rounded-lg border border-red-200">
          {{ error() }}
        </div>

        <button
          type="submit"
          [disabled]="loading() || !name || !email || !subject || !message"
          class="w-full py-3 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white font-semibold rounded-xl text-sm transition shadow-sm">
          {{ loading() ? 'Sending...' : 'Submit Request' }}
        </button>
      </form>
    </div>
  `
})
export class ContactEnquiryComponent {
  private marketing = inject(MarketingService);

  type = signal<'CONTACT' | 'COMPLAINT'>('CONTACT');
  name = '';
  email = '';
  subject = '';
  message = '';
  loading = signal(false);
  submitted = signal(false);
  error = signal<string | null>(null);

  onSubmit() {
    if (!this.name || !this.email || !this.subject || !this.message) return;
    this.loading.set(true);
    this.error.set(null);

    this.marketing.submitEnquiry({
      type: this.type(),
      subject: this.subject,
      name: this.name,
      email: this.email,
      message: this.message
    }).subscribe({
      next: (res) => {
        this.loading.set(false);
        if (res.success) {
          this.submitted.set(true);
        } else {
          this.error.set(res.error || 'Failed to submit enquiry. Please try again.');
        }
      },
      error: (err: any) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || 'Failed to submit enquiry. Please try again.');
      }
    });
  }

  resetForm() {
    this.name = '';
    this.email = '';
    this.subject = '';
    this.message = '';
    this.submitted.set(false);
    this.error.set(null);
  }
}

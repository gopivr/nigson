import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CmsService, MarketingService, PageHeaderComponent, InputComponent, ButtonComponent } from 'shared-ui';
import { LucideMapPin, LucidePhone, LucideMail, LucideMessageCircle, LucideCheckCircle2 } from '@lucide/angular';

@Component({
  selector: 'app-contact',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    PageHeaderComponent,
    LucideMapPin,
    LucidePhone,
    LucideMail,
    LucideMessageCircle,
    LucideCheckCircle2
  ],
  template: `
    <div class="bg-surface min-h-screen">
      <ng-container *ngIf="hero().visible">
        <lib-page-header [eyebrow]="hero().t('eyebrow')" [title]="hero().t('heading')" [subtitle]="hero().t('body')"></lib-page-header>
      </ng-container>

      <section class="container-page py-16 grid gap-10 lg:grid-cols-3">
        
        <!-- Info column -->
        <div *ngIf="info().visible" class="space-y-6">
          <div class="rounded-2xl border border-border bg-background p-6">
            <svg lucideMapPin class="h-6 w-6 text-brand"></svg>
            <h3 class="mt-3 font-semibold">{{ info().t('officeTitle') }}</h3>
            <p class="mt-1 whitespace-pre-line text-sm text-muted-foreground">{{ info().t('address') }}</p>
          </div>
          
          <div class="rounded-2xl border border-border bg-background p-6">
            <svg lucidePhone class="h-6 w-6 text-brand"></svg>
            <h3 class="mt-3 font-semibold">{{ info().t('phoneTitle') }}</h3>
            <a [href]="'tel:' + sanitizePhone(info().t('phone'))" class="mt-1 block text-sm text-muted-foreground hover:text-brand">
              {{ info().t('phone') }}
            </a>
          </div>
          
          <div class="rounded-2xl border border-border bg-background p-6">
            <svg lucideMail class="h-6 w-6 text-brand"></svg>
            <h3 class="mt-3 font-semibold">{{ info().t('emailTitle') }}</h3>
            <a [href]="'mailto:' + info().t('email')" class="mt-1 block text-sm text-muted-foreground hover:text-brand">
              {{ info().t('email') }}
            </a>
          </div>
          
          <a
            [href]="info().t('whatsappLink')"
            class="flex items-center gap-3 rounded-2xl bg-gradient-brand p-6 text-brand-foreground shadow-brand hover:opacity-95"
          >
            <svg lucideMessageCircle class="h-6 w-6"></svg>
            <div>
              <p class="font-semibold">{{ info().t('whatsappTitle') }}</p>
              <p class="text-sm text-background/80">{{ info().t('whatsappBody') }}</p>
            </div>
          </a>
        </div>

        <!-- Form and Map Column -->
        <div class="space-y-8" [ngClass]="info().visible ? 'lg:col-span-2' : 'lg:col-span-3'">
          
          <form *ngIf="form().visible" (submit)="onSubmit($event)" class="rounded-2xl border border-border bg-background p-8 shadow-elegant space-y-5">
            <ng-container *ngIf="submitted(); else formFields">
              <div class="text-center py-10">
                <svg lucideCheckCircle2 class="mx-auto h-14 w-14 text-brand"></svg>
                <h3 class="mt-4 text-2xl font-semibold">Message sent</h3>
                <p class="mt-2 text-muted-foreground">{{ form().t('successText') }}</p>
              </div>
            </ng-container>
            
            <ng-template #formFields>
              <h2 class="text-2xl font-semibold">{{ form().t('heading') }}</h2>
              <div *ngIf="submitError()" class="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
                {{ submitError() }}
              </div>
              <div class="grid gap-5 md:grid-cols-2">
                <div>
                  <label class="block text-sm font-medium mb-1.5">{{ form().t('nameLabel') }} <span class="text-brand">*</span></label>
                  <input type="text" [(ngModel)]="f.name" name="name" required class="w-full rounded-lg border border-border bg-background px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                </div>
                <div>
                  <label class="block text-sm font-medium mb-1.5">{{ form().t('emailLabel') }} <span class="text-brand">*</span></label>
                  <input type="email" [(ngModel)]="f.email" name="email" required class="w-full rounded-lg border border-border bg-background px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
                </div>
              </div>
              
              <div>
                <label class="block text-sm font-medium mb-1.5">{{ form().t('subjectLabel') }}</label>
                <input type="text" [(ngModel)]="f.subject" name="subject" class="w-full rounded-lg border border-border bg-background px-4 py-2.5 text-sm focus:outline-none focus:border-brand" />
              </div>
              
              <div>
                <label class="block text-sm font-medium mb-1.5">Type</label>
                <select [(ngModel)]="f.type" name="type" class="w-full rounded-lg border border-border bg-background px-4 py-2.5 text-sm focus:outline-none focus:border-brand">
                  <option>General inquiry</option>
                  <option>Sales / Wholesale</option>
                  <option>Complaint</option>
                  <option>Partnership</option>
                </select>
              </div>
              
              <div>
                <label class="block text-sm font-medium mb-1.5">{{ form().t('messageLabel') }} <span class="text-brand">*</span></label>
                <textarea [(ngModel)]="f.message" name="message" rows="5" required class="w-full rounded-lg border border-border bg-background px-4 py-2.5 text-sm focus:outline-none focus:border-brand"></textarea>
              </div>
              
              <button type="submit" class="w-full rounded-full bg-brand text-brand-foreground py-3 text-sm font-semibold hover:bg-brand-deep transition-colors">
                {{ form().t('btnLabel') }}
              </button>
            </ng-template>
          </form>

          <div class="rounded-2xl overflow-hidden border border-border">
            <iframe
              title="Nigson HQ"
              class="w-full h-72"
              src="https://www.google.com/maps?q=Ikate+Lekki+Lagos&output=embed"
              loading="lazy"
            ></iframe>
          </div>
        </div>

      </section>
    </div>
  `
})
export class ContactComponent {
  marketingSvc = inject(MarketingService);
  submitting = this.marketingSvc.submittingEnquiry;
  submitError = signal<string | null>(null);
  cms = inject(CmsService);
  page = this.cms.getPageSections('contact');

  hero = computed(() => this.page.get('hero'));
  info = computed(() => this.page.get('info'));
  form = computed(() => this.page.get('form'));

  submitted = signal(false);

  f = {
    name: '',
    email: '',
    subject: '',
    type: 'General inquiry',
    message: ''
  };

  sanitizePhone(phone: string): string {
    return phone ? phone.replace(/\s/g, '') : '';
  }

  onSubmit(event: Event) {
    event.preventDefault();
    this.submitError.set(null);

    const enquiryType = this.f.type.toLowerCase().includes('complaint') ? 'COMPLAINT' : 'CONTACT';
    
    this.marketingSvc.submitEnquiry({
      type: enquiryType,
      name: this.f.name.trim(),
      email: this.f.email.trim(),
      subject: (this.f.subject || this.f.type).trim(),
      message: this.f.message.trim()
    }).subscribe({
      next: (res) => {
        if (res.success) {
          this.submitted.set(true);
        } else {
          this.submitError.set(res.error || 'Failed to send enquiry. Please check your information and try again.');
        }
      },
      error: () => {
        this.submitError.set('An unexpected error occurred. Please try again.');
      }
    });
  }
}

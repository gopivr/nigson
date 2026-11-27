import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { 
  LucideTruck, 
  LucideRefreshCcw, 
  LucideMessageCircleQuestion, 
  LucideLifeBuoy, 
  LucideHandshake 
} from '@lucide/angular';

type Page = {
  slug: string;
  title: string;
  intro: string;
  icon: string;
  sections: { heading: string; body: string }[];
};

const PAGES: Record<string, Page> = {
  shipping: {
    slug: "shipping",
    title: "Shipping Information",
    intro: "Fast, reliable delivery of Nigson products across Nigeria.",
    icon: "truck",
    sections: [
      {
        heading: "Nationwide delivery",
        body: "We dispatch orders from our Lagos fulfilment hub to all 36 states. Orders confirmed before 12 noon are processed the same business day; orders placed later ship the next business day.",
      },
      {
        heading: "Delivery timelines",
        body: "Lagos: 1–2 business days. South-West: 2–3 business days. Other states: 3–5 business days. Remote locations may take up to 7 business days.",
      },
      {
        heading: "Order tracking",
        body: "Every confirmed order includes live tracking from your account. Open My Orders and select an order to see its current status, timeline and delivery updates.",
      },
      {
        heading: "Wholesale & bulk shipments",
        body: "Distributor and bulk orders ship via dedicated logistics partners with palletised packaging and insurance. Delivery schedules are confirmed on your quote approval.",
      },
    ],
  },
  returns: {
    slug: "returns",
    title: "Returns & Exchanges",
    intro: "Simple, transparent returns when a product isn't right.",
    icon: "refresh-ccw",
    sections: [
      {
        heading: "Return window",
        body: "You may return an unused product in its original packaging within 7 days of delivery. Products with physical damage not caused by Nigson are not eligible.",
      },
      {
        heading: "How to start a return",
        body: "Sign in, open My Orders, select the order and choose the items you want to return. Our support team will confirm pickup or drop-off within one business day.",
      },
      {
        heading: "Refunds",
        body: "Once the returned item passes inspection, refunds are issued to your original payment method within 3–7 business days. Cancelled orders follow the same refund process.",
      },
      {
        heading: "Warranty",
        body: "All Nigson electronics carry a 6–12 month warranty against manufacturing defects. Present your order number to claim warranty support.",
      },
    ],
  },
  faqs: {
    slug: "faqs",
    title: "Frequently Asked Questions",
    intro: "Answers to the questions we hear most often.",
    icon: "message-circle-question",
    sections: [
      {
        heading: "Are Nigson products genuine?",
        body: "Yes. Every product is sourced through authorised channels, batch-verified and backed by a warranty against counterfeits.",
      },
      {
        heading: "Do you sell to individuals and businesses?",
        body: "Both. Shop any product in the catalogue, or register as a distributor for wholesale pricing, bulk discounts and dedicated account support.",
      },
      {
        heading: "How do I track my order?",
        body: "Sign in and open My Orders — each order shows live status from confirmation through delivery.",
      },
      {
        heading: "What payment methods do you accept?",
        body: "Credit card, debit card and net banking at checkout. Distributor orders can be settled by bank transfer against an approved quote.",
      },
      {
        heading: "Can I cancel an order?",
        body: "Confirmed orders can be cancelled before they ship from My Orders → Order Details. Paid orders receive a refund within 3–7 business days.",
      },
    ],
  },
  help: {
    slug: "help",
    title: "Help Center",
    intro: "We're here to help — reach us through any channel.",
    icon: "life-buoy",
    sections: [
      {
        heading: "Chat with us",
        body: "Use the chat assistant in the corner of any page for instant answers about products, pricing, orders and delivery.",
      },
      {
        heading: "WhatsApp support",
        body: "Message us on WhatsApp for quick order help, stock questions and product advice. Response time is typically under 15 minutes during business hours.",
      },
      {
        heading: "Contact our team",
        body: "For detailed enquiries, quotes or partnership discussions, use the Contact page and a Nigson specialist will reply within one business day.",
      },
      {
        heading: "Business hours",
        body: "Monday to Saturday, 8:00am – 6:00pm WAT. Online orders and chat are available around the clock.",
      },
    ],
  },
  affiliate: {
    slug: "affiliate",
    title: "Become an Affiliate",
    intro: "Earn by recommending Nigson products to your audience.",
    icon: "handshake",
    sections: [
      {
        heading: "How it works",
        body: "Apply for the affiliate programme. Approved affiliates will earn 5% on eligible retail purchases once referral tracking launches.",
      },
      {
        heading: "Who can join",
        body: "Content creators, tech reviewers, community leaders and retailers with an engaged audience are welcome. Approval is usually granted within 2 business days.",
      },
      {
        heading: "Payouts",
        body: "The programme provides monthly manual bank payouts of the full available commission balance. Earnings become available seven days after delivery; payout details are collected separately after approval.",
      },
      {
        heading: "Get started",
        body: "Apply through the Contact page with 'Affiliate' in the subject line, or register as a distributor if you also want to resell Nigson products directly.",
      },
    ],
  },
};


@Component({
  selector: 'app-support',
  standalone: true,
  imports: [
    CommonModule, RouterLink, 
    LucideTruck, LucideRefreshCcw, LucideMessageCircleQuestion, 
    LucideLifeBuoy, LucideHandshake
  ],
  template: `
    <div class="bg-surface min-h-screen" *ngIf="page()">
      <section class="border-b border-hairline bg-surface-alt py-20">
        <div class="container-page max-w-3xl">
          <p class="text-[11px] font-semibold uppercase tracking-[0.25em] text-brand">Support</p>
          <div class="mt-4 flex items-center gap-4">
            <span class="grid h-12 w-12 place-items-center rounded-2xl bg-brand/10">
              <ng-container [ngSwitch]="page()?.icon">
                <svg lucideTruck *ngSwitchCase="'truck'" class="h-6 w-6 text-brand" stroke-width="1.75"></svg>
                <svg lucideRefreshCcw *ngSwitchCase="'refresh-ccw'" class="h-6 w-6 text-brand" stroke-width="1.75"></svg>
                <svg lucideMessageCircleQuestion *ngSwitchCase="'message-circle-question'" class="h-6 w-6 text-brand" stroke-width="1.75"></svg>
                <svg lucideLifeBuoy *ngSwitchCase="'life-buoy'" class="h-6 w-6 text-brand" stroke-width="1.75"></svg>
                <svg lucideHandshake *ngSwitchCase="'handshake'" class="h-6 w-6 text-brand" stroke-width="1.75"></svg>
              </ng-container>
            </span>
            <h1 class="text-4xl font-extrabold tracking-[-0.02em] md:text-5xl">{{ page()?.title }}</h1>
          </div>
          <p class="mt-4 text-lg text-muted-foreground">{{ page()?.intro }}</p>
        </div>
      </section>

      <section class="container-page max-w-3xl py-14">
        <div class="space-y-8">
          <div *ngFor="let s of page()?.sections" class="rounded-2xl border border-hairline bg-white p-7">
            <h2 class="text-lg font-extrabold tracking-[-0.01em]">{{ s.heading }}</h2>
            <p class="mt-2.5 text-sm leading-relaxed text-muted-foreground">{{ s.body }}</p>
          </div>
        </div>

        <div class="mt-10 flex flex-wrap items-center gap-3">
          <a
            routerLink="/contact"
            class="rounded-full bg-brand px-6 py-3 text-sm font-semibold text-brand-foreground transition-colors hover:bg-brand-deep"
          >
            Contact us
          </a>
          <a
            routerLink="/products"
            class="rounded-full border border-ink px-6 py-3 text-sm font-semibold text-ink transition-colors hover:bg-ink hover:text-white"
          >
            Browse products
          </a>
        </div>
      </section>
    </div>
  `
})
export class SupportComponent {
  route = inject(ActivatedRoute);
  router = inject(Router);

  slug = toSignal(this.route.paramMap.pipe(map(params => params.get('page'))));
  
  page = computed(() => {
    const s = this.slug();
    if (!s) return undefined;
    if (s === 'affiliate') {
      this.router.navigate(['/distributor'], { queryParams: { tab: 'affiliate' } });
      return undefined;
    }
    const p = PAGES[s];
    if (!p) {
      this.router.navigate(['/home']);
      return undefined;
    }
    return p;
  });
}

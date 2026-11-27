import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MarketingService } from 'shared-ui';

export interface FooterLink {
  label: string;
  to?: string;
  queryParams?: Record<string, string>;
  href?: string;
}

export interface FooterColumn {
  heading: string;
  links: FooterLink[];
}

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './footer.html',
  styleUrls: ['./footer.css']
})
export class FooterComponent {
  marketingSvc = inject(MarketingService);

  newsletterEmail = '';
  subscribing = signal(false);
  subscribed = signal(false);
  subscribeError = signal<string | null>(null);

  onSubscribe(event: Event) {
    event.preventDefault();
    if (!this.newsletterEmail || !this.newsletterEmail.includes('@')) {
      this.subscribeError.set('Please enter a valid email address.');
      return;
    }

    this.subscribing.set(true);
    this.subscribeError.set(null);

    this.marketingSvc.subscribeNewsletter(this.newsletterEmail.trim()).subscribe({
      next: (ok) => {
        this.subscribing.set(false);
        this.subscribed.set(true);
        this.newsletterEmail = '';
      },
      error: () => {
        this.subscribing.set(false);
        this.subscribeError.set('Subscription failed. Please try again.');
      }
    });
  }
  
  readonly currentYear = new Date().getFullYear();

  scrollToTop() {
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
      document.documentElement.scrollTop = 0;
      document.body.scrollTop = 0;
    }
  }

  readonly COLUMNS: FooterColumn[] = [
    {
      heading: "Get to Know Us",
      links: [
        { label: "About", to: "/about" },
        { label: "Insights", to: "/insights" },
        { label: "Careers", to: "/careers" },
      ],
    },
    {
      heading: "Shop",
      links: [
        { label: "All Products", to: "/products" },
        { label: "Deals", to: "/products" },
        { label: "New Arrivals", to: "/products" },
        { label: "Best Sellers", to: "/products" },
        { label: "Categories", to: "/products" },
      ],
    },
    {
      heading: "Customer",
      links: [
        { label: "My Account", to: "/account" },
        { label: "Orders", to: "/account/orders" },
        { label: "Shipping", to: "/support/shipping" },
        { label: "Returns", to: "/support/returns" },
      ],
    },
    {
      heading: "Business",
      links: [
        { label: "Wholesale", to: "/distributor", queryParams: { tab: 'wholesaler' } },
        { label: "Become a Partner", to: "/distributor", queryParams: { tab: 'distributor' } },
        { label: "Become an Affiliate", to: "/distributor", queryParams: { tab: 'affiliate' } },
        { label: "Request a Quote", to: "/quote" },
      ],
    },
    {
      heading: "Support",
      links: [
        { label: "Contact Us", to: "/contact" },
        { label: "WhatsApp", href: "https://wa.me/2348073467809" },
        { label: "FAQs", to: "/support/faqs" },
        { label: "Help Center", to: "/support/help" },
      ],
    },
  ];
}

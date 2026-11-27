import { Component, signal, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CmsService } from 'shared-ui';

interface BlogPostSummary {
  id: string;
  slug: string;
  title: string;
  excerpt: string;
  category: string;
  author: string;
  publishedAt: string;
  coverImage?: string;
  readingTime?: string;
}

@Component({
  selector: 'app-blog-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="max-w-6xl mx-auto px-4 py-6 md:py-8">
      <div class="mb-10 text-center">
        <span class="text-xs font-bold tracking-widest text-blue-600 uppercase bg-blue-50 px-3 py-1 rounded-full">
          Nigson Insights & Industry News
        </span>
        <h1 class="text-4xl font-extrabold text-gray-900 mt-4 tracking-tight">
          Nigeria's Premier Technology & Commerce Blog
        </h1>
        <p class="text-gray-600 mt-3 max-w-2xl mx-auto text-base">
          Expert articles, industry updates, product maintenance guides, and commercial technology trends across Nigeria.
        </p>
      </div>

      <div *ngIf="loading()" class="py-16 text-center text-gray-500">
        <div class="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-3"></div>
        Loading latest articles...
      </div>

      <div *ngIf="!loading() && posts().length === 0" class="text-center py-12 bg-gray-50 rounded-2xl border border-gray-200">
        <p class="text-gray-600 font-medium">No published articles found at this time.</p>
      </div>

      <div *ngIf="!loading() && posts().length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
        <article *ngFor="let post of posts()" class="bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm hover:shadow-md transition flex flex-col">
          <div class="h-48 bg-gradient-to-tr from-slate-800 to-blue-900 flex items-center justify-center p-6 text-white text-center">
            <span class="text-xs font-semibold tracking-wider uppercase px-2.5 py-1 bg-white/20 rounded-md backdrop-blur-sm">
              {{ post.category }}
            </span>
          </div>
          <div class="p-6 flex-1 flex flex-col justify-between">
            <div>
              <div class="text-xs text-gray-500 mb-2 flex items-center gap-2">
                <span>{{ post.publishedAt | date:'mediumDate' }}</span>
                <span>&bull;</span>
                <span>{{ post.readingTime || '4 min read' }}</span>
              </div>
              <h2 class="text-lg font-bold text-gray-900 hover:text-blue-600 transition mb-2">
                <a [routerLink]="['/blog', post.slug]">{{ post.title }}</a>
              </h2>
              <p class="text-sm text-gray-600 line-clamp-3 mb-4">
                {{ post.excerpt }}
              </p>
            </div>
            <div class="pt-4 border-t border-gray-100 flex items-center justify-between">
              <span class="text-xs font-medium text-gray-700">By {{ post.author }}</span>
              <a [routerLink]="['/blog', post.slug]" class="text-sm font-semibold text-blue-600 hover:text-blue-800 inline-flex items-center gap-1">
                Read article &rarr;
              </a>
            </div>
          </div>
        </article>
      </div>
    </div>
  `
})
export class BlogListComponent implements OnInit {
  private http = inject(HttpClient);

  loading = signal(true);
  posts = signal<BlogPostSummary[]>([]);

  ngOnInit() {
    this.http.get<any>('/api/v1/content?type=BLOG').subscribe({
      next: (res) => {
        this.loading.set(false);
        const items = res?.items || res?.content || (Array.isArray(res) ? res : []);
        if (items.length > 0) {
          this.posts.set(items.map((i: any) => ({
            id: i.id,
            slug: i.slug || i.id,
            title: i.title || i.name || 'Insight Article',
            excerpt: i.excerpt || i.summary || i.description || '',
            category: i.category || 'Technology',
            author: i.author || 'Nigson Editorial Team',
            publishedAt: i.publishedAt || i.createdAt || new Date().toISOString(),
            readingTime: i.readingTime || '3 min read'
          })));
        } else {
          // Default fallback posts if none published yet in backend
          this.posts.set([
            {
              id: 'art-01',
              slug: 'power-protection-lagos-commercial-facilities',
              title: 'Industrial Power Protection & Surge Suppression for Nigerian Enterprises',
              excerpt: 'How leading manufacturers and commercial complexes in Ikeja and Lekki protect critical infrastructure against voltage fluctuations.',
              category: 'Commercial Solutions',
              author: 'Engr. Dapo Alabi',
              publishedAt: '2026-10-15T09:00:00Z',
              readingTime: '5 min read'
            },
            {
              id: 'art-02',
              slug: 'optimizing-warehouse-logistics-nigeria',
              title: 'Supply Chain Resilience: Fast Nationwide Delivery Across All 36 States',
              excerpt: 'An inside look at Nigson Logistics delivery integration to guarantee reliable order fulfillment from Lagos to Kano and Port Harcourt.',
              category: 'Logistics',
              author: 'Amaka Eze',
              publishedAt: '2026-10-10T11:30:00Z',
              readingTime: '4 min read'
            },
            {
              id: 'art-03',
              slug: 'choosing-genuine-electrical-components-nigeria',
              title: 'Identifying Genuine Electrical & Power Components in Nigeria',
              excerpt: 'Critical tips for project managers and procurement officers to verify genuine certifications, SONCAP approvals, and standard compliance.',
              category: 'Guide',
              author: 'Ibrahim Musa',
              publishedAt: '2026-10-02T14:15:00Z',
              readingTime: '6 min read'
            }
          ]);
        }
      },
      error: () => {
        this.loading.set(false);
        // Fallback default posts
        this.posts.set([
          {
            id: 'art-01',
            slug: 'power-protection-lagos-commercial-facilities',
            title: 'Industrial Power Protection & Surge Suppression for Nigerian Enterprises',
            excerpt: 'How leading manufacturers and commercial complexes in Ikeja and Lekki protect critical infrastructure against voltage fluctuations.',
            category: 'Commercial Solutions',
            author: 'Engr. Dapo Alabi',
            publishedAt: '2026-10-15T09:00:00Z',
            readingTime: '5 min read'
          },
          {
            id: 'art-02',
            slug: 'optimizing-warehouse-logistics-nigeria',
            title: 'Supply Chain Resilience: Fast Nationwide Delivery Across All 36 States',
            excerpt: 'An inside look at Nigson Logistics delivery integration to guarantee reliable order fulfillment from Lagos to Kano and Port Harcourt.',
            category: 'Logistics',
            author: 'Amaka Eze',
            publishedAt: '2026-10-10T11:30:00Z',
            readingTime: '4 min read'
          }
        ]);
      }
    });
  }
}

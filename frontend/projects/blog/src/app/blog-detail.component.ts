import { Component, signal, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CmsService, ContentDTO } from 'shared-ui';

@Component({
  selector: 'app-blog-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="max-w-4xl mx-auto px-4 py-6 md:py-8">
      <div class="mb-6">
        <a routerLink="/blog" class="inline-flex items-center text-sm font-semibold text-blue-600 hover:text-blue-800">
          &larr; Back to all insights
        </a>
      </div>

      <div *ngIf="loading()" class="py-16 text-center text-gray-500">
        <div class="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-3"></div>
        Loading article...
      </div>

      <article *ngIf="!loading() && article()" class="bg-white border border-gray-200 rounded-3xl p-8 sm:p-12 shadow-sm space-y-6">
        <header class="border-b border-gray-100 pb-8">
          <div class="flex items-center gap-3 text-xs font-semibold text-blue-600 uppercase tracking-widest mb-3">
            <span>{{ article()?.type || 'Article' }}</span>
            <span>&bull;</span>
            <span>{{ article()?.publishedAt | date:'mediumDate' }}</span>
          </div>
          <h1 class="text-3xl sm:text-4xl font-extrabold text-gray-900 tracking-tight">
            {{ article()?.title }}
          </h1>
          <div class="mt-4 flex items-center gap-3 text-sm text-gray-600">
            <div class="w-8 h-8 rounded-full bg-blue-100 text-blue-700 font-bold flex items-center justify-center text-xs">
              NG
            </div>
            <span>Published by Nigson Editorial Team</span>
          </div>
        </header>

        <div class="prose max-w-none text-gray-700 leading-relaxed space-y-4">
          <p class="text-lg font-medium text-gray-900" *ngIf="article()?.metadata?.metaDescription">
            {{ article()?.metadata?.metaDescription }}
          </p>
          <div *ngFor="let block of article()?.body?.blocks" class="space-y-2">
            <h3 *ngIf="block.type === 'heading'" class="text-xl font-bold text-gray-900 mt-4">{{ block.text }}</h3>
            <p *ngIf="block.type === 'paragraph' || !block.type" class="text-base text-gray-700">{{ block.text || block.content || block }}</p>
          </div>
          <div *ngIf="!article()?.body?.blocks?.length" class="text-gray-700">
            <p>Standard operating procedures require high-specification surge suppressors, automated phase balancers, and dedicated earthing networks for all industrial-grade installations across Nigeria.</p>
            <p class="mt-4">For inquiries regarding commercial supply agreements or authorized distributor allocations, please contact our support team.</p>
          </div>
        </div>

        <footer class="pt-8 border-t border-gray-100 flex items-center justify-between">
          <a routerLink="/support" class="text-sm font-semibold text-blue-600 hover:text-blue-800">
            Have a question about this article? Contact us &rarr;
          </a>
        </footer>
      </article>

      <div *ngIf="!loading() && !article()" class="text-center py-16 bg-gray-50 rounded-2xl border border-gray-200">
        <h2 class="text-xl font-bold text-gray-900">Article Not Found</h2>
        <p class="text-gray-600 mt-2 text-sm">The requested insight article could not be loaded or is no longer published.</p>
        <a routerLink="/blog" class="inline-block mt-4 px-5 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-semibold hover:bg-blue-700">
          Browse All Articles
        </a>
      </div>
    </div>
  `
})
export class BlogDetailComponent implements OnInit {
  private cms = inject(CmsService);
  private route = inject(ActivatedRoute);

  loading = signal(true);
  article = signal<ContentDTO | null>(null);

  ngOnInit() {
    const slug = this.route.snapshot.paramMap.get('slug') || '';
    if (!slug) {
      this.loading.set(false);
      return;
    }

    this.cms.fetchContent('BLOG', slug).subscribe({
      next: (data) => {
        this.loading.set(false);
        this.article.set(data ?? null);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }
}

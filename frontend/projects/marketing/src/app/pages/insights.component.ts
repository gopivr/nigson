import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { CmsService, PageHeaderComponent } from 'shared-ui';
import { LucideArrowRight } from '@lucide/angular';
import { BlogPost, POSTS, getAllPublishedPosts, parseMarkdownToSections } from './blog-posts';

@Component({
  selector: 'app-insights',
  standalone: true,
  imports: [CommonModule, RouterLink, PageHeaderComponent, LucideArrowRight],
  template: `
    <div class="bg-surface">
      <ng-container *ngIf="hero().visible">
        <lib-page-header [eyebrow]="hero().t('eyebrow')" [title]="hero().t('heading')" [subtitle]="hero().t('body')"></lib-page-header>
      </ng-container>

      <section *ngIf="articles().visible" class="container-page py-16">
        <p *ngIf="articles().t('intro')" class="mb-10 max-w-2xl text-muted-foreground">{{ articles().t('intro') }}</p>
        
        <div class="grid gap-8 md:grid-cols-2 lg:grid-cols-3">
          <a
            *ngFor="let p of posts()"
            [routerLink]="['/insights', p.slug]"
            class="group rounded-2xl border border-border bg-background overflow-hidden hover:shadow-elegant hover:-translate-y-1 transition-all flex flex-col"
          >
            <div class="aspect-[16/10] overflow-hidden bg-surface-alt">
              <img
                [src]="p.img"
                [alt]="p.title"
                loading="lazy"
                class="h-full w-full object-cover transition-transform duration-[1200ms] group-hover:scale-105"
              />
            </div>
            <div class="p-6 flex flex-col flex-1">
              <div class="flex items-center gap-3 text-xs text-muted-foreground">
                <span class="rounded-full bg-surface-alt px-2.5 py-0.5 font-medium text-brand uppercase tracking-widest">{{ p.tag }}</span>
                <span>{{ p.date }}</span>
              </div>
              <h3 class="mt-3 text-lg font-semibold group-hover:text-brand transition-colors">{{ p.title }}</h3>
              <p class="mt-2 text-sm text-muted-foreground flex-1">{{ p.excerpt }}</p>
              <span class="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-brand">
                {{ articles().t('readLabel') }} <svg lucideArrowRight class="h-4 w-4 transition-transform group-hover:translate-x-1"></svg>
              </span>
            </div>
          </a>
        </div>
      </section>

      <section *ngIf="cta().visible" class="container-page pb-24">
        <div class="rounded-3xl bg-brand text-white p-10 md:p-14">
          <h2 class="text-3xl md:text-4xl font-extrabold tracking-[-0.02em] max-w-2xl">{{ cta().t('heading') }}</h2>
          <p class="mt-4 max-w-xl text-white/85">{{ cta().t('body') }}</p>
          <a
            routerLink="/quote"
            class="mt-8 inline-flex items-center gap-2 rounded-full bg-white px-7 py-3.5 text-sm font-semibold text-brand hover:bg-white/95 transition-colors"
          >
            {{ cta().t('btnLabel') }} <svg lucideArrowRight class="h-4 w-4"></svg>
          </a>
        </div>
      </section>
    </div>
  `
})
export class InsightsComponent implements OnInit {
  cms = inject(CmsService);
  private http = inject(HttpClient);
  
  page = this.cms.getPageSections('insights');

  hero = computed(() => this.page.get('hero'));
  articles = computed(() => this.page.get('articles'));
  cta = computed(() => this.page.get('cta'));

  posts = signal<BlogPost[]>(getAllPublishedPosts());

  ngOnInit() {
    this.loadPosts();
  }

  private loadPosts() {
    const list: BlogPost[] = getAllPublishedPosts();
    this.posts.set(list);

    this.http.get<any>('/api/v1/content?type=BLOG&locale=en&page=0&size=100').pipe(
      catchError(() => of(null))
    ).subscribe(page => {
      const mapped: BlogPost[] = (page?.content ?? page?.items ?? []).map((item: any) => {
        const blocks = item.body?.blocks ?? [];
        const body = blocks.filter((block: any) => block.type === 'TEXT').map((block: any) => block.text).filter(Boolean);
        return { slug: item.slug, title: item.title, date: item.publishedAt || '', tag: item.metadata?.structuredData?.tag || 'Insight', author: item.metadata?.structuredData?.author || '', readMinutes: 3, excerpt: item.metadata?.structuredData?.excerpt || body.join(' ').slice(0, 220), img: '/assets/blog-market.jpg', body, sections: [] };
      });
      this.posts.set(mapped);
    });
  }
}

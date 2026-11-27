import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  LucideArrowLeft,
  LucideArrowRight,
  LucideClock,
  LucideMessageCircle,
  LucideUser
} from '@lucide/angular';
import { BlogPost, POSTS, getAllPublishedPosts, parseMarkdownToSections } from './blog-posts';

@Component({
  selector: 'app-insight-post',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    LucideArrowLeft, LucideArrowRight, LucideClock,
    LucideMessageCircle, LucideUser
  ],
  template: `
    <div class="bg-surface min-h-screen">
      <ng-container *ngIf="currentPost(); else notFound">
        <article class="pt-6 md:pt-8">
          <div class="container-page max-w-3xl">
            <a routerLink="/insights" class="inline-flex items-center gap-2 text-sm font-semibold text-muted-ink hover:text-brand transition-colors">
              <svg lucideArrowLeft class="h-4 w-4"></svg> Back to Insights
            </a>
            
            <div class="mt-8 flex flex-wrap items-center gap-3 text-xs">
              <span class="rounded-full bg-brand/10 px-3 py-1 font-semibold text-brand uppercase tracking-widest">{{ currentPost()?.tag }}</span>
              <span class="text-muted-ink">{{ currentPost()?.date }}</span>
            </div>
            
            <h1 class="mt-6 text-3xl sm:text-4xl md:text-6xl font-extrabold leading-[1.05] tracking-[-0.02em] text-balance">
              {{ currentPost()?.title }}
            </h1>
            
            <p class="mt-6 text-lg md:text-xl text-muted-ink leading-relaxed">{{ currentPost()?.excerpt }}</p>
            
            <div class="mt-6 flex flex-wrap items-center gap-5 text-sm text-muted-ink">
              <span class="inline-flex items-center gap-2"><svg lucideUser class="h-4 w-4 text-brand"></svg> {{ currentPost()?.author }}</span>
              <span class="inline-flex items-center gap-2"><svg lucideClock class="h-4 w-4 text-brand"></svg> {{ currentPost()?.readMinutes }} min read</span>
            </div>
          </div>

          <div class="container-page max-w-5xl mt-12">
            <div class="aspect-[16/9] overflow-hidden rounded-3xl">
              <img [src]="currentPost()?.img" [alt]="currentPost()?.title" class="h-full w-full object-cover" />
            </div>
          </div>

          <div class="container-page max-w-3xl py-16 space-y-10">
            <ng-container *ngIf="currentPost()?.sections?.length; else fallbackBody">
              <section *ngFor="let s of currentPost()?.sections" class="space-y-4">
                <h2 *ngIf="s.heading" class="text-2xl md:text-3xl font-extrabold tracking-[-0.01em]">{{ s.heading }}</h2>
                <p *ngFor="let para of s.paragraphs" class="text-lg text-ink/85 leading-relaxed">{{ para }}</p>
                
                <ul *ngIf="s.bullets" class="space-y-3">
                  <li *ngFor="let b of s.bullets" class="flex gap-3 text-lg text-ink/85 leading-relaxed">
                    <span class="mt-2.5 h-1.5 w-1.5 shrink-0 rounded-full bg-brand"></span>
                    <span>{{ b }}</span>
                  </li>
                </ul>
              </section>
            </ng-container>
            
            <ng-template #fallbackBody>
              <p *ngFor="let para of currentPost()?.body" class="text-lg text-ink/85 leading-relaxed">{{ para }}</p>
            </ng-template>
          </div>

          <div class="container-page max-w-3xl pb-24">
            <div class="rounded-2xl bg-surface-alt border border-hairline p-8 flex flex-wrap items-center justify-between gap-4">
              <div>
                <p class="text-sm text-muted-ink">Have questions about this article?</p>
                <p class="text-lg font-extrabold">Talk to our team.</p>
              </div>
              <a
                href="https://wa.me/2348073467809"
                target="_blank"
                rel="noopener noreferrer"
                class="inline-flex items-center gap-2 rounded-full bg-brand text-white px-6 py-3 text-sm font-semibold hover:bg-brand-deep transition-colors"
              >
                <svg lucideMessageCircle class="h-4 w-4"></svg> WhatsApp Sales
              </a>
            </div>
          </div>

          <section class="border-t border-hairline bg-surface-alt py-20">
            <div class="container-page">
              <h2 class="text-3xl font-extrabold mb-10">More from Nigson</h2>
              <div class="grid gap-6 md:grid-cols-3">
                <a
                  *ngFor="let r of relatedPosts()"
                  [routerLink]="['/insights', r.slug]"
                  class="group rounded-2xl bg-white border border-hairline overflow-hidden hover:-translate-y-1 hover:border-brand transition-all flex flex-col"
                >
                  <div class="aspect-[16/10] overflow-hidden">
                    <img [src]="r.img" [alt]="r.title" loading="lazy" class="h-full w-full object-cover transition-transform duration-700 group-hover:scale-105" />
                  </div>
                  <div class="p-5 flex flex-col flex-1">
                    <div class="flex items-center gap-3 text-[10px] text-muted-ink">
                      <span class="font-semibold uppercase tracking-widest text-brand">{{ r.tag }}</span>
                      <span>{{ r.date }}</span>
                    </div>
                    <h3 class="mt-2 font-extrabold group-hover:text-brand transition-colors">{{ r.title }}</h3>
                    <p class="mt-2 text-sm text-muted-ink line-clamp-2 flex-1">{{ r.excerpt }}</p>
                    <span class="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-brand">
                      Read article <svg lucideArrowRight class="h-4 w-4 transition-transform group-hover:translate-x-1"></svg>
                    </span>
                  </div>
                </a>
              </div>
            </div>
          </section>
        </article>
      </ng-container>

      <ng-template #notFound>
        <div class="container-page py-32 text-center">
          <h1 class="text-4xl font-extrabold">Article not found</h1>
          <a routerLink="/insights" class="mt-6 inline-flex items-center gap-2 text-brand font-semibold">
            <svg lucideArrowLeft class="h-4 w-4"></svg> Back to Insights
          </a>
        </div>
      </ng-template>
    </div>
  `
})
export class InsightPostComponent implements OnInit {
  route = inject(ActivatedRoute);
  private http = inject(HttpClient);

  slug = toSignal(this.route.paramMap.pipe(map(params => params.get('slug'))));
  posts = signal<BlogPost[]>(getAllPublishedPosts());

  currentPost = computed(() => {
    const s = this.slug();
    if (!s) return undefined;
    const decoded = decodeURIComponent(s);
    return this.posts().find(item => item.slug === s || item.slug === decoded);
  });

  relatedPosts = computed(() => {
    const p = this.currentPost();
    if (!p) return [];
    return this.posts().filter(x => x.slug !== p.slug).slice(0, 3);
  });

  ngOnInit() {
    const s = this.slug();
    if (s) {
      this.http.get<any>(`/api/v1/content/BLOG/${encodeURIComponent(s)}?locale=en`).pipe(
        catchError(() => of(null))
      ).subscribe(item => {
        if (!item || !item.slug) return;
        const blocks = item.body?.blocks ?? [];
        const body = blocks.filter((block: any) => block.type === 'TEXT').map((block: any) => block.text).filter(Boolean);
        const postItem: BlogPost = {
          slug: item.slug,
          title: item.title,
          date: item.publishedAt ? new Date(item.publishedAt).toLocaleDateString('en-US', { month: 'short', year: 'numeric' }) : '',
          tag: item.metadata?.tag || item.metadata?.structuredData?.tag || 'Insight',
          author: item.metadata?.author || item.metadata?.structuredData?.author || 'Nigson Team',
          readMinutes: 3,
          excerpt: item.metadata?.description || item.metadata?.excerpt || body.join(' ').slice(0, 220),
          img: item.metadata?.image || item.metadata?.structuredData?.image || '/assets/blog-market.jpg',
          body,
          sections: parseMarkdownToSections(body.join('\n\n'))
        };
        const list = [...this.posts()];
        const idx = list.findIndex(p => p.slug === item.slug);
        if (idx >= 0) list[idx] = postItem;
        else list.push(postItem);
        this.posts.set(list);
      });
    }

    this.http.get<any>('/api/v1/content?type=BLOG&locale=en&page=0&size=100').pipe(
      catchError(() => of(null))
    ).subscribe(page => {
      const mapped: BlogPost[] = (page?.content || page?.items || []).map((item: any) => {
        const body = (item.body?.blocks ?? []).filter((block: any) => block.type === 'TEXT').map((block: any) => block.text).filter(Boolean);
        return {
          slug: item.slug,
          title: item.title,
          date: item.publishedAt ? new Date(item.publishedAt).toLocaleDateString('en-US', { month: 'short', year: 'numeric' }) : '',
          tag: item.metadata?.tag || item.metadata?.structuredData?.tag || 'Insight',
          author: item.metadata?.author || item.metadata?.structuredData?.author || 'Nigson Team',
          readMinutes: 3,
          excerpt: item.metadata?.description || item.metadata?.excerpt || body.join(' ').slice(0, 220),
          img: item.metadata?.image || item.metadata?.structuredData?.image || '/assets/blog-market.jpg',
          body,
          sections: parseMarkdownToSections(body.join('\n\n'))
        };
      });
      const list: BlogPost[] = [...this.posts()];
      for (const m of mapped) {
        if (m.slug === 'insights') continue;
        const idx = list.findIndex(p => p.slug === m.slug);
        if (idx >= 0) list[idx] = m;
        else list.push(m);
      }
      this.posts.set(list);
    });
  }
}

import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CmsService } from 'shared-ui';
import { PageHeaderComponent } from 'shared-ui';
import { LucideArrowRight } from '@lucide/angular';

@Component({
  selector: 'app-about',
  standalone: true,
  imports: [CommonModule, RouterLink, PageHeaderComponent, LucideArrowRight],
  template: `
    <div class="bg-surface">
      <!-- We render sections in the order defined by the CMS -->
      <ng-container *ngFor="let blockId of order()">
        
        <!-- HERO -->
        <ng-container *ngIf="blockId === 'hero' && hero().visible">
          <lib-page-header [eyebrow]="hero().t('eyebrow')" [title]="hero().t('heading')" [subtitle]="hero().t('body')"></lib-page-header>
        </ng-container>

        <!-- STORY -->
        <section *ngIf="blockId === 'story' && story().visible" class="container-page py-20 grid gap-12 lg:grid-cols-3">
          <div class="lg:col-span-2 space-y-6 text-lg leading-relaxed text-muted-foreground">
            <p *ngIf="story().t('p1')">{{ story().t('p1') }}</p>
            <p *ngIf="story().t('p2')">{{ story().t('p2') }}</p>
            <p *ngIf="story().t('p3')">{{ story().t('p3') }}</p>
          </div>
          <aside class="rounded-2xl bg-ink text-background p-8 shadow-elegant self-start">
            <p class="text-xs uppercase tracking-widest text-brand mb-3">{{ story().t('smartEyebrow') }}</p>
            <p class="font-display text-5xl">{{ story().t('smartTitle') }}</p>
            <ul class="mt-6 space-y-2 text-sm text-background/80">
              <li *ngFor="let c of storyCards()">
                <b class="text-brand">{{ c.letter }}</b> <ng-container *ngIf="c.rest"> &mdash; {{ c.rest }}</ng-container>
              </li>
            </ul>
          </aside>
        </section>

        <!-- MISSION -->
        <section *ngIf="blockId === 'mission' && mission().visible" class="border-y border-border bg-surface-alt">
          <div class="container-page py-20 grid gap-10 md:grid-cols-3">
            <div *ngFor="let c of missionCards()" class="rounded-2xl bg-background border border-border p-8">
              <p class="text-xs font-semibold uppercase tracking-[0.2em] text-brand">{{ c.title }}</p>
              <p class="mt-4 text-muted-foreground leading-relaxed">{{ c.body }}</p>
            </div>
          </div>
        </section>

        <!-- JOURNEY -->
        <section *ngIf="blockId === 'journey' && journey().visible" class="container-page py-24">
          <div class="max-w-2xl mb-16 reveal-visible">
            <p class="text-xs font-semibold uppercase tracking-[0.2em] text-brand">{{ journey().t('eyebrow') }}</p>
            <h2 class="mt-3 text-4xl md:text-5xl font-extrabold tracking-[-0.02em]">{{ journey().t('heading') }}</h2>
          </div>
          <ol class="relative border-l-2 border-hairline pl-10 space-y-14 max-w-3xl">
            <li *ngFor="let m of journeyCards(); let i = index" class="reveal-visible relative group">
              <span class="absolute -left-[49px] top-1 grid h-7 w-7 place-items-center rounded-full bg-white border-2 border-brand group-hover:bg-brand transition-colors duration-500 animate-soft-pulse">
                <span class="h-2 w-2 rounded-full bg-brand group-hover:bg-white transition-colors"></span>
              </span>
              <p class="text-4xl md:text-5xl font-extrabold text-brand tracking-tight">{{ m.year }}</p>
              <h3 class="mt-2 text-xl font-extrabold text-ink">{{ m.title }}</h3>
              <p class="mt-2 text-muted-foreground leading-relaxed">{{ m.body }}</p>
            </li>
          </ol>
        </section>

        <!-- VALUES -->
        <section *ngIf="blockId === 'values' && values().visible" class="container-page py-24">
          <div class="max-w-2xl mb-12">
            <p class="text-xs font-semibold uppercase tracking-[0.2em] text-brand">{{ values().t('eyebrow') }}</p>
            <h2 class="mt-3 text-4xl font-semibold">{{ values().t('heading') }}</h2>
          </div>
          <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            <div *ngFor="let c of valuesCards()" class="rounded-xl border border-border p-6 hover:border-brand transition-colors">
              <h3 class="text-lg font-semibold">{{ c.title }}</h3>
              <p class="mt-2 text-sm text-muted-foreground">{{ c.body }}</p>
            </div>
          </div>
        </section>

        <!-- LEADERSHIP -->
        <ng-container *ngIf="blockId === 'leadership' && leadership().visible">
          <ng-container *ngTemplateOutlet="teamSection; context: { sec: leadership, cards: leadershipCards() }"></ng-container>
        </ng-container>

        <!-- MANAGEMENT -->
        <ng-container *ngIf="blockId === 'management' && management().visible">
          <ng-container *ngTemplateOutlet="teamSection; context: { sec: management, cards: managementCards() }"></ng-container>
        </ng-container>

        <!-- ADVISORY -->
        <ng-container *ngIf="blockId === 'advisory' && advisory().visible">
          <ng-container *ngTemplateOutlet="teamSection; context: { sec: advisory, cards: advisoryCards() }"></ng-container>
        </ng-container>

        <!-- CAREERS CTA -->
        <section *ngIf="blockId === 'careersCta' && careersCta().visible" class="container-page pb-24 pt-8">
          <div class="relative overflow-hidden rounded-3xl bg-brand text-white shadow-[0_40px_100px_-30px_rgba(215,25,32,0.55)]">
            <div aria-hidden="true" class="absolute -top-24 -right-24 h-96 w-96 rounded-full bg-white/10 blur-3xl"></div>
            <div aria-hidden="true" class="absolute -bottom-32 -left-24 h-96 w-96 rounded-full bg-brand-deep/40 blur-3xl"></div>
            <div class="relative grid gap-10 p-10 md:p-16 lg:grid-cols-[1.2fr_1fr] lg:items-center">
              <div>
                <p class="text-[11px] font-semibold uppercase tracking-[0.25em] text-white/80">{{ careersCta().t("eyebrow") }}</p>
                <h2 class="mt-5 text-4xl md:text-6xl font-extrabold leading-[1.02] tracking-[-0.02em]">{{ careersCta().t("heading") }}</h2>
                <p class="mt-6 max-w-xl text-lg text-white/85 leading-relaxed">{{ careersCta().t("body") }}</p>
                <a
                  routerLink="/careers"
                  class="group mt-10 inline-flex items-center gap-3 rounded-full bg-white px-8 py-4 text-sm font-semibold text-brand hover:bg-white/95 transition-all"
                >
                  {{ careersCta().t("btnLabel") }}
                  <svg lucideArrowRight class="h-4 w-4 transition-transform group-hover:translate-x-1"></svg>
                </a>
              </div>
              <ul class="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
                <li *ngFor="let c of careersCtaCards()" class="rounded-2xl bg-white/10 backdrop-blur-sm border border-white/15 px-5 py-4">
                  <p class="text-sm font-extrabold">{{ c.title }}</p>
                  <p class="mt-1 text-xs text-white/75">{{ c.body }}</p>
                </li>
              </ul>
            </div>
          </div>
        </section>

      </ng-container>

      <ng-template #teamSection let-sec="sec" let-cards="cards">
        <section class="border-t border-border bg-surface-alt">
          <div class="container-page py-24">
            <div class="max-w-2xl mb-12">
              <p class="text-xs font-semibold uppercase tracking-[0.2em] text-brand">{{ sec().t('eyebrow') }}</p>
              <h2 class="mt-3 text-4xl font-semibold">{{ sec().t('heading') }}</h2>
              <p *ngIf="sec().t('body')" class="mt-4 text-muted-foreground">{{ sec().t('body') }}</p>
            </div>
            <div class="grid gap-8 md:grid-cols-3">
              <article *ngFor="let m of cards" class="rounded-2xl bg-background border border-border overflow-hidden shadow-elegant">
                <div class="aspect-[4/5] bg-gradient-brand grid place-items-center overflow-hidden">
                  <ng-container *ngIf="m.image; else noImage">
                    <img [src]="m.image" [alt]="m.name" class="h-full w-full object-cover" />
                  </ng-container>
                  <ng-template #noImage>
                    <span class="font-display text-7xl text-background/90">
                      {{ getInitials(m.name) }}
                    </span>
                  </ng-template>
                </div>
                <div class="p-6">
                  <h3 class="text-xl font-semibold">{{ m.name }}</h3>
                  <p class="text-sm text-brand font-medium">{{ m.role }}</p>
                  <p *ngIf="m.bio" class="mt-3 text-sm text-muted-foreground leading-relaxed">{{ m.bio }}</p>
                </div>
              </article>
            </div>
          </div>
        </section>
      </ng-template>

    </div>
  `
})
export class AboutComponent {
  cms = inject(CmsService);
  page = this.cms.getPageSections('about');
  order = this.page.order;

  hero = computed(() => this.page.get('hero'));
  story = computed(() => this.page.get('story'));
  mission = computed(() => this.page.get('mission'));
  journey = computed(() => this.page.get('journey'));
  values = computed(() => this.page.get('values'));
  leadership = computed(() => this.page.get('leadership'));
  management = computed(() => this.page.get('management'));
  advisory = computed(() => this.page.get('advisory'));
  careersCta = computed(() => this.page.get('careersCta'));

  storyCards = computed(() => this.story().cards((c) => {
    const parts = (c['title'] ?? '').split('—');
    return { letter: parts[0]?.trim(), rest: parts.slice(1).join('—').trim() };
  }));

  missionCards = computed(() => this.mission().cards((c) => ({ title: c['title'], body: c['body'] })));
  journeyCards = computed(() => this.journey().cards((c) => ({ year: c['year'], title: c['title'], body: c['body'] })));
  valuesCards = computed(() => this.values().cards((c) => ({ title: c['title'], body: c['body'] })));
  
  leadershipCards = computed(() => this.leadership().cards((c) => ({ name: c['name'], role: c['role'], image: c['image'], bio: c['bio'] })));
  managementCards = computed(() => this.management().cards((c) => ({ name: c['name'], role: c['role'], image: c['image'], bio: c['bio'] })));
  advisoryCards = computed(() => this.advisory().cards((c) => ({ name: c['name'], role: c['role'], image: c['image'], bio: c['bio'] })));
  
  careersCtaCards = computed(() => this.careersCta().cards((c) => ({ title: c['title'], body: c['body'] })));

  getInitials(name: string | undefined): string {
    if (!name) return '';
    return name.split(' ').map((s) => s[0]).slice(0, 2).join('');
  }
}

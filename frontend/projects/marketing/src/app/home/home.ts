import { Component, HostListener, OnInit, OnDestroy, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { 
  LucideArrowRight, 
  LucideTruck, 
  LucideShieldCheck, 
  LucideRotateCcw, 
  LucideHeadphones,
  LucideChevronLeft,
  LucideChevronRight
} from '@lucide/angular';
import { CatalogService, ShopService, CmsService, QtyStepperComponent } from 'shared-ui';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule, 
    RouterLink, 
    LucideArrowRight, 
    LucideTruck, 
    LucideShieldCheck, 
    LucideRotateCcw, 
    LucideHeadphones,
    LucideChevronLeft,
    LucideChevronRight,
    QtyStepperComponent
  ],
  templateUrl: './home.html',
})
export class HomeComponent implements OnInit, OnDestroy {
  catalog = inject(CatalogService);
  shop = inject(ShopService);
  cms = inject(CmsService);

  /** CMS sections for the home page (reads published content) */
  private sections = this.cms.getPageSections('home');

  // ── section getters ──────────────────────────────────────────────────────
  linkRoute(value: string | null | undefined, fallback = '/products'): string {
    const target = value?.trim();
    if (!target || target === '#' || target === '#new-arrivals' || target.includes('new-arrivals')) {
      return fallback;
    }
    const hash = target.indexOf('#');
    return hash === 0 ? '/' : hash > 0 ? target.slice(0, hash) : target;
  }

  linkFragment(value: string | null | undefined, fallback = '/products'): string | undefined {
    const target = value?.trim();
    if (!target || target === '#' || target === '#new-arrivals' || target.includes('new-arrivals')) {
      return undefined;
    }
    const hash = target.indexOf('#');
    return hash < 0 ? undefined : target.slice(hash + 1);
  }

  hero             = computed(() => this.sections.get('hero'));
  newArrivals      = computed(() => this.sections.get('newArrivals'));
  categoryShowcase = computed(() => this.sections.get('categoryShowcase'));
  lifestyle        = computed(() => this.sections.get('lifestylePanels'));
  bestSellers      = computed(() => this.sections.get('bestSellers'));
  wholesale        = computed(() => this.sections.get('wholesale'));
  distCta          = computed(() => this.sections.get('distributorCta'));
  benefits         = computed(() => this.sections.get('serviceBenefits'));
  testimonials     = computed(() => this.sections.get('testimonials'));
  seo              = computed(() => this.sections.get('home_seo'));
  carousel         = computed(() => this.sections.get('featuredCarousel'));

  // ── derived card arrays ──────────────────────────────────────────────────
  private readonly categoryImageReplacements: Record<string, { previous: string; current: string }> = {
    'audio': { previous: '/assets/nigson-audio.jpg', current: '/assets/home-category-audio.png' },
    'power': { previous: '/assets/nigson-power.jpg', current: '/assets/home-category-power.png' },
    'charging': { previous: '/assets/nigson-hero-products.jpg', current: '/assets/home-category-charging.png' },
    'mobile accessories': { previous: '/assets/nigson-audio.jpg', current: '/assets/home-category-mobile-accessories.png' },
    'car accessories': { previous: '/assets/nigson-power.jpg', current: '/assets/home-category-car-accessories.png' },
    'fmcg': { previous: '/assets/nigson-wholesale.jpg', current: '/assets/home-category-fmcg.png' },
  };

  // Resolve the old bundled defaults in published CMS cards without replacing custom uploads.
  private categoryImage(label: string, image: string): string {
    const replacement = this.categoryImageReplacements[(label || '').trim().toLowerCase()];
    return replacement && (!image || image === replacement.previous)
      ? replacement.current
      : image || '/assets/nigson-hero-products.jpg';
  }

  showcaseCards = computed(() =>
    this.categoryShowcase().cards((f) => ({
      label: f['label'], title: f['title'], description: f['description'],
      filter: f['filter'], image: this.categoryImage(f['label'], f['image'])
    }))
  );

  lifestyleCards = computed(() =>
    this.lifestyle().cards((f) => ({
      title: f['title'], text: f['text'],
      image: f['image'] || '/assets/nigson-lifestyle-sound.jpg',
      alt: f['alt'] || '',
      btnLabel: f['btnLabel'] || 'SHOP NOW',
      btnLink: f['btnLink'] || '#best-sellers'
    }))
  );

  carouselSlides = computed(() =>
    this.carousel().cards((f) => ({
      kicker: f['kicker'], title: f['title'], desc: f['desc'],
      cta: f['cta'], link: f['link'] || '/products',
      queryParams: f['category'] ? { category: f['category'] } : {},
      tone: f['tone'] || 'dark',
      image: f['image'] || '/assets/nigson-audio.jpg'
    }))
  );

  benefitCards = computed(() =>
    this.benefits().cards((f) => ({ title: f['title'], desc: f['desc'] }))
  );

  testimonialCards = computed(() =>
    this.testimonials().cards((f) => ({ quote: f['quote'], name: f['name'], role: f['role'] }))
  );

  distBenefits = computed(() =>
    this.distCta().cards((f) => ({ title: f['title'] }))
  );

  // ── image assets ─────────────────────────────────────────────────────────
  heroImage      = '/assets/home-hero.png';
  audioImage     = '/assets/nigson-audio.jpg';
  newArrivalsImage = '/assets/home-new-arrivals.png';
  wholesaleImage = '/assets/nigson-wholesale.jpg';
  lifestyleWork  = '/assets/nigson-lifestyle-work.jpg';

  get products() {
    return this.catalog.products().filter((prod: any) => this.catalog.isDeal(prod)).slice(0, 4).map((prod: any) => ({
      name: prod.name,
      detail: prod.description,
      price: this.catalog.formatPrice(prod.priceMinorUnits ?? 0),
      image: this.catalog.imageFor(prod.categoryName ?? ""),
      product: prod
    }));
  }

  // ── carousel state ────────────────────────────────────────────────────────
  get N() { return this.carouselSlides().length || 1; }
  pos = 0;
  drag = 0;
  paused = false;
  intervalId: any;
  startX: number | null = null;
  offsets = [-2, -1, 0, 1, 2];

  mod(n: number) { return ((n % this.N) + this.N) % this.N; }

  ngOnInit() { this.startInterval(); }
  ngOnDestroy() { if (this.intervalId) clearInterval(this.intervalId); }

  startInterval() {
    if (this.intervalId) clearInterval(this.intervalId);
    this.intervalId = setInterval(() => {
      if (!this.paused && this.startX === null) this.pos++;
    }, 5500);
  }

  setPaused(p: boolean) { this.paused = p; }
  go(d: number) { this.pos += d; this.startInterval(); }

  onPointerDown(e: PointerEvent) {
    if ((e.target as Element | null)?.closest('a, button, [role="button"]')) return;
    this.startX = e.clientX;
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  }
  onPointerMove(e: PointerEvent) {
    if (this.startX !== null) this.drag = e.clientX - this.startX;
  }
  onPointerUp(e: PointerEvent) {
    if (this.startX === null) return;
    const w = (e.currentTarget as HTMLElement).offsetWidth || 1000;
    if (Math.abs(this.drag) > w * 0.08) this.go(this.drag < 0 ? 1 : -1);
    this.startX = null;
    this.drag = 0;
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(e: KeyboardEvent) {
    if (e.key === "ArrowLeft") this.go(-1);
    if (e.key === "ArrowRight") this.go(1);
  }
}


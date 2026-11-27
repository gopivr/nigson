import { Component, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { CmsService, PAGES, PageDef, ContentRevisionEdit } from 'shared-ui';
import { LucideSearch, LucideFileText, LucideExternalLink, LucideGlobe, LucideCheckCircle2 } from '@lucide/angular';

@Component({
  selector: 'app-admin-pages',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule, 
    LucideSearch, LucideFileText, LucideExternalLink, LucideGlobe, LucideCheckCircle2
  ],
  template: `
<div *ngIf="cms.error()" role="alert" class="m-4 rounded border border-red-300 bg-red-50 p-4 text-red-800">{{cms.error()}}</div>
    <div class="px-8 py-8 relative">
      <!-- TOAST NOTIFICATION -->
      <div *ngIf="publishSuccessMessage()" class="fixed top-20 right-8 z-50 flex items-center gap-3 rounded-xl bg-emerald-600 px-5 py-3.5 text-white shadow-2xl transition-all">
        <svg lucideCheckCircle2 class="h-5 w-5 shrink-0"></svg>
        <span class="text-sm font-semibold">{{ publishSuccessMessage() }}</span>
      </div>

      <div class="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 class="text-3xl font-black tracking-tight text-ink">Pages</h1>
          <p class="mt-1 text-sm text-muted-ink">Full website CMS &mdash; edit every section of each page, then publish</p>
        </div>
        
        <div class="relative w-full sm:w-[320px]">
          <svg lucideSearch class="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"></svg>
          <input 
            type="text" 
            [(ngModel)]="searchQuery" 
            placeholder="Search pages..." 
            class="w-full rounded-full border border-gray-200 bg-white py-2.5 pl-10 pr-4 text-sm text-ink shadow-sm outline-none transition-all focus:border-gray-300 focus:ring-4 focus:ring-gray-100"
          >
        </div>
      </div>
      
      <div class="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
        <div *ngFor="let p of filteredPages()" class="flex flex-col rounded-[20px] border border-gray-100 bg-white p-6 shadow-sm transition-all hover:shadow-md">
          
          <div class="flex items-start justify-between">
            <div class="flex items-center gap-4">
              <div class="flex h-12 w-12 items-center justify-center rounded-2xl bg-red-50 text-brand">
                <svg lucideFileText class="h-6 w-6"></svg>
              </div>
              <div>
                <h3 class="text-lg font-bold text-ink leading-tight">{{ p.name }}</h3>
                <span class="text-sm text-muted-ink">{{ p.path }}</span>
              </div>
            </div>
            
            <div *ngIf="!isDirty(p.id)" class="flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-[10px] font-bold uppercase tracking-widest text-emerald-600">
              <svg lucideCheckCircle2 class="h-3 w-3"></svg> Published
            </div>
            <div *ngIf="isDirty(p.id) && cms.canPublish()" class="flex items-center gap-1.5 rounded-full bg-amber-50 px-2.5 py-1 text-[10px] font-bold uppercase tracking-widest text-amber-700">
              <span class="h-1.5 w-1.5 rounded-full bg-amber-500 animate-pulse"></span> Draft Changes
            </div>
          </div>
          
          <div class="mt-6 flex-1 space-y-2">
            <p class="text-sm text-ink font-medium">{{ p.sections.length }} sections</p>
            <p class="text-[13px] text-muted-ink">Updated {{ getUpdatedAt(p.id) }}</p>
          </div>
          
          <div class="mt-6 flex flex-wrap items-center gap-2.5">
            <a 
              [routerLink]="getEditorLink(p.id)" 
              class="flex h-9 items-center justify-center rounded-full bg-brand px-5 text-[13px] font-bold text-white transition-colors hover:bg-brand-600"
            >
              Edit Page
            </a>

            <button 
              *ngIf="isDirty(p.id)"
              (click)="publishDirect(p.id)"
              [disabled]="publishingId() === p.id"
              class="flex h-9 items-center justify-center gap-1.5 rounded-full bg-black px-4 text-[13px] font-bold text-white transition-colors hover:bg-gray-800 disabled:opacity-50"
              title="Publish draft changes to live site"
            >
              <svg lucideGlobe class="h-3.5 w-3.5" [class.animate-spin]="publishingId() === p.id"></svg>
              {{ publishingId() === p.id ? 'Publishing...' : 'Publish' }}
            </button>
            
            <a 
              [href]="p.path" 
              target="_blank"
              class="flex h-9 items-center justify-center gap-1.5 rounded-full border border-gray-200 bg-white px-4 text-[13px] font-bold text-ink transition-colors hover:bg-gray-50"
            >
              Live Page <svg lucideExternalLink class="h-3.5 w-3.5"></svg>
            </a>
          </div>
        </div>
      </div>

      <div *ngIf="filteredPages().length === 0" class="flex flex-col items-center justify-center rounded-3xl border border-dashed border-gray-200 bg-gray-50 py-20 text-center">
        <svg lucideFileText class="mb-4 h-10 w-10 text-gray-300"></svg>
        <h3 class="text-lg font-bold text-ink">No pages found</h3>
        <p class="mt-1 text-sm text-muted-ink">We couldn't find any pages matching "{{ searchQuery() }}".</p>
      </div>
    </div>
  `
})
export class AdminPagesComponent {
  cms = inject(CmsService);
  constructor(){this.cms.loadDrafts().subscribe({error:e=>this.cms.error.set(this.cms.message(e))});}
  
  searchQuery = signal('');
  publishingId = signal<string | null>(null);
  publishSuccessMessage = signal<string | null>(null);
  
  pages = computed(() => PAGES);

  filteredPages = computed(() => {
    const q = this.searchQuery().toLowerCase();
    if (!q) return this.pages();
    return this.pages().filter(p => 
      p.name.toLowerCase().includes(q) || 
      p.path.toLowerCase().includes(q)
    );
  });

  isDirty(pageId: string): boolean {
    const pub = this.cms.published()[pageId]?.sections;
    const draft = this.cms.drafts()[pageId]?.sections;
    if (!pub && !draft) return false;
    return JSON.stringify(pub) !== JSON.stringify(draft);
  }

  getUpdatedAt(pageId: string): string {
    const dt = this.cms.published()[pageId]?.updatedAt || this.cms.drafts()[pageId]?.updatedAt;
    if (!dt) return 'Recently';
    try {
      const d = new Date(dt);
      return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    } catch {
      return 'Recently';
    }
  }

  getEditorLink(id: string): string {
    return id === 'home' ? '/admin/pages/home' : '/admin/pages/' + id;
  }

  publishDirect(id:string){this.publishingId.set(id);this.publishSuccessMessage.set(null);this.cms.executePublishPipeline(id).subscribe({next:()=>{this.publishingId.set(null);this.publishSuccessMessage.set('Page published successfully.');},error:()=>this.publishingId.set(null)});}
}

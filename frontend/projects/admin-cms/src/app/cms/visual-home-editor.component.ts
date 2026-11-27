import { Component, computed, inject, signal, OnInit, effect, viewChild, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { CmsService, SessionService, ContentRevisionEdit, PageContent, SectionContent, SectionDef } from 'shared-ui';
import {
  LucidePlus, LucidePencil, LucideGlobe, LucideSave,
  LucideEye, LucideEyeOff, LucideGripVertical, LucideCheckCircle2, LucideChevronRight,
  LucideExternalLink, LucideRefreshCw, LucideHeading, LucideList, LucideArrowLeft
} from '@lucide/angular';
import { FormsModule } from '@angular/forms';
import { AdminBlogPost, parseMarkdownToSections } from './blog.component';

@Component({
  selector: 'app-visual-home-editor',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    LucidePlus, LucidePencil, LucideGlobe, LucideSave,
    LucideEye, LucideEyeOff, LucideGripVertical, LucideCheckCircle2, LucideChevronRight,
    LucideExternalLink, LucideRefreshCw, LucideHeading, LucideList, LucideArrowLeft
  ],
  template: `
<div *ngIf="cms.error()" role="alert" class="m-4 rounded border border-red-300 bg-red-50 p-4 text-red-800">{{cms.error()}}</div>
    <div class="fixed inset-0 z-50 flex flex-col bg-[#f0f2f5] font-sans antialiased select-none">
      
      <!-- TOAST: PUBLISH SUCCESS -->
      <div *ngIf="publishSuccess()" class="fixed top-20 right-6 z-50 flex items-center gap-3 rounded-xl px-4 py-3 text-white shadow-2xl transition-all" [class.bg-emerald-600]="backendSynced()" [class.bg-amber-600]="!backendSynced()">
        <div class="flex h-8 w-8 items-center justify-center rounded-lg" [class.bg-emerald-700]="backendSynced()" [class.bg-amber-700]="!backendSynced()">
          <svg lucideCheckCircle2 class="h-5 w-5"></svg>
        </div>
        <div>
          <p class="text-xs font-bold leading-none">{{ backendSynced() ? 'Published to Database & Live!' : 'Published Locally in Browser' }}</p>
          <p class="text-[11px] text-white/90 mt-0.5" *ngIf="backendSynced()">Edits are saved to PostgreSQL database & live on {{ pageDef()?.name }}.</p>
          <p class="text-[11px] text-white/90 mt-0.5" *ngIf="!backendSynced()">Saved in local browser. Sign in via /auth/login for persistent database sync.</p>
        </div>
        <a [href]="pageDef()?.path" target="_blank" class="ml-2 rounded-md bg-white/20 px-2.5 py-1 text-[11px] font-semibold text-white hover:bg-white/30 transition-colors flex items-center gap-1">
          View Live <svg lucideExternalLink class="h-3 w-3"></svg>
        </a>
      </div>

      <!-- TOAST: SAVE DRAFT SUCCESS -->
      <div *ngIf="saveSuccess()" class="fixed top-20 right-6 z-50 flex items-center gap-3 rounded-xl bg-gray-900 px-4 py-3 text-white shadow-2xl transition-all">
        <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-gray-800">
          <svg lucideSave class="h-4 w-4 text-gray-200"></svg>
        </div>
        <div>
          <p class="text-xs font-bold leading-none">Draft Saved</p>
          <p class="text-[11px] text-gray-300 mt-0.5">Local draft updated. Click Publish to make changes live.</p>
        </div>
      </div>

      <!-- TOP TOOLBAR -->
      <header class="z-30 flex h-16 shrink-0 items-center justify-between border-b border-gray-200 bg-white px-5 shadow-sm">
        <div class="flex items-center gap-4">
          <a routerLink="/admin/pages" class="flex h-8 w-8 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-100 hover:text-gray-900 transition-colors">
            &larr;
          </a>
          <div class="h-6 w-px bg-gray-200"></div>
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-[15px] font-semibold text-gray-900">{{ pageDef()?.name || 'Page' }}</h1>
              <span *ngIf="!isDirty()" class="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-0.5 text-[10px] font-bold text-emerald-800">
                <svg lucideCheckCircle2 class="h-3 w-3"></svg> PUBLISHED
              </span>
              <span *ngIf="isDirty()" class="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-0.5 text-[10px] font-bold text-amber-800">
                <span class="h-1.5 w-1.5 rounded-full bg-amber-500 animate-pulse"></span> UNPUBLISHED DRAFT
              </span>
            </div>
            <a [href]="pageDef()?.path" target="_blank" class="flex items-center gap-1 text-[11px] text-gray-500 hover:text-blue-600 transition-colors">
              nigson.com{{ pageDef()?.path }} <svg lucideExternalLink class="h-3 w-3"></svg>
            </a>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <button (click)="reloadPreview()" title="Reload canvas preview" class="flex h-8 w-8 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-100 hover:text-gray-900 transition-colors">
            <svg lucideRefreshCw class="h-4 w-4"></svg>
          </button>

          <div class="flex items-center rounded-lg bg-gray-100 p-1">
            <button (click)="previewMode.set(false)" [class.bg-white]="!previewMode()" [class.shadow-sm]="!previewMode()" class="flex items-center gap-2 rounded-md px-3 py-1.5 text-xs font-semibold text-gray-600 transition-all">
              <svg lucidePencil class="h-3.5 w-3.5"></svg> Edit
            </button>
            <button (click)="previewMode.set(true)" [class.bg-white]="previewMode()" [class.shadow-sm]="previewMode()" class="flex items-center gap-2 rounded-md px-3 py-1.5 text-xs font-semibold text-gray-600 transition-all">
              <svg lucideEye class="h-3.5 w-3.5"></svg> Preview
            </button>
          </div>

          <div class="h-6 w-px bg-gray-200"></div>

          <button (click)="handleSaveDraft()" [disabled]="isSaving()" class="flex items-center gap-2 rounded-lg bg-white px-4 py-2 text-sm font-semibold text-gray-700 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 transition-all disabled:opacity-50">
            <svg lucideSave class="h-4 w-4" [class.animate-pulse]="isSaving()"></svg>
            {{ isSaving() ? 'Saving...' : 'Save Draft' }}
          </button>
          
          <button *ngIf="cms.canPublish()" (click)="handlePublish()" [disabled]="isPublishing()" class="flex items-center gap-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 px-4 py-2 text-sm font-semibold text-white shadow-sm transition-all disabled:opacity-50">
            <svg lucideGlobe class="h-4 w-4" [class.animate-spin]="isPublishing()"></svg>
            {{ isPublishing() ? 'Publishing...' : 'Publish' }}
          </button>
        </div>
      </header>

      <!-- MAIN EDITOR WORKSPACE -->
      <div class="flex flex-1 overflow-hidden relative">
        
        <!-- LEFT SIDEBAR: Layers/Sections -->
        <aside *ngIf="!previewMode()" class="z-20 flex w-72 shrink-0 flex-col border-r border-gray-200 bg-white transition-all duration-300">
          <div class="flex h-14 shrink-0 items-center justify-between border-b border-gray-100 px-4">
            <h2 class="text-sm font-bold text-gray-900">Page Sections</h2>
          </div>
          
          <div class="flex-1 overflow-y-auto p-3 space-y-1">
            <button 
              *ngFor="let section of activeSections(); trackBy: trackSection; let i = index"
              (click)="selectSection(section.id)"
              [class.bg-blue-50]="selectedSectionId() === section.id"
              [class.text-blue-700]="selectedSectionId() === section.id"
              [class.text-gray-700]="selectedSectionId() !== section.id"
              class="group flex w-full items-center justify-between rounded-lg px-3 py-2.5 text-sm font-medium hover:bg-gray-50 transition-colors"
            >
              <div class="flex items-center gap-3 truncate">
                <svg lucideGripVertical class="h-4 w-4 text-gray-300 opacity-0 transition-opacity group-hover:opacity-100 shrink-0"></svg>
                <div class="flex items-center gap-2.5 truncate">
                  <span class="truncate">{{ getSectionTitle(section.id) }}</span>
                </div>
              </div>
              <div class="flex items-center gap-1 shrink-0">
                <button (click)="$event.stopPropagation(); toggleVisible(section.id)" class="rounded p-1 text-gray-400 hover:bg-gray-200 hover:text-gray-900 transition-colors">
                  <svg *ngIf="section.visible" lucideEye class="h-3.5 w-3.5"></svg>
                  <svg *ngIf="!section.visible" lucideEyeOff class="h-3.5 w-3.5"></svg>
                </button>
              </div>
            </button>
          </div>
        </aside>

        <!-- CENTER: Canvas / Iframe Preview -->
        <main class="relative flex flex-1 flex-col items-center overflow-y-auto bg-gray-50 p-6">
          <div class="relative w-full max-w-[1280px] overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-gray-900/5 transition-all duration-300" style="min-height: 850px;">
            <iframe #previewFrame class="h-full w-full border-0 min-h-[850px]"></iframe>
          </div>
        </main>

        <!-- RIGHT SIDEBAR: Contextual Editor -->
        <aside *ngIf="!previewMode()" class="z-20 flex w-[440px] shrink-0 flex-col border-l border-gray-200 bg-white transition-all duration-300">
          <div class="flex h-14 shrink-0 items-center justify-between border-b border-gray-100 px-5">
            <h2 class="text-sm font-bold text-gray-900 truncate">
              {{ editingArticle() ? 'Edit Article Content' : 'Edit ' + getSectionTitle(selectedSectionId()) }}
            </h2>
            <button
              *ngIf="editingArticle()"
              (click)="closeArticleEditor()"
              class="text-xs font-semibold text-gray-500 hover:text-gray-900 flex items-center gap-1"
            >
              <svg lucideArrowLeft class="h-3.5 w-3.5"></svg> All Articles
            </button>
          </div>

          <div class="flex-1 overflow-y-auto p-5">
            
            <!-- SPECIAL CASE: INSIGHTS ARTICLES CONTENT MANAGEMENT (When editing article cards on /admin/pages/insights) -->
            <div *ngIf="pageId() === 'insights' && selectedSectionId() === 'articles'">
              
              <!-- VIEW 1: SINGLE ARTICLE CONTENT EDITOR -->
              <div *ngIf="editingArticle() as art" class="space-y-4">
                <div class="flex items-center justify-between pb-2 border-b border-gray-100">
                  <button
                    (click)="closeArticleEditor()"
                    class="inline-flex items-center gap-1.5 text-xs font-semibold text-blue-600 hover:text-blue-800"
                  >
                    <svg lucideArrowLeft class="h-3.5 w-3.5"></svg> Back to Articles list
                  </button>
                  <span class="rounded-full bg-blue-50 px-2 py-0.5 text-[10px] font-bold uppercase text-blue-700">
                    {{ art.tag }}
                  </span>
                </div>

                <!-- Title & Slug -->
                <div class="space-y-1">
                  <label class="text-[11px] font-bold uppercase tracking-wider text-gray-500">Article Title *</label>
                  <input
                    type="text"
                    [(ngModel)]="art.title"
                    class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none"
                  />
                </div>

                <div class="grid grid-cols-2 gap-3">
                  <div class="space-y-1">
                    <label class="text-[11px] font-bold uppercase tracking-wider text-gray-500">Category / Tag</label>
                    <select
                      [(ngModel)]="art.tag"
                      class="w-full rounded-lg border border-gray-300 bg-white px-2.5 py-1.5 text-xs text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none"
                    >
                      <option value="Industry">Industry</option>
                      <option value="Product Update">Product Update</option>
                      <option value="Announcement">Announcement</option>
                      <option value="Guide">Guide</option>
                      <option value="Technology">Technology</option>
                    </select>
                  </div>
                  <div class="space-y-1">
                    <label class="text-[11px] font-bold uppercase tracking-wider text-gray-500">Date</label>
                    <input
                      type="text"
                      [(ngModel)]="art.date"
                      class="w-full rounded-lg border border-gray-300 bg-white px-2.5 py-1.5 text-xs text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none"
                    />
                  </div>
                </div>

                <div class="space-y-1">
                  <label class="text-[11px] font-bold uppercase tracking-wider text-gray-500">Author</label>
                  <input
                    type="text"
                    [(ngModel)]="art.author"
                    class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-xs text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none"
                  />
                </div>

                <!-- Excerpt -->
                <div class="space-y-1">
                  <label class="text-[11px] font-bold uppercase tracking-wider text-gray-500">Excerpt / Card Summary *</label>
                  <textarea
                    [(ngModel)]="art.excerpt"
                    rows="2"
                    class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-xs text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none"
                  ></textarea>
                </div>

                <!-- Cover Image -->
                <div class="space-y-1">
                  <label class="text-[11px] font-bold uppercase tracking-wider text-gray-500">Cover Image</label>
                  <div class="flex items-center gap-2">
                    <input
                      type="text"
                      [(ngModel)]="art.image"
                      class="flex-1 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-mono text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none"
                    />
                    <div class="h-9 w-12 shrink-0 overflow-hidden rounded-md border border-gray-200 bg-gray-100">
                      <img [src]="art.image || '/assets/blog-market.jpg'" class="h-full w-full object-cover" />
                    </div>
                  </div>
                  <!-- Quick presets -->
                  <div class="flex flex-wrap gap-1.5 pt-1">
                    <button
                      type="button"
                      *ngFor="let p of presetImages"
                      (click)="art.image = p.url"
                      class="rounded border border-gray-200 bg-gray-50 px-2 py-0.5 text-[10px] text-gray-600 hover:bg-gray-100"
                    >
                      {{ p.label }}
                    </button>
                  </div>
                </div>

                <!-- Content with Headings & Bullets -->
                <div class="space-y-1 pt-2">
                  <div class="flex items-center justify-between">
                    <label class="text-[11px] font-bold uppercase tracking-wider text-gray-500">
                      Article Content (Headings & Bullets) *
                    </label>
                    <div class="flex items-center gap-1.5">
                      <button
                        type="button"
                        (click)="insertArticleHeading(art)"
                        class="inline-flex items-center gap-1 rounded border border-gray-200 bg-gray-50 px-2 py-0.5 text-[10px] font-semibold text-gray-600 hover:bg-gray-100"
                      >
                        <svg lucideHeading class="h-3 w-3"></svg> + Heading
                      </button>
                      <button
                        type="button"
                        (click)="insertArticleBullet(art)"
                        class="inline-flex items-center gap-1 rounded border border-gray-200 bg-gray-50 px-2 py-0.5 text-[10px] font-semibold text-gray-600 hover:bg-gray-100"
                      >
                        <svg lucideList class="h-3 w-3"></svg> + Bullet
                      </button>
                    </div>
                  </div>

                  <textarea
                    [(ngModel)]="art.body"
                    rows="10"
                    placeholder="Use '### Heading' for sections and '- Item' for bullet points..."
                    class="w-full rounded-lg border border-gray-300 bg-white p-3 text-xs font-mono text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none leading-relaxed"
                  ></textarea>
                  <p class="text-[10px] text-gray-500">
                    Use <code>### Heading</code> for section titles, <code>- Item</code> for bullets, and blank lines between paragraphs.
                  </p>
                </div>

                <!-- Formatted Live Preview inside Sidebar -->
                <div class="mt-4 rounded-lg border border-gray-200 bg-gray-50/70 p-3 space-y-3">
                  <div class="text-[10px] font-bold text-gray-500 uppercase tracking-wider flex items-center gap-1">
                    <svg lucideEye class="h-3.5 w-3.5"></svg> Live Section Preview
                  </div>
                  <div class="space-y-3 text-xs">
                    <ng-container *ngFor="let s of parseArticleSections(art.body)">
                      <div class="space-y-1.5">
                        <h4 *ngIf="s.heading" class="font-bold text-gray-900 text-sm border-b border-gray-200 pb-0.5">
                          {{ s.heading }}
                        </h4>
                        <p *ngFor="let p of s.paragraphs" class="text-gray-700 leading-relaxed">
                          {{ p }}
                        </p>
                        <ul *ngIf="s.bullets?.length" class="space-y-1 pl-2">
                          <li *ngFor="let b of s.bullets" class="flex items-start gap-1.5 text-gray-700">
                            <span class="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-blue-600"></span>
                            <span>{{ b }}</span>
                          </li>
                        </ul>
                      </div>
                    </ng-container>
                  </div>
                </div>

                <!-- Action Button -->
                <div class="pt-3 flex items-center justify-between border-t border-gray-200">
                  <button
                    type="button"
                    (click)="closeArticleEditor()"
                    class="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-semibold text-gray-600 hover:bg-gray-50"
                  >
                    Done
                  </button>
                  <button
                    type="button"
                    (click)="saveCurrentArticle(art)"
                    class="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-4 py-1.5 text-xs font-bold text-white shadow-sm hover:bg-blue-700 transition-colors"
                  >
                    <svg lucideCheckCircle2 class="h-3.5 w-3.5"></svg> Save Article Content
                  </button>
                </div>
              </div>

              <!-- VIEW 2: ARTICLES LIST & HEADER SETTINGS -->
              <div *ngIf="!editingArticle()" class="space-y-6">
                <!-- Section Intro & Read Link label -->
                <div class="space-y-3 pb-4 border-b border-gray-200">
                  <div class="space-y-1">
                    <label class="text-[11px] font-bold uppercase tracking-wider text-gray-500">Section Intro (Optional)</label>
                    <textarea
                      [value]="selectedSectionContent()?.fields?.['intro'] || ''"
                      (input)="setField('articles', 'intro', $any($event.target).value)"
                      rows="2"
                      placeholder="Optional introductory text above the articles grid..."
                      class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-xs text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none"
                    ></textarea>
                  </div>
                  <div class="space-y-1">
                    <label class="text-[11px] font-bold uppercase tracking-wider text-gray-500">Read Link Label</label>
                    <input
                      type="text"
                      [value]="selectedSectionContent()?.fields?.['readLabel'] || 'Read article'"
                      (input)="setField('articles', 'readLabel', $any($event.target).value)"
                      class="w-full rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none"
                    />
                  </div>
                </div>

                <!-- Insights Articles Manager -->
                <div>
                  <div class="flex items-center justify-between mb-3">
                    <div>
                      <h3 class="text-xs font-bold uppercase tracking-wider text-gray-800">
                        Insights Articles ({{ insightArticles().length }})
                      </h3>
                      <p class="text-[11px] text-gray-500 mt-0.5">Click any article to edit its content, headings, and bullet points.</p>
                    </div>
                    <button
                      type="button"
                      (click)="addNewInsightArticle()"
                      class="inline-flex items-center gap-1 rounded-md bg-blue-50 px-2 py-1 text-[11px] font-bold text-blue-700 hover:bg-blue-100"
                    >
                      <svg lucidePlus class="h-3 w-3"></svg> New Article
                    </button>
                  </div>

                  <div class="space-y-2">
                    <div
                      *ngFor="let art of insightArticles(); let i = index"
                      (click)="openArticleEditor(art)"
                      class="group flex items-center justify-between rounded-xl border border-gray-200 bg-white p-2.5 shadow-2xs hover:border-blue-500 hover:bg-blue-50/30 transition-all cursor-pointer"
                    >
                      <div class="flex items-center gap-3 min-w-0">
                        <div class="h-10 w-12 shrink-0 overflow-hidden rounded-lg bg-gray-100 border border-gray-200">
                          <img [src]="art.image || '/assets/blog-market.jpg'" class="h-full w-full object-cover" />
                        </div>
                        <div class="min-w-0">
                          <p class="text-xs font-bold text-gray-900 truncate group-hover:text-blue-600">{{ art.title }}</p>
                          <div class="flex items-center gap-2 text-[10px] text-gray-500 mt-0.5">
                            <span class="font-semibold text-blue-600 uppercase">{{ art.tag }}</span>
                            <span>&bull;</span>
                            <span>{{ art.date }}</span>
                          </div>
                        </div>
                      </div>
                      <div class="shrink-0 pl-2">
                        <span class="inline-flex items-center gap-1 text-[11px] font-bold text-gray-400 group-hover:text-blue-600">
                          Edit <svg lucideChevronRight class="h-3 w-3"></svg>
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

            </div>

            <!-- STANDARD SECTIONS (Hero, CTA, or any other page) -->
            <div *ngIf="!(pageId() === 'insights' && selectedSectionId() === 'articles')">
              <div *ngIf="selectedSectionDef() as def">
                <p *ngIf="def.note" class="text-xs text-gray-500 mb-5 leading-relaxed">{{ def.note }}</p>
                
                <!-- Core fields -->
                <div class="space-y-4">
                  <div *ngFor="let field of def.fields; trackBy: trackField" class="space-y-1.5">
                    <label class="text-[11px] font-bold uppercase tracking-wider text-gray-500">{{ field.label }}</label>
                    
                    <input *ngIf="field.type === 'text' || field.type === 'link' || field.type === 'image'"
                      type="text"
                      [value]="selectedSectionContent()?.fields?.[field.key] || ''"
                      (input)="setField(def.id, field.key, $any($event.target).value)"
                      class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    >
                    
                    <textarea *ngIf="field.type === 'textarea'"
                      [value]="selectedSectionContent()?.fields?.[field.key] || ''"
                      (input)="setField(def.id, field.key, $any($event.target).value)"
                      rows="3"
                      class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    ></textarea>
                  </div>
                </div>

                <!-- Cards / Blocks (if section defines cardFields) -->
                <div *ngIf="def.cardFields && def.cardFields.length > 0" class="mt-8 border-t border-gray-200 pt-6">
                  <div class="flex items-center justify-between mb-4">
                    <h3 class="text-xs font-bold uppercase tracking-wider text-gray-700">
                      {{ def.cardsLabel || 'Items' }} ({{ selectedSectionCards().length }})
                    </h3>
                  </div>
                  
                  <div class="space-y-4">
                    <div *ngFor="let card of selectedSectionCards(); trackBy: trackCard; let ci = index" class="rounded-lg border border-gray-200 bg-gray-50/70 p-3 space-y-3">
                      <div class="text-[10px] font-bold text-gray-500 uppercase tracking-wide">Item #{{ ci + 1 }}</div>
                      
                      <div *ngFor="let cfield of def.cardFields; trackBy: trackField" class="space-y-1">
                        <label class="text-[10px] font-bold uppercase tracking-wider text-gray-500">{{ cfield.label }}</label>
                        
                        <input *ngIf="cfield.type === 'text' || cfield.type === 'link' || cfield.type === 'image'"
                          type="text"
                          [value]="card.fields[cfield.key] || ''"
                          (input)="setCardField(def.id, ci, cfield.key, $any($event.target).value)"
                          class="w-full rounded-md border border-gray-300 bg-white px-2.5 py-1.5 text-xs text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                        >
                        
                        <textarea *ngIf="cfield.type === 'textarea'"
                          [value]="card.fields[cfield.key] || ''"
                          (input)="setCardField(def.id, ci, cfield.key, $any($event.target).value)"
                          rows="2"
                          class="w-full rounded-md border border-gray-300 bg-white px-2.5 py-1.5 text-xs text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                        ></textarea>
                      </div>
                    </div>
                  </div>
                </div>

              </div>
            </div>

          </div>
        </aside>

      </div>
    </div>
  `
})
export class VisualHomeEditorComponent implements OnInit, AfterViewInit {
  cms = inject(CmsService);
  session = inject(SessionService);
  http = inject(HttpClient);
  route = inject(ActivatedRoute);

  isPublishing = this.cms.isPublishing;
  publishSuccess = signal(false);
  backendSynced = signal(false);
  saveSuccess = signal(false);
  toastMessage = signal<string | null>(null);

  previewFrame = viewChild<ElementRef<HTMLIFrameElement>>('previewFrame');

  pageId = signal<string>('home');
  selectedSectionId = signal<string>('hero');
  previewMode = signal<boolean>(false);
  isSaving = signal<boolean>(false);

  // Insights Articles State
  insightArticles = signal<AdminBlogPost[]>([]);
  editingArticle = signal<AdminBlogPost | null>(null);

  presetImages = [
    { label: 'Market / Accessories', url: '/assets/blog-market.jpg' },
    { label: 'GaN 100W Charger', url: '/assets/blog-gan.jpg' },
    { label: 'Periwinkle / Construction', url: '/assets/blog-groundbreak.jpg' },
    { label: 'Power Banks / Inspection', url: '/assets/blog-counterfeit.jpg' }
  ];

  pageDef = computed(() => this.cms.pageDef(this.pageId()));
  draft = computed(() => this.cms.drafts()[this.pageId()]);
  pub = computed(() => this.cms.published()[this.pageId()]);

  isDirty = computed(() => JSON.stringify(this.pub()?.sections) !== JSON.stringify(this.draft()?.sections));

  validSectionIds = computed(() => new Set(this.pageDef()?.sections.map((s: SectionDef) => s.id) || []));
  activeSections = computed(() => (this.draft()?.sections || []).filter((s: SectionContent) => this.validSectionIds().has(s.id)));

  selectedSectionDef = computed(() => this.pageDef()?.sections.find((s: SectionDef) => s.id === this.selectedSectionId()));
  selectedSectionContent = computed(() => this.draft()?.sections.find((s: SectionContent) => s.id === this.selectedSectionId()));
  selectedSectionCards = computed(() => this.selectedSectionContent()?.cards || []);

  constructor() {
    effect(() => {
      const def = this.pageDef();
      if (def) {
        this.reloadPreview();
        if (def.sections.length > 0) {
          this.selectedSectionId.set(def.sections[0].id);
        }
      }
    });
  }

  ngOnInit() {
    this.cms.loadDrafts().subscribe({error:e=>this.cms.error.set(this.cms.message(e))});
    this.route.paramMap.subscribe(params => {
      const routeId = params.get('id');
      if (routeId) {
        this.pageId.set(routeId);
      } else {
        const parts = window.location.pathname.split('/');
        const lastPart = parts[parts.length - 1];
        if (lastPart && lastPart !== 'pages') {
          this.pageId.set(lastPart);
        }
      }

      if (this.pageId() === 'insights') {
        this.loadInsightArticles();
      }
    });
  }

  ngAfterViewInit() {
    this.reloadPreview();
  }

  loadInsightArticles(){this.cms.loadBlogs().subscribe({next:rows=>this.insightArticles.set(rows),error:e=>this.cms.error.set(this.cms.message(e))});}

  saveInsightArticles(){this.cms.error.set('Save each article using the article editor before publishing this page.');}

  selectSection(id: string) {
    this.selectedSectionId.set(id);
    this.editingArticle.set(null);
  }

  openArticleEditor(article: AdminBlogPost) {
    this.editingArticle.set({ ...article });
  }

  closeArticleEditor() {
    this.editingArticle.set(null);
  }

  saveCurrentArticle(art:AdminBlogPost){this.cms.saveBlog(art).subscribe({next:()=>{this.loadInsightArticles();this.editingArticle.set(null);this.saveSuccess.set(true);this.reloadPreview();},error:e=>this.cms.error.set(this.cms.message(e))});}

  addNewInsightArticle() {
    const newArt: AdminBlogPost = {
      id: 'post-' + Date.now(),
      title: 'New Insight Article',
      slug: 'new-insight-' + Date.now(),
      tag: 'Industry',
      author: 'Nigson Team',
      date: new Date().toLocaleDateString('en-US', { month: 'short', year: 'numeric' }),
      status: 'Published',
      excerpt: 'Brief overview of this article...',
      image: '/assets/blog-market.jpg',
      body: `Introduction paragraph goes here.\n\n### Main Heading\nDetailed section content.\n\n- Key feature or bullet point 1\n- Key feature or bullet point 2\n\n### Conclusion\nFinal closing thoughts.`
    };
    newArt.sections = parseMarkdownToSections(newArt.body);
    this.insightArticles.update(list => [newArt, ...list]);
    this.openArticleEditor(newArt);
    this.saveInsightArticles();
  }

  insertArticleHeading(art: AdminBlogPost) {
    const snippet = '\n\n### New Section Heading\nSection content goes here.';
    art.body = ((art.body || '').trimEnd() + snippet).trim();
  }

  insertArticleBullet(art: AdminBlogPost) {
    const snippet = '\n- Bullet detail item';
    art.body = ((art.body || '').trimEnd() + snippet).trim();
  }

  parseArticleSections(body: string) {
    return parseMarkdownToSections(body || '');
  }

  getBaseUrl(): string {
    if (typeof window !== 'undefined' && window.location.origin) {
      return window.location.origin;
    }
    return 'http://localhost:4200';
  }

  reloadPreview() {
    const def = this.pageDef();
    if (def) {
      const base = this.getBaseUrl();
      const url = `${base}${def.path}?preview=true&_t=${Date.now()}`;
      const frame = this.previewFrame()?.nativeElement;
      if (frame) {
        frame.src = url;
      }
    }
  }

  getSectionTitle(id: string): string {
    return this.pageDef()?.sections.find((s: SectionDef) => s.id === id)?.title || id;
  }

  trackSection(_index: number, section: SectionContent): string { return section.id; }
  trackCard(index: number, card: { id?: string }): string | number { return card.id ?? index; }
  trackField(_index: number, field: { key: string }): string { return field.key; }

  setField(secId: string, key: string, value: string) {
    this.cms.updateDraft(this.pageId(), (p: PageContent) => ({
      ...p,
      sections: p.sections.map((s: SectionContent) =>
        s.id === secId ? { ...s, fields: { ...s.fields, [key]: value } } : s
      )
    }));
  }

  setCardField(secId: string, cardIndex: number, key: string, value: string) {
    this.cms.updateDraft(this.pageId(), (p: PageContent) => ({
      ...p,
      sections: p.sections.map((s: SectionContent) => {
        if (s.id !== secId) return s;
        const updatedCards = [...(s.cards || [])];
        if (updatedCards[cardIndex]) {
          updatedCards[cardIndex] = {
            ...updatedCards[cardIndex],
            fields: { ...updatedCards[cardIndex].fields, [key]: value }
          };
        }
        return { ...s, cards: updatedCards };
      })
    }));
  }

  toggleVisible(secId: string) {
    this.cms.updateDraft(this.pageId(), (p: PageContent) => ({
      ...p,
      sections: p.sections.map((s: SectionContent) =>
        s.id === secId ? { ...s, visible: !s.visible } : s
      )
    }));
  }

  handleSaveDraft(){this.isSaving.set(true);this.saveSuccess.set(false);this.cms.saveDraft(this.pageId()).subscribe({next:()=>{this.isSaving.set(false);this.saveSuccess.set(true);this.reloadPreview();},error:()=>this.isSaving.set(false)});}

  handlePublish(){this.publishSuccess.set(false);this.backendSynced.set(false);this.cms.executePublishPipeline(this.pageId()).subscribe({next:()=>{this.backendSynced.set(true);this.publishSuccess.set(true);this.reloadPreview();},error:()=>{this.backendSynced.set(false);this.publishSuccess.set(false);}});}
}

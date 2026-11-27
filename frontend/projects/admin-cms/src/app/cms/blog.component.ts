import { Component, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { AdminHeadingComponent, CmsService } from 'shared-ui';
import {
  LucidePlus,
  LucidePencil,
  LucideTrash2,
  LucideGlobe,
  LucideFileText,
  LucideExternalLink,
  LucideX,
  LucideCheckCircle2,
  LucideSearch,
  LucideList,
  LucideHeading
} from '@lucide/angular';

export interface BlogSection {
  heading?: string;
  paragraphs?: string[];
  bullets?: string[];
}

export interface AdminBlogPost {
  id: string;
  title: string;
  slug: string;
  tag: string;
  author: string;
  date: string;
  status: 'Published' | 'Draft';
  excerpt: string;
  image: string;
  body: string;
  sections?: BlogSection[];
}

export function parseMarkdownToSections(markdown: string): BlogSection[] {
  if (!markdown || !markdown.trim()) return [];

  const lines = markdown.split(/\r?\n/);
  const sections: BlogSection[] = [];
  let currentSection: BlogSection = { paragraphs: [], bullets: [] };
  let currentParagraphLines: string[] = [];

  function flushParagraph() {
    if (currentParagraphLines.length > 0) {
      const p = currentParagraphLines.join(' ').trim();
      if (p) {
        if (!currentSection.paragraphs) currentSection.paragraphs = [];
        currentSection.paragraphs.push(p);
      }
      currentParagraphLines = [];
    }
  }

  for (const rawLine of lines) {
    const line = rawLine.trim();

    // Check for headings (e.g., "### Heading", "## Heading", "# Heading")
    const headingMatch = line.match(/^#{1,4}\s+(.+)$/);
    if (headingMatch) {
      flushParagraph();
      if (
        currentSection.heading ||
        (currentSection.paragraphs && currentSection.paragraphs.length > 0) ||
        (currentSection.bullets && currentSection.bullets.length > 0)
      ) {
        sections.push(currentSection);
      }
      currentSection = { heading: headingMatch[1].trim(), paragraphs: [], bullets: [] };
      continue;
    }

    // Check for bullet points (e.g., "- bullet", "* bullet", "• bullet")
    const bulletMatch = line.match(/^[-*•]\s+(.+)$/);
    if (bulletMatch) {
      flushParagraph();
      if (!currentSection.bullets) currentSection.bullets = [];
      currentSection.bullets.push(bulletMatch[1].trim());
      continue;
    }

    // Empty line -> paragraph separator
    if (!line) {
      flushParagraph();
      continue;
    }

    // Regular text line
    currentParagraphLines.push(line);
  }

  flushParagraph();
  if (
    currentSection.heading ||
    (currentSection.paragraphs && currentSection.paragraphs.length > 0) ||
    (currentSection.bullets && currentSection.bullets.length > 0)
  ) {
    sections.push(currentSection);
  }

  return sections.length > 0 ? sections : [{ paragraphs: [markdown.trim()] }];
}

@Component({
  selector: 'app-admin-blog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    AdminHeadingComponent,
    LucidePlus,
    LucidePencil,
    LucideTrash2,
    LucideGlobe,
    LucideFileText,
    LucideExternalLink,
    LucideX,
    LucideCheckCircle2,
    LucideSearch,
    LucideList,
    LucideHeading
  ],
  template: `
    <!-- Top notification banner -->
    <div *ngIf="toastMessage()" class="fixed top-20 right-8 z-50 flex items-center gap-3 rounded-xl bg-emerald-600 px-5 py-3 text-white shadow-2xl transition-all">
      <svg lucideCheckCircle2 class="h-5 w-5 shrink-0"></svg>
      <span class="text-sm font-semibold">{{ toastMessage() }}</span>
    </div>

    <lib-admin-heading title="Blog Posts" subtitle="Write and manage articles. Add a new post to appear in Insights.">
      <button
        (click)="openCreateModal()"
        class="flex h-10 items-center gap-2 rounded-xl bg-brand px-4 text-sm font-bold text-white shadow-sm transition-all hover:bg-brand-600 hover:shadow"
      >
        <svg lucidePlus class="h-4 w-4"></svg> New Post
      </button>
    </lib-admin-heading>

    <div class="rounded-2xl border border-hairline bg-white shadow-sm overflow-hidden mt-4">
      <!-- Search and Filter bar -->
      <div class="flex flex-col sm:flex-row items-center justify-between gap-4 border-b border-hairline p-4 bg-surface/50">
        <div class="relative flex-1 w-full sm:max-w-md">
          <svg lucideSearch class="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"></svg>
          <input
            type="text"
            [(ngModel)]="searchQuery"
            placeholder="Search by title, tag, slug, or content..."
            class="w-full rounded-xl border border-hairline bg-white py-2 pl-10 pr-4 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
          />
        </div>

        <!-- Filter tabs -->
        <div class="flex items-center gap-1 rounded-xl bg-surface-alt p-1 text-xs font-semibold">
          <button
            (click)="statusFilter.set('ALL')"
            [class]="statusFilter() === 'ALL' ? 'rounded-lg bg-white px-3 py-1.5 text-ink shadow-sm' : 'rounded-lg px-3 py-1.5 text-muted-ink hover:text-ink'"
          >
            All ({{ posts().length }})
          </button>
          <button
            (click)="statusFilter.set('Published')"
            [class]="statusFilter() === 'Published' ? 'rounded-lg bg-white px-3 py-1.5 text-emerald-600 shadow-sm' : 'rounded-lg px-3 py-1.5 text-muted-ink hover:text-emerald-600'"
          >
            Published
          </button>
          <button
            (click)="statusFilter.set('Draft')"
            [class]="statusFilter() === 'Draft' ? 'rounded-lg bg-white px-3 py-1.5 text-amber-600 shadow-sm' : 'rounded-lg px-3 py-1.5 text-muted-ink hover:text-amber-600'"
          >
            Draft
          </button>
        </div>
      </div>

      <!-- Posts Table -->
      <div class="overflow-x-auto">
        <table class="w-full min-w-[760px] text-left text-sm text-ink">
          <thead class="border-b border-hairline bg-surface-alt/50 text-[10px] font-bold uppercase tracking-widest text-muted-ink">
            <tr>
              <th class="px-6 py-4">Article</th>
              <th class="px-6 py-4">Tag</th>
              <th class="px-6 py-4">Author</th>
              <th class="px-6 py-4">Date</th>
              <th class="px-6 py-4">Status</th>
              <th class="px-6 py-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-hairline">
            <tr
              *ngFor="let p of filteredPosts()"
              class="group transition-colors hover:bg-surface-alt/40 cursor-pointer"
              (click)="openEditModal(p)"
            >
              <!-- Thumbnail + Title + Excerpt -->
              <td class="px-6 py-4">
                <div class="flex items-center gap-3.5">
                  <div class="relative h-12 w-16 shrink-0 overflow-hidden rounded-xl bg-surface-alt border border-hairline shadow-2xs">
                    <img
                      [src]="p.image || '/assets/blog-market.jpg'"
                      [alt]="p.title"
                      class="h-full w-full object-cover transition-transform group-hover:scale-105"
                      (error)="handleImageError($event)"
                    />
                  </div>
                  <div class="min-w-0">
                    <p class="font-bold text-ink group-hover:text-brand transition-colors line-clamp-1">{{ p.title }}</p>
                    <p class="text-xs text-muted-ink line-clamp-1 mt-0.5">{{ p.excerpt }}</p>
                    <span class="text-[11px] text-gray-400 font-mono">/insights/{{ p.slug }}</span>
                  </div>
                </div>
              </td>

              <!-- Tag -->
              <td class="px-6 py-4 whitespace-nowrap" (click)="$event.stopPropagation()">
                <span
                  class="rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wider"
                  [ngClass]="{
                    'bg-blue-50 text-blue-700': p.tag === 'Product Update',
                    'bg-emerald-50 text-emerald-700': p.tag === 'Guide',
                    'bg-amber-50 text-amber-700': p.tag === 'Announcement',
                    'bg-purple-50 text-purple-700': p.tag === 'Industry',
                    'bg-gray-100 text-gray-700': !['Product Update', 'Guide', 'Announcement', 'Industry'].includes(p.tag)
                  }"
                >
                  {{ p.tag }}
                </span>
              </td>

              <!-- Author -->
              <td class="px-6 py-4 whitespace-nowrap text-xs text-muted-ink" (click)="$event.stopPropagation()">
                {{ p.author }}
              </td>

              <!-- Date -->
              <td class="px-6 py-4 whitespace-nowrap text-xs text-muted-ink font-medium" (click)="$event.stopPropagation()">
                {{ p.date }}
              </td>

              <!-- Status Toggle -->
              <td class="px-6 py-4 whitespace-nowrap" (click)="$event.stopPropagation()">
                <button
                  type="button"
                  [disabled]="!cms.canPublish()" (click)="toggleStatus(p)"
                  class="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold transition-all hover:scale-105"
                  [ngClass]="p.status === 'Published' ? 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100' : 'bg-amber-50 text-amber-700 hover:bg-amber-100'"
                  [title]="'Click to toggle status (currently ' + p.status + ')'"
                >
                  <svg *ngIf="p.status === 'Published'" lucideGlobe class="h-3.5 w-3.5"></svg>
                  <svg *ngIf="p.status === 'Draft'" lucideFileText class="h-3.5 w-3.5"></svg>
                  <span>{{ p.status }}</span>
                </button>
              </td>

              <!-- Actions -->
              <td class="px-6 py-4 text-right whitespace-nowrap" (click)="$event.stopPropagation()">
                <div class="flex items-center justify-end gap-1.5">
                  <!-- View on site button -->
                  <a
                    [href]="'/insights/' + p.slug"
                    target="_blank"
                    class="grid h-8 w-8 place-items-center rounded-lg text-muted-ink transition-colors hover:bg-surface-alt hover:text-ink"
                    title="Open Article on Website"
                  >
                    <svg lucideExternalLink class="h-4 w-4"></svg>
                  </a>

                  <!-- Edit button -->
                  <button
                    (click)="openEditModal(p)"
                    class="grid h-8 w-8 place-items-center rounded-lg text-muted-ink transition-colors hover:bg-brand/10 hover:text-brand"
                    title="Edit / Open Post"
                  >
                    <svg lucidePencil class="h-4 w-4"></svg>
                  </button>

                  <!-- Delete button -->
                  <button
                    [disabled]="!cms.canPublish()" (click)="delete(p.id)"
                    class="grid h-8 w-8 place-items-center rounded-lg text-muted-ink transition-colors hover:bg-rose-100 hover:text-rose-600"
                    title="Delete Post"
                  >
                    <svg lucideTrash2 class="h-4 w-4"></svg>
                  </button>
                </div>
              </td>
            </tr>

            <!-- Empty state -->
            <tr *ngIf="filteredPosts().length === 0">
              <td colspan="6" class="p-12 text-center text-muted-ink">
                <svg lucideFileText class="mx-auto h-10 w-10 text-gray-300 mb-2"></svg>
                <p class="font-medium text-sm">No blog posts found matching your criteria.</p>
                <button
                  (click)="resetToDefaults()"
                  class="mt-3 text-xs font-semibold text-brand hover:underline"
                >
                  Reload Articles
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- MODAL / POST EDITOR DIALOG -->
    <div
      *ngIf="isModalOpen()"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink/50 backdrop-blur-xs animate-in fade-in duration-200"
      (click)="closeModal()"
    >
      <div
        class="w-full max-w-4xl max-h-[92vh] flex flex-col rounded-2xl bg-white shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200 border border-hairline"
        (click)="$event.stopPropagation()"
      >
        <!-- Modal Header -->
        <div class="flex items-center justify-between border-b border-hairline px-6 py-4 bg-surface/50">
          <div>
            <h2 class="text-xl font-bold text-ink">{{ isEditing() ? 'Edit Article' : 'Create New Article' }}</h2>
            <p class="text-xs text-muted-ink mt-0.5">Manage article content, section headings, and bullet points for the Insights page.</p>
          </div>
          <div class="flex items-center gap-2">
            <!-- Mode Switcher: Write vs Preview -->
            <div class="flex items-center bg-surface-alt rounded-lg p-0.5 text-xs font-semibold mr-2">
              <button
                type="button"
                (click)="editorTab.set('write')"
                [class]="editorTab() === 'write' ? 'bg-white text-ink shadow-2xs rounded-md px-3 py-1' : 'text-muted-ink hover:text-ink px-3 py-1'"
              >
                Write Content
              </button>
              <button
                type="button"
                (click)="editorTab.set('preview')"
                [class]="editorTab() === 'preview' ? 'bg-white text-ink shadow-2xs rounded-md px-3 py-1' : 'text-muted-ink hover:text-ink px-3 py-1'"
              >
                Live Preview
              </button>
            </div>

            <a
              *ngIf="editingPost.slug"
              [href]="'/insights/' + editingPost.slug"
              target="_blank"
              class="hidden sm:inline-flex items-center gap-1.5 text-xs font-semibold text-brand hover:underline mr-2"
            >
              <svg lucideExternalLink class="h-3.5 w-3.5"></svg> View on Site
            </a>
            <button
              (click)="closeModal()"
              class="grid h-8 w-8 place-items-center rounded-lg text-muted-ink hover:bg-surface-alt hover:text-ink transition-colors"
            >
              <svg lucideX class="h-5 w-5"></svg>
            </button>
          </div>
        </div>

        <!-- Modal Form Body (Scrollable) -->
        <div class="flex-1 overflow-y-auto p-6 space-y-5">
          <!-- WRITE TAB -->
          <ng-container *ngIf="editorTab() === 'write'">
            <!-- Title & Slug -->
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-bold text-ink mb-1.5 uppercase tracking-wider">Title *</label>
                <input
                  type="text"
                  [(ngModel)]="editingPost.title"
                  (ngModelChange)="onTitleChange($event)"
                  placeholder="e.g. Launching the GaN 100W adapter across Nigeria"
                  class="w-full rounded-xl border border-hairline bg-white px-3.5 py-2.5 text-sm text-ink focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 font-medium"
                />
              </div>
              <div>
                <label class="block text-xs font-bold text-ink mb-1.5 uppercase tracking-wider">Slug (URL path) *</label>
                <input
                  type="text"
                  [(ngModel)]="editingPost.slug"
                  placeholder="e.g. launching-gan-100w"
                  class="w-full rounded-xl border border-hairline bg-white px-3.5 py-2.5 text-sm text-ink focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 font-mono"
                />
              </div>
            </div>

            <!-- Tag, Author, Date, Status -->
            <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div>
                <label class="block text-xs font-bold text-ink mb-1.5 uppercase tracking-wider">Category / Tag</label>
                <select
                  [(ngModel)]="editingPost.tag"
                  class="w-full rounded-xl border border-hairline bg-white px-3 py-2 text-sm text-ink focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
                >
                  <option value="Industry">Industry</option>
                  <option value="Product Update">Product Update</option>
                  <option value="Announcement">Announcement</option>
                  <option value="Guide">Guide</option>
                  <option value="Technology">Technology</option>
                </select>
              </div>

              <div>
                <label class="block text-xs font-bold text-ink mb-1.5 uppercase tracking-wider">Author</label>
                <input
                  type="text"
                  [(ngModel)]="editingPost.author"
                  placeholder="e.g. Nigson Product Team"
                  class="w-full rounded-xl border border-hairline bg-white px-3 py-2 text-sm text-ink focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
                />
              </div>

              <div>
                <label class="block text-xs font-bold text-ink mb-1.5 uppercase tracking-wider">Date</label>
                <input
                  type="text"
                  [(ngModel)]="editingPost.date"
                  placeholder="e.g. Jun 2026"
                  class="w-full rounded-xl border border-hairline bg-white px-3 py-2 text-sm text-ink focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
                />
              </div>

              <div>
                <label class="block text-xs font-bold text-ink mb-1.5 uppercase tracking-wider">Status</label>
                <select
                  [(ngModel)]="editingPost.status"
                  class="w-full rounded-xl border border-hairline bg-white px-3 py-2 text-sm font-semibold focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
                  [ngClass]="editingPost.status === 'Published' ? 'text-emerald-700' : 'text-amber-700'"
                >
                  <option *ngIf="cms.canPublish()" value="Published">Published</option>
                  <option value="Draft">Draft</option>
                </select>
              </div>
            </div>

            <!-- Cover Image & Quick Picker -->
            <div>
              <label class="block text-xs font-bold text-ink mb-1.5 uppercase tracking-wider">Cover Image URL</label>
              <div class="flex gap-3 items-center">
                <input
                  type="text"
                  [(ngModel)]="editingPost.image"
                  placeholder="/assets/blog-gan.jpg"
                  class="flex-1 rounded-xl border border-hairline bg-white px-3.5 py-2 text-sm text-ink focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 font-mono"
                />
                <div class="h-10 w-16 shrink-0 overflow-hidden rounded-xl border border-hairline bg-surface-alt shadow-2xs">
                  <img
                    [src]="editingPost.image || '/assets/blog-market.jpg'"
                    alt="Preview"
                    class="h-full w-full object-cover"
                    (error)="handleImageError($event)"
                  />
                </div>
              </div>
              <!-- Preset image selectors -->
              <div class="flex flex-wrap items-center gap-2 mt-2">
                <span class="text-xs text-muted-ink">Preset images:</span>
                <button
                  type="button"
                  *ngFor="let img of presetImages"
                  (click)="editingPost.image = img.url"
                  class="rounded-lg border border-hairline px-2.5 py-1 text-[11px] font-medium transition-all hover:border-brand hover:text-brand"
                  [class.bg-brand/10]="editingPost.image === img.url"
                  [class.text-brand]="editingPost.image === img.url"
                >
                  {{ img.label }}
                </button>
              </div>
            </div>

            <!-- Excerpt -->
            <div>
              <label class="block text-xs font-bold text-ink mb-1.5 uppercase tracking-wider">Excerpt / Summary *</label>
              <textarea
                [(ngModel)]="editingPost.excerpt"
                rows="2"
                placeholder="Short summary shown on the Insights card (e.g. Our new 4-port GaN charger is now available to distributors nationwide.)..."
                class="w-full rounded-xl border border-hairline bg-white px-3.5 py-2 text-sm text-ink focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 resize-none"
              ></textarea>
            </div>

            <!-- Full Content / Body with Headings & Bullets Toolbar -->
            <div>
              <div class="flex items-center justify-between mb-1.5">
                <label class="block text-xs font-bold text-ink uppercase tracking-wider">
                  Article Content (with Headings & Bullet points) *
                </label>
                <div class="flex items-center gap-2">
                  <button
                    type="button"
                    (click)="insertHeading()"
                    class="inline-flex items-center gap-1 rounded-lg border border-hairline bg-surface px-2 py-0.5 text-[11px] font-semibold text-muted-ink hover:text-ink hover:bg-surface-alt"
                  >
                    <svg lucideHeading class="h-3 w-3"></svg> + Section Heading
                  </button>
                  <button
                    type="button"
                    (click)="insertBullet()"
                    class="inline-flex items-center gap-1 rounded-lg border border-hairline bg-surface px-2 py-0.5 text-[11px] font-semibold text-muted-ink hover:text-ink hover:bg-surface-alt"
                  >
                    <svg lucideList class="h-3 w-3"></svg> + Bullet Point
                  </button>
                </div>
              </div>

              <textarea
                #contentTextarea
                [(ngModel)]="editingPost.body"
                rows="12"
                placeholder="Write full article here. Use '### Heading' for sections and '- Bullet point' for list items..."
                class="w-full rounded-xl border border-hairline bg-white p-4 text-xs font-mono text-ink focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 leading-relaxed"
              ></textarea>
              <p class="mt-1 text-[11px] text-muted-ink">
                <strong>Formatting guide:</strong> Use <code>### Heading Name</code> for section titles, <code>- Item</code> for bullet points, and blank lines to separate paragraphs.
              </p>
            </div>
          </ng-container>

          <!-- LIVE PREVIEW TAB -->
          <ng-container *ngIf="editorTab() === 'preview'">
            <div class="rounded-2xl border border-hairline bg-white p-6 sm:p-8 space-y-6">
              <div class="flex flex-wrap items-center gap-3 text-xs">
                <span class="rounded-full bg-brand/10 px-3 py-1 font-semibold text-brand uppercase tracking-widest">
                  {{ editingPost.tag || 'Industry' }}
                </span>
                <span class="text-muted-ink">{{ editingPost.date }}</span>
                <span class="text-muted-ink">&bull;</span>
                <span class="text-muted-ink">By {{ editingPost.author || 'Nigson Team' }}</span>
              </div>

              <h1 class="text-3xl font-extrabold text-ink leading-tight tracking-tight">
                {{ editingPost.title || 'Untitled Article' }}
              </h1>

              <p class="text-lg text-muted-ink italic border-l-4 border-brand pl-4">
                {{ editingPost.excerpt || 'No summary excerpt provided.' }}
              </p>

              <div class="aspect-[16/9] max-h-72 overflow-hidden rounded-2xl bg-surface-alt">
                <img
                  [src]="editingPost.image || '/assets/blog-market.jpg'"
                  [alt]="editingPost.title"
                  class="h-full w-full object-cover"
                />
              </div>

              <!-- Rendered Sections with Headings & Bullets -->
              <div class="space-y-6 pt-4 border-t border-hairline">
                <ng-container *ngFor="let s of previewSections()">
                  <div class="space-y-3">
                    <h2 *ngIf="s.heading" class="text-2xl font-bold text-ink tracking-tight">
                      {{ s.heading }}
                    </h2>
                    <p *ngFor="let p of s.paragraphs" class="text-base text-ink/85 leading-relaxed">
                      {{ p }}
                    </p>
                    <ul *ngIf="s.bullets?.length" class="space-y-2 pl-2">
                      <li *ngFor="let b of s.bullets" class="flex items-start gap-3 text-base text-ink/85 leading-relaxed">
                        <span class="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-brand"></span>
                        <span>{{ b }}</span>
                      </li>
                    </ul>
                  </div>
                </ng-container>
              </div>
            </div>
          </ng-container>
        </div>

        <!-- Modal Footer -->
        <div class="flex items-center justify-between border-t border-hairline px-6 py-4 bg-surface/50">
          <button
            type="button"
            (click)="closeModal()"
            class="rounded-xl border border-hairline px-5 py-2.5 text-sm font-semibold text-muted-ink hover:bg-surface-alt hover:text-ink transition-colors"
          >
            Cancel
          </button>
          <div class="flex items-center gap-3">
            <button
              type="button"
              (click)="savePost()"
              class="flex items-center gap-2 rounded-xl bg-brand px-6 py-2.5 text-sm font-bold text-white shadow-sm transition-all hover:bg-brand-600 hover:shadow"
            >
              <svg lucideCheckCircle2 class="h-4 w-4"></svg>
              <span>{{ isEditing() ? 'Save Changes' : 'Create Article' }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminBlogComponent {
  searchQuery = signal('');
  statusFilter = signal<'ALL' | 'Published' | 'Draft'>('ALL');
  toastMessage = signal<string | null>(null);

  posts = signal<AdminBlogPost[]>([]);
  private http = inject(HttpClient);
  cms=inject(CmsService);

  // Modal State
  isModalOpen = signal(false);
  isEditing = signal(false);
  editorTab = signal<'write' | 'preview'>('write');
  editingPost: AdminBlogPost = this.createEmptyPost();

  presetImages = [
    { label: 'Market / Accessories', url: '/assets/blog-market.jpg' },
    { label: 'GaN 100W Charger', url: '/assets/blog-gan.jpg' },
    { label: 'Periwinkle / Construction', url: '/assets/blog-groundbreak.jpg' },
    { label: 'Power Banks / Inspection', url: '/assets/blog-counterfeit.jpg' }
  ];

  constructor() {
    this.load();
  }

  createEmptyPost(): AdminBlogPost {
    return {
      id: 'post-' + Date.now(),
      title: '',
      slug: '',
      tag: 'Industry',
      author: 'Nigson Team',
      date: new Date().toLocaleDateString('en-US', { month: 'short', year: 'numeric' }),
      status: 'Draft',
      excerpt: '',
      image: '/assets/blog-market.jpg',
      body: ''
    };
  }

  load() {
    this.cms.loadBlogs().subscribe({
      next: (rows) => {
        if (Array.isArray(rows) && rows.length > 0) {
          this.posts.set(rows);
          this.syncLocalStorage(rows);
        } else {
          this.loadFromStorageFallback();
        }
      },
      error: (e) => {
        this.loadFromStorageFallback();
        this.showToast(this.cms.message(e));
      }
    });
  }

  private loadFromStorageFallback() {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const raw = localStorage.getItem('nigson.admin.v2');
        if (raw) {
          const parsed = JSON.parse(raw);
          if (Array.isArray(parsed.posts) && parsed.posts.length > 0) {
            this.posts.set(parsed.posts);
          }
        }
      }
    } catch { }
  }

  resetToDefaults(){this.load();}

  syncLocalStorage(posts: AdminBlogPost[]) {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const raw = localStorage.getItem('nigson.admin.v2');
        const parsed = raw ? JSON.parse(raw) : {};
        parsed.posts = posts;
        localStorage.setItem('nigson.admin.v2', JSON.stringify(parsed));
      }
    } catch { }
  }

  saveAll() {
    this.syncLocalStorage(this.posts());
    this.load();
  }

  showToast(message: string) {
    this.toastMessage.set(message);
    setTimeout(() => {
      this.toastMessage.set(null);
    }, 3500);
  }

  filteredPosts = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    const filter = this.statusFilter();

    return this.posts().filter(p => {
      const matchesSearch =
        !q ||
        p.title.toLowerCase().includes(q) ||
        (p.slug || '').toLowerCase().includes(q) ||
        (p.tag || '').toLowerCase().includes(q) ||
        (p.excerpt || '').toLowerCase().includes(q) ||
        (p.body || '').toLowerCase().includes(q);

      const matchesStatus = filter === 'ALL' || p.status === filter;

      return matchesSearch && matchesStatus;
    });
  });

  previewSections = computed(() => {
    return parseMarkdownToSections(this.editingPost.body || '');
  });

  // Modal open handlers
  openCreateModal() {
    this.isEditing.set(false);
    this.editorTab.set('write');
    this.editingPost = this.createEmptyPost();
    this.isModalOpen.set(true);
  }

  openEditModal(post: AdminBlogPost) {
    this.isEditing.set(true);
    this.editorTab.set('write');
    // Clone post to prevent mutating directly before save
    this.editingPost = {
      ...post,
      image: post.image || '/assets/blog-market.jpg',
      excerpt: (post.excerpt || '').trim(),
      body: (post.body || post.excerpt || '').trim()
    };
    this.isModalOpen.set(true);
  }

  closeModal() {
    this.isModalOpen.set(false);
  }

  onTitleChange(title: string) {
    if (!this.isEditing() && title) {
      this.editingPost.slug = title
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-|-$/g, '');
    }
  }

  insertHeading() {
    const snippet = '\n\n### New Section Heading\nSection content goes here.';
    this.editingPost.body = ((this.editingPost.body || '').trimEnd() + snippet).trim();
  }

  insertBullet() {
    const snippet = '\n- Bullet point detail item';
    this.editingPost.body = ((this.editingPost.body || '').trimEnd() + snippet).trim();
  }

  savePost() {
    if (!this.editingPost.title.trim()) return this.showToast('Enter an article title.');
    if (!this.editingPost.slug.trim()) {
      this.editingPost.slug = this.editingPost.title
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-|-$/g, '');
    }

    const cleanPost: AdminBlogPost = {
      ...this.editingPost,
      title: this.editingPost.title.trim(),
      slug: this.editingPost.slug.trim(),
      excerpt: (this.editingPost.excerpt || this.editingPost.title).trim(),
      body: (this.editingPost.body || this.editingPost.excerpt || this.editingPost.title).trim(),
      status: this.cms.canPublish() ? this.editingPost.status : 'Draft'
    };

    // Optimistically update post in signal and local storage so changes never revert
    this.posts.update(ps => {
      const idx = ps.findIndex(p => p.id === cleanPost.id || (cleanPost.slug && p.slug === cleanPost.slug));
      if (idx >= 0) {
        const copy = [...ps];
        copy[idx] = { ...copy[idx], ...cleanPost };
        return copy;
      }
      return [cleanPost, ...ps];
    });
    this.syncLocalStorage(this.posts());

    this.cms.saveBlog(cleanPost).subscribe({
      next: (saved) => {
        if (saved) {
          this.posts.update(ps => {
            const idx = ps.findIndex(p => p.id === saved.id || (saved.slug && p.slug === saved.slug));
            if (idx >= 0) {
              const copy = [...ps];
              copy[idx] = { ...copy[idx], ...saved };
              return copy;
            }
            return [saved, ...ps];
          });
          this.syncLocalStorage(this.posts());
        }
        this.load();
        this.closeModal();
        this.showToast('Article saved.');
      },
      error: (e) => {
        this.closeModal();
        this.showToast('Article updated locally. Server note: ' + this.cms.message(e));
      }
    });
  }

  delete(id:string){const post=this.posts().find(p=>p.id===id) as any;if(!post)return;if(!post.revision||!['PUBLISHED','APPROVED'].includes(post.revision.state))return this.showToast('Draft revisions are retained for audit. Publish or edit this draft.');this.cms.withdrawContent(post.revision).subscribe({next:()=>{this.load();this.showToast('Article withdrawn from the public site.');},error:e=>this.showToast(this.cms.message(e))});}

  toggleStatus(post:AdminBlogPost){const current=post as any;const request=post.status==='Published'?this.cms.withdrawContent(current.revision):this.cms.saveBlog({...post,status:'Published'});request.subscribe({next:()=>{this.load();this.showToast('Publication status updated.');},error:e=>this.showToast(this.cms.message(e))});}

  handleImageError(event: Event) {
    const img = event.target as HTMLImageElement;
    if (img && !img.src.includes('blog-market.jpg')) {
      img.src = '/assets/blog-market.jpg';
    }
  }
}

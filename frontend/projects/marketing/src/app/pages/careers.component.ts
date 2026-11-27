import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CmsService, MarketingService, CareerOpportunity, PageHeaderComponent } from 'shared-ui';
import {
  LucideBriefcase,
  LucideGraduationCap,
  LucideSparkles,
  LucideUsers,
  LucideUpload,
  LucideFileText,
  LucidePaperclip,
  LucideTrash,
  LucideDownload,
  LucideCheckCircle2
} from '@lucide/angular';
import { AttachedCvFile, CareerStorageService, StoredCareerApplication } from './career-storage';

@Component({
  selector: 'app-careers',
  standalone: true,
  imports: [
    CommonModule, 
    PageHeaderComponent,
    FormsModule,
    LucideBriefcase,
    LucideGraduationCap,
    LucideSparkles,
    LucideUsers,
    LucideUpload,
    LucideFileText,
    LucidePaperclip,
    LucideTrash,
    LucideDownload,
    LucideCheckCircle2
  ],
  template: `
    <div class="bg-surface">
      <ng-container *ngIf="hero().visible">
        <lib-page-header [eyebrow]="hero().t('eyebrow')" [title]="hero().t('heading')" [subtitle]="hero().t('body')"></lib-page-header>
      </ng-container>

      <section *ngIf="tracks().visible" class="container-page py-16 grid gap-6 md:grid-cols-2 lg:grid-cols-4">
        <div *ngFor="let t of tracksCards(); let i = index" class="rounded-2xl border border-border p-6">
          <ng-container [ngSwitch]="i % 4">
            <svg lucideBriefcase *ngSwitchCase="0" class="h-7 w-7 text-brand"></svg>
            <svg lucideGraduationCap *ngSwitchCase="1" class="h-7 w-7 text-brand"></svg>
            <svg lucideSparkles *ngSwitchCase="2" class="h-7 w-7 text-brand"></svg>
            <svg lucideUsers *ngSwitchCase="3" class="h-7 w-7 text-brand"></svg>
          </ng-container>
          <h3 class="mt-4 text-lg font-semibold">{{ t.title }}</h3>
          <p class="mt-2 text-sm text-muted-foreground">{{ t.body }}</p>
        </div>
      </section>

      <section *ngIf="openings().visible" class="border-t border-border bg-surface-alt">
        <div class="container-page py-16">
          <h2 class="text-3xl font-semibold mb-8">{{ openings().t('heading') }}</h2>
          <div class="rounded-2xl border border-border bg-background divide-y divide-border overflow-hidden">
            <div *ngFor="let o of openingsCards()" class="flex flex-col md:flex-row md:items-center gap-3 md:gap-8 p-6 hover:bg-surface-alt/60 transition-colors">
              <div class="flex-1">
                <h3 class="font-semibold text-lg">{{ o.title }}</h3>
                <p class="text-sm text-muted-foreground">{{ o.type }} &bull; {{ o.location }}</p>
                
                <!-- Inline Application Form -->
                <div *ngIf="applyingTo() === o.title" class="mt-4 p-5 rounded-2xl border border-border bg-surface-alt/70 shadow-sm">
                  <ng-container *ngIf="appliedTo() === o.title; else formFields">
                    <div class="space-y-3 py-2">
                      <div class="flex items-center gap-2 text-brand">
                        <svg lucideCheckCircle2 class="h-5 w-5 shrink-0"></svg>
                        <p class="text-sm font-semibold">Application submitted successfully. We will be in touch!</p>
                      </div>
                      <div *ngIf="lastSubmittedCv()" class="flex items-center justify-between p-3 rounded-xl border border-hairline bg-background text-xs">
                        <div class="flex items-center gap-2.5 min-w-0">
                          <svg lucideFileText class="h-4 w-4 text-brand shrink-0"></svg>
                          <span class="truncate text-ink">Attached CV: <strong>{{ lastSubmittedCv()?.name }}</strong> ({{ formatFileSize(lastSubmittedCv()?.size) }})</span>
                        </div>
                        <button type="button" (click)="downloadSubmittedFile()" class="inline-flex items-center gap-1 font-semibold text-brand hover:underline shrink-0 ml-2">
                          <svg lucideDownload class="h-3.5 w-3.5"></svg>
                          Download Stored CV
                        </button>
                      </div>
                    </div>
                  </ng-container>
                  <ng-template #formFields>
                    <div class="grid gap-4 sm:grid-cols-2 text-sm">
                      <input type="text" placeholder="Full Name" [(ngModel)]="appForm.name" class="rounded-lg border border-border px-3 py-2 bg-background">
                      <input type="email" placeholder="Email Address" [(ngModel)]="appForm.email" class="rounded-lg border border-border px-3 py-2 bg-background">
                      <input type="tel" placeholder="Phone Number" [(ngModel)]="appForm.phone" class="rounded-lg border border-border px-3 py-2 bg-background">
                      
                      <!-- Resume / CV Attachment Field -->
                      <div class="flex flex-col gap-1.5">
                        <div class="flex items-center justify-between text-xs">
                          <span class="font-medium text-muted-foreground flex items-center gap-1">
                            <svg lucideFileText class="h-3.5 w-3.5 text-brand"></svg>
                            Resume / CV
                          </span>
                          <div class="flex items-center gap-1.5">
                            <button
                              type="button"
                              (click)="cvMode.set('file')"
                              [ngClass]="cvMode() === 'file' ? 'text-brand font-bold underline' : 'text-muted-foreground hover:text-ink'"
                              class="transition-colors cursor-pointer"
                            >
                              Attach Local File
                            </button>
                            <span class="text-muted-foreground">&bull;</span>
                            <button
                              type="button"
                              (click)="cvMode.set('url')"
                              [ngClass]="cvMode() === 'url' ? 'text-brand font-bold underline' : 'text-muted-foreground hover:text-ink'"
                              class="transition-colors cursor-pointer"
                            >
                              Paste URL
                            </button>
                          </div>
                        </div>

                        <!-- File Attachment Mode -->
                        <div *ngIf="cvMode() === 'file'">
                          <div *ngIf="!attachedFile(); else attachedCard">
                            <div
                              (click)="fileInput.click()"
                              (dragover)="onDragOver($event)"
                              (dragleave)="onDragLeave($event)"
                              (drop)="onFileDrop($event)"
                              [ngClass]="isDragging() ? 'border-brand bg-brand/10' : 'border-border hover:border-brand/60 hover:bg-surface-alt'"
                              class="border border-dashed rounded-lg px-3 py-2.5 text-center cursor-pointer transition-all bg-background flex items-center justify-center gap-2 group"
                            >
                              <input
                                #fileInput
                                type="file"
                                (change)="onFileSelected($event)"
                                accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                class="hidden"
                              />
                              <svg lucideUpload class="h-4 w-4 text-brand group-hover:scale-110 transition-transform"></svg>
                              <span class="text-xs text-muted-foreground">
                                {{ uploadingFile() ? 'Reading & storing file...' : 'Attach local CV (PDF, DOCX • max 10MB)' }}
                              </span>
                            </div>
                          </div>

                          <ng-template #attachedCard>
                            <div class="flex items-center justify-between px-3 py-2 rounded-lg border border-brand/30 bg-brand/5 text-xs">
                              <div class="flex items-center gap-2 min-w-0">
                                <svg lucideFileText class="h-4 w-4 text-brand shrink-0"></svg>
                                <div class="truncate">
                                  <span class="font-medium text-ink truncate block">{{ attachedFile()?.name }}</span>
                                  <span class="text-[11px] text-muted-foreground">{{ formatFileSize(attachedFile()?.size) }} &bull; Stored locally</span>
                                </div>
                              </div>
                              <div class="flex items-center gap-1.5 shrink-0 ml-2">
                                <button
                                  type="button"
                                  (click)="downloadAttachedFile()"
                                  title="Download attached CV"
                                  class="p-1 text-brand hover:text-brand-deep rounded hover:bg-brand/10 transition-colors"
                                >
                                  <svg lucideDownload class="h-4 w-4"></svg>
                                </button>
                                <button
                                  type="button"
                                  (click)="removeAttachedFile()"
                                  title="Remove attached file"
                                  class="p-1 text-muted-foreground hover:text-red-600 rounded hover:bg-red-50 transition-colors"
                                >
                                  <svg lucideTrash class="h-4 w-4"></svg>
                                </button>
                              </div>
                            </div>
                          </ng-template>
                        </div>

                        <!-- URL Link Mode -->
                        <div *ngIf="cvMode() === 'url'">
                          <div class="relative">
                            <input
                              type="text"
                              placeholder="Link to Resume/CV (URL)"
                              [(ngModel)]="appForm.cv"
                              class="w-full rounded-lg border border-border px-3 py-2 bg-background text-sm pr-9"
                            />
                            <button
                              type="button"
                              (click)="cvMode.set('file'); fileInput2.click()"
                              title="Attach local file instead"
                              class="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-brand p-1 cursor-pointer"
                            >
                              <svg lucidePaperclip class="h-4 w-4"></svg>
                            </button>
                            <input
                              #fileInput2
                              type="file"
                              (change)="onFileSelected($event)"
                              accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                              class="hidden"
                            />
                          </div>
                        </div>

                        <p *ngIf="fileError()" class="text-xs text-red-600 mt-0.5" role="alert">{{ fileError() }}</p>
                      </div>

                      <textarea placeholder="Cover Letter (Optional)" [(ngModel)]="appForm.coverLetter" class="sm:col-span-2 rounded-lg border border-border px-3 py-2 bg-background" rows="3"></textarea>
                    </div>
                    <div class="mt-4 flex gap-2">
                      <button (click)="submitApplication(o.title, o.id)" [disabled]="submitting() || uploadingFile()" class="rounded-full bg-brand px-4 py-1.5 text-xs font-semibold text-white hover:bg-brand-deep disabled:opacity-50">
                        {{ submitting() ? 'Submitting...' : 'Submit Application' }}
                      </button>
                      <button (click)="cancelApplication()" class="rounded-full bg-surface px-4 py-1.5 text-xs font-semibold text-ink border border-border hover:bg-surface-alt">Cancel</button>
                    </div>
                  </ng-template>
                </div>
              </div>
              <button
                *ngIf="applyingTo() !== o.title && appliedTo() !== o.title"
                (click)="applyingTo.set(o.title)"
                class="inline-flex items-center justify-center rounded-full bg-brand text-brand-foreground px-5 py-2 text-sm font-semibold hover:bg-brand-deep transition-colors"
              >
                {{ openings().t('btnLabel') }}
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  `
})
export class CareersComponent implements OnInit {
  marketingSvc = inject(MarketingService);
  cms = inject(CmsService);
  careerStorage = new CareerStorageService();

  submitting = signal(false);
  submitError = signal<string | null>(null);

  // CV Attachment & Storage state
  cvMode = signal<'file' | 'url'>('file');
  attachedFile = signal<AttachedCvFile | null>(null);
  uploadingFile = signal(false);
  fileError = signal<string | null>(null);
  isDragging = signal(false);
  lastSubmittedCv = signal<AttachedCvFile | null>(null);

  applyingTo = signal<string | null>(null);
  appliedTo = signal<string | null>(null);

  appForm = { name: '', email: '', phone: '', cv: '', coverLetter: '' };

  page = this.cms.getPageSections('careers');

  hero = computed(() => this.page.get('hero'));
  tracks = computed(() => this.page.get('tracks'));
  openings = computed(() => this.page.get('openings'));

  tracksCards = computed(() => this.tracks().cards((c: any) => ({ title: c['title'], body: c['body'] })));
  backendCareers = this.marketingSvc.careers;
  openingsCards = computed(() => {
    const apiItems = this.backendCareers();
    if (apiItems && apiItems.length > 0) {
      return apiItems.map(item => ({
        id: item.id,
        title: item.title,
        type: item.kind || 'Full-time',
        location: 'Lagos, Nigeria'
      }));
    }
    return this.openings().cards((c: any) => ({
      id: c['id'] || 'opp_' + Math.random().toString(36).substring(2, 8),
      title: c['title'],
      type: c['type'],
      location: c['location']
    }));
  });

  ngOnInit() {
    this.marketingSvc.loadCareers().subscribe();
  }

  cancelApplication() {
    this.applyingTo.set(null);
    this.attachedFile.set(null);
    this.fileError.set(null);
  }

  formatFileSize(bytes?: number): string {
    if (!bytes || bytes <= 0) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  async handleFile(file: File) {
    this.fileError.set(null);
    const validExtensions = ['.pdf', '.doc', '.docx'];
    const lowerName = file.name.toLowerCase();
    const hasValidExt = validExtensions.some(ext => lowerName.endsWith(ext));
    const validMimes = [
      'application/pdf',
      'application/msword',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    ];
    const hasValidMime = file.type ? validMimes.includes(file.type) : true;

    if (!hasValidExt || !hasValidMime) {
      this.fileError.set('Please select a PDF, DOC, or DOCX document.');
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      this.fileError.set('File exceeds maximum size of 10 MB.');
      return;
    }

    this.uploadingFile.set(true);
    try {
      const stored = await this.careerStorage.storeCvFile(file);
      this.attachedFile.set(stored);
      this.cvMode.set('file');
      this.fileError.set(null);
    } catch (err) {
      console.error('[CareersComponent] File storage failed:', err);
      this.fileError.set('Could not store attached file. Please try again.');
    } finally {
      this.uploadingFile.set(false);
    }
  }

  async onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input?.files?.[0];
    if (file) {
      await this.handleFile(file);
    }
    if (input) input.value = '';
  }

  removeAttachedFile() {
    this.attachedFile.set(null);
    this.fileError.set(null);
  }

  downloadAttachedFile() {
    const file = this.attachedFile();
    if (file) {
      this.careerStorage.downloadFile(file);
    }
  }

  downloadSubmittedFile() {
    const file = this.lastSubmittedCv();
    if (file) {
      this.careerStorage.downloadFile(file);
    }
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(true);
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(false);
  }

  async onFileDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      await this.handleFile(file);
    }
  }

  submitApplication(jobTitle: string, jobId?: string) {
    const hasFile = !!this.attachedFile();
    const hasUrl = !!this.appForm.cv?.trim();

    if (!this.appForm.name?.trim() || !this.appForm.email?.trim()) {
      return;
    }

    if (!hasFile && !hasUrl) {
      this.fileError.set('Please attach your local Resume/CV file or provide a link.');
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);

    const currentAttached = this.attachedFile();
    const cvPayloadUrl = currentAttached
      ? `stored://${currentAttached.name}`
      : this.appForm.cv.trim();

    const targetId = jobId || 'opp_default';

    // Store in browser application registry (IndexedDB + localStorage)
    const appRecord: StoredCareerApplication = {
      id: 'app_' + Date.now() + '_' + Math.random().toString(36).substring(2, 8),
      opportunityId: targetId,
      jobTitle,
      applicantName: this.appForm.name.trim(),
      applicantEmail: this.appForm.email.trim(),
      applicantPhone: this.appForm.phone.trim(),
      coverLetter: this.appForm.coverLetter?.trim(),
      cvType: currentAttached ? 'file' : 'url',
      cvUrl: cvPayloadUrl,
      cvFile: currentAttached || undefined,
      submittedAt: new Date().toISOString()
    };
    this.careerStorage.saveApplication(appRecord);

    this.marketingSvc.submitCareerApplication(targetId, {
      name: this.appForm.name.trim(),
      email: this.appForm.email.trim(),
      answers: {
        phone: this.appForm.phone,
        coverLetter: this.appForm.coverLetter,
        cvUrl: cvPayloadUrl,
        positionTitle: jobTitle
      }
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.lastSubmittedCv.set(currentAttached);
        this.appliedTo.set(jobTitle);
        this.attachedFile.set(null);
        this.appForm = { name: '', email: '', phone: '', cv: '', coverLetter: '' };
      },
      error: () => {
        this.submitting.set(false);
        this.lastSubmittedCv.set(currentAttached);
        this.appliedTo.set(jobTitle);
        this.attachedFile.set(null);
        this.appForm = { name: '', email: '', phone: '', cv: '', coverLetter: '' };
      }
    });
  }
}

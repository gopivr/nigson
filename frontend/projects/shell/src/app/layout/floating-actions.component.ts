import { Component, HostListener, ViewChild, ElementRef, signal, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { LucideMessageCircle, LucideBot, LucideX, LucideSend, LucideArrowUp } from '@lucide/angular';

type Msg = { role: 'user' | 'bot'; text: string };
type Conversation = { id: string; anonymousToken: string; version: number };
const QUICK = ['Product catalog', 'Wholesale pricing', 'Become distributor', 'Contact us'];

@Component({
  selector: 'app-floating-actions',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideMessageCircle, LucideBot, LucideX, LucideSend, LucideArrowUp],
  template: `
    <button (click)="scrollToTop()" aria-label="Back to Top" title="Back to top"
      class="fixed bottom-6 right-6 z-40 grid h-12 w-12 place-items-center rounded-full bg-white border border-hairline text-ink shadow-[0_10px_30px_-5px_rgba(0,0,0,0.2)] hover:bg-brand hover:text-white hover:border-brand transition-all duration-300"
      [ngClass]="showTopBtn() ? 'opacity-100 translate-y-0 pointer-events-auto' : 'opacity-0 translate-y-4 pointer-events-none'">
      <svg lucideArrowUp class="h-5 w-5"></svg>
    </button>

    <button (click)="aiOpen.set(!aiOpen())" aria-label="AI Assistant" [attr.title]="aiOpen() ? 'Close Assistant' : 'Chat with Nigson Assistant'"
      class="fixed bottom-24 right-6 z-40 grid h-14 w-14 place-items-center rounded-full bg-ink text-white shadow-[0_20px_50px_-15px_rgba(0,0,0,0.5)] hover:bg-brand transition-colors">
      <svg *ngIf="!aiOpen()" lucideMessageCircle class="h-6 w-6"></svg>
      <svg *ngIf="aiOpen()" lucideX class="h-6 w-6"></svg>
    </button>

    <div *ngIf="aiOpen()" class="fixed bottom-44 right-6 z-40 w-[340px] rounded-2xl bg-white border border-hairline shadow-[0_30px_80px_-20px_rgba(0,0,0,0.35)] animate-fade-up overflow-hidden">
      <div class="bg-ink text-white px-5 py-4 flex items-center justify-between">
        <div class="flex items-center gap-2.5">
          <span class="grid h-8 w-8 place-items-center rounded-full bg-brand"><svg lucideBot class="h-4 w-4"></svg></span>
          <div><p class="text-sm font-semibold">Nigson Assistant</p><p class="text-[11px] text-white/60">Answers use the configured Nigson AI service</p></div>
        </div>
        <button type="button" (click)="aiOpen.set(false)" class="text-white/60 hover:text-white transition-colors" title="Close Assistant" aria-label="Close Assistant">
          <svg lucideX class="h-5 w-5"></svg>
        </button>
      </div>
      <div #scrollRef class="p-4 space-y-3 max-h-[280px] overflow-y-auto">
        <div *ngFor="let m of msgs()" class="rounded-2xl px-4 py-2.5 text-sm max-w-[85%]"
          [ngClass]="m.role === 'bot' ? 'rounded-tl-sm bg-surface-alt text-ink' : 'ml-auto rounded-tr-sm bg-brand text-white'">{{ m.text }}</div>
        <div *ngIf="msgs().length === 0 && !busy()" class="grid grid-cols-2 gap-2 pt-1">
          <button *ngFor="let q of quick" (click)="send(q)" class="text-xs font-medium text-left rounded-lg border border-hairline px-3 py-2 hover:border-brand hover:text-brand">{{ q }}</button>
        </div>
        <div *ngIf="busy()" class="text-xs text-muted-ink">Waiting for the AI service…</div>
        <button *ngIf="uncertain()" type="button" (click)="startNewConversation()" class="text-xs font-semibold text-brand underline">Start a new conversation</button>
      </div>
      <form (submit)="onSubmit($event)" class="border-t border-hairline p-3 flex gap-2">
        <input type="text" [(ngModel)]="input" name="chatInput" [disabled]="busy() || uncertain()"
          placeholder="Type your question..." class="flex-1 rounded-full border border-hairline bg-white px-4 py-2 text-sm focus:outline-none focus:border-brand" />
        <button type="submit" [disabled]="busy() || uncertain()" title="Send message" aria-label="Send message" class="grid h-9 w-9 place-items-center rounded-full bg-brand text-white disabled:opacity-50">
          <svg lucideSend class="h-4 w-4"></svg>
        </button>
      </form>
    </div>
  `
})
export class FloatingActionsComponent {
  private http = inject(HttpClient);
  aiOpen = signal(false);
  showTopBtn = signal(false);
  msgs = signal<Msg[]>([]);
  busy = signal(false);
  uncertain = signal(false);
  input = '';
  quick = QUICK;
  private conversation: Conversation | null = null;
  @ViewChild('scrollRef') scrollRef?: ElementRef<HTMLDivElement>;

  constructor() {
    effect(() => {
      this.msgs();
      this.aiOpen();
      setTimeout(() => this.scrollRef?.nativeElement?.scrollTo({ top: this.scrollRef.nativeElement.scrollHeight, behavior: 'smooth' }), 50);
    });
  }

  @HostListener('window:scroll') onScroll() { this.showTopBtn.set(window.scrollY > 250); }
  scrollToTop() { window.scrollTo({ top: 0, behavior: 'smooth' }); }
  onSubmit(event: Event) { event.preventDefault(); this.send(this.input); }

  startNewConversation(): void {
    this.conversation = null;
    this.uncertain.set(false);
    this.msgs.set([]);
  }

  async send(text: string): Promise<void> {
    const message = text.trim();
    if (!message || this.busy() || this.uncertain()) return;
    this.msgs.update(items => [...items, { role: 'user', text: message }]);
    this.input = '';
    this.busy.set(true);
    let messageRequestStarted = false;

    try {
      if (!this.conversation) this.conversation = await this.createConversation();
      const current = this.conversation;
      const headers = new HttpHeaders({
        'X-Conversation-Token': current.anonymousToken,
        'If-Match': `"${current.version}"`
      });
      messageRequestStarted = true;
      const response = await firstValueFrom(this.http.post(
        `/api/v1/ai/conversations/${current.id}/messages`,
        { message, clientRequestId: crypto.randomUUID() },
        { headers, observe: 'response', responseType: 'text' }
      ));
      const answer = this.readCompletedAnswer(response.body || '');
      const etag = response.headers.get('ETag');
      if (etag && /^"[0-9]+"$/.test(etag)) current.version = Number(etag.slice(1, -1));
      else current.version += 1;
      this.msgs.update(items => [...items, { role: 'bot', text: answer }]);
    } catch (error) {
      const detail = this.problemText(error);
      this.msgs.update(items => [...items, { role: 'bot', text: detail }]);
      if (messageRequestStarted && (!(error instanceof HttpErrorResponse) || error.status === 0 || error.status >= 500 || error.status === 409)) this.uncertain.set(true);
    } finally {
      this.busy.set(false);
    }
  }

  private async createConversation(): Promise<Conversation> {
    const response = await firstValueFrom(this.http.post<{ id: string; anonymousToken: string }>(
      '/api/v1/ai/conversations', { market: 'NG', locale: 'en' },
      { headers: new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID() }) }
    ));
    if (!response.id || !response.anonymousToken) throw new Error('AI conversation capability was not returned by the service.');
    return { id: response.id, anonymousToken: response.anonymousToken, version: 0 };
  }

  private readCompletedAnswer(stream: string): string {
    for (const block of stream.split(/\r?\n\r?\n/)) {
      const event = block.match(/^event:\s*(.+)$/m)?.[1];
      const data = block.match(/^data:\s*(.+)$/m)?.[1];
      if (event === 'completed' && data) {
        const payload = JSON.parse(data);
        if (typeof payload?.answer?.answer === 'string') return payload.answer.answer;
      }
    }
    throw new Error('The AI response ended without a completed answer. Start a new conversation before retrying.');
  }

  private problemText(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      let problem: any = error.error;
      if (typeof problem === 'string') {
        try { problem = JSON.parse(problem); } catch { /* response was not a problem JSON body */ }
      }
      if (typeof problem?.detail === 'string') return problem.detail;
      if (typeof problem?.title === 'string') return problem.title;
      if (error.status === 0) return 'The Java AI service could not be reached. Check that the AI profile is running.';
    }
    return error instanceof Error ? error.message : 'The AI service could not complete this request.';
  }
}

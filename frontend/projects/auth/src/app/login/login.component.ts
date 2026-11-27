import {loginDestination, safeReturnPath} from 'shared-ui';
import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideExternalLink, LucideLoader2 } from '@lucide/angular';
import { AuthShellComponent } from '../auth-shell/auth-shell.component';
import { SessionService } from 'shared-ui';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, RouterLink, AuthShellComponent, LucideExternalLink, LucideLoader2],
  template: `
    <app-auth-shell eyebrow="Authentication" title="Sign in to Nigson"
      subtitle="Continue to your secure Nigson account." [hasFooter]="true">
      <div class="flex flex-col gap-5 py-2">
        <div *ngIf="!session.sessionLoaded()" class="flex items-center justify-center gap-2 py-4 text-sm text-muted-ink">
          <svg lucideLoader2 class="h-4 w-4 animate-spin"></svg><span>Checking your backend session…</span>
        </div>
        <div *ngIf="error()" role="alert" class="rounded-2xl border border-red-200 bg-red-50 p-3.5 text-sm text-red-700">{{ error() }}</div>
        <button type="button" (click)="signIn()" [disabled]="redirecting()"
          class="w-full inline-flex items-center justify-center gap-2 rounded-full bg-brand text-white py-3 text-sm font-semibold hover:bg-brand-deep disabled:opacity-70">
          <svg *ngIf="redirecting()" lucideLoader2 class="h-4 w-4 animate-spin"></svg>
          <svg *ngIf="!redirecting()" lucideExternalLink class="h-4 w-4"></svg>
          {{ redirecting() ? 'Opening secure sign-in…' : 'Sign in securely' }}
        </button>
        <p class="text-xs leading-5 text-muted-ink">Your session stays on the Java backend. This page does not store passwords or identity tokens. If sign-in returns an authentication error, start Commerce with the local BFF security profile configured.</p>
      </div>
      <div footer>New to Nigson? <a routerLink="/auth/signup" class="font-semibold text-brand hover:underline">Create an account</a></div>
    </app-auth-shell>
  `
})
export class LoginComponent implements OnInit {
  readonly session = inject(SessionService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  readonly redirecting = signal(false);
  readonly error = signal<string | null>(null);
  readonly isLocal = signal(typeof window !== 'undefined' && (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'));

  ngOnInit(): void {
    this.session.loadSession().subscribe(() => {
      if (this.session.authenticated()) this.router.navigateByUrl(this.getReturnUrl());
    });
  }

  private requestedReturn():string|undefined {
    return safeReturnPath(this.route.snapshot.queryParams['returnTo'] || this.route.snapshot.queryParams['redirect']);
  }
  private getReturnUrl():string {
    return this.session.consumeLoginReturn() ?? loginDestination(this.session.roles(),this.requestedReturn());
  }


  signIn(): void {
    this.redirecting.set(true);
    this.session.signIn(this.requestedReturn());
  }
}

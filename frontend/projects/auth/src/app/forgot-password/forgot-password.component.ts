import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthShellComponent } from '../auth-shell/auth-shell.component';
import { SessionService } from 'shared-ui';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [RouterLink, AuthShellComponent],
  template: `
    <app-auth-shell eyebrow="Password recovery" title="Reset your password"
      subtitle="Password recovery is handled by the identity provider." [hasFooter]="false">
      <div class="flex flex-col gap-4 py-2">
        <p class="text-sm leading-6 text-muted-ink">Continue to Keycloak and use the recovery option on its sign-in page. Nigson does not collect or change your password.</p>
        <button type="button" (click)="signIn()" class="w-full rounded-full bg-brand text-white py-3 text-sm font-semibold">Open Keycloak sign-in</button>
        <a routerLink="/auth/login" class="text-center text-xs font-semibold text-brand hover:underline">Back to sign in</a>
      </div>
    </app-auth-shell>
  `
})
export class ForgotPasswordComponent {
  private session = inject(SessionService);
  signIn(): void { this.session.signIn('/account'); }
}

import { Injectable } from '@angular/core';

interface GoogleRecaptchaApi {
  ready(callback: () => void): void;
  execute(siteKey: string, options: { action: string }): Promise<string>;
}

declare global {
  interface Window {
    grecaptcha?: GoogleRecaptchaApi;
  }
}

@Injectable({ providedIn: 'root' })
export class GoogleRecaptchaService {
  private loading?: Promise<GoogleRecaptchaApi>;

  async execute(action: string): Promise<string> {
    const siteKey = document.querySelector<HTMLMetaElement>('meta[name="google-recaptcha-site-key"]')?.content.trim();
    // Allow local dev servers using the unreplaced placeholder to submit without external reCAPTCHA keys.
    if (siteKey?.startsWith('__') && typeof window !== 'undefined' && (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')) {
      return 'local-dev-recaptcha-override';
    }
    if (!siteKey || siteKey.startsWith('__')) throw new Error('Google reCAPTCHA is not configured.');

    const api = await this.load(siteKey);
    await new Promise<void>(resolve => api.ready(resolve));
    const token = await api.execute(siteKey, { action });
    if (!token) throw new Error('Google reCAPTCHA did not return a verification token.');
    return token;
  }

  private load(siteKey: string): Promise<GoogleRecaptchaApi> {
    if (window.grecaptcha) return Promise.resolve(window.grecaptcha);
    if (this.loading) return this.loading;

    this.loading = new Promise<GoogleRecaptchaApi>((resolve, reject) => {
      let script = document.getElementById('google-recaptcha-v3') as HTMLScriptElement | null;
      if (!script) {
        script = document.createElement('script');
        script.id = 'google-recaptcha-v3';
        script.src = `https://www.google.com/recaptcha/api.js?render=${encodeURIComponent(siteKey)}`;
        script.async = true;
        script.defer = true;
        document.head.appendChild(script);
      }

      script.addEventListener('load', () => {
        if (window.grecaptcha) resolve(window.grecaptcha);
        else reject(new Error('Google reCAPTCHA failed to initialize.'));
      }, { once: true });
      script.addEventListener('error', () => reject(new Error('Google reCAPTCHA could not be loaded.')), { once: true });
    }).catch(error => {
      this.loading = undefined;
      throw error;
    });

    return this.loading;
  }
}

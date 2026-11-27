import { afterEach, describe, expect, it, vi } from 'vitest';
import { GoogleRecaptchaService } from './google-recaptcha.service';

describe('GoogleRecaptchaService', () => {
  afterEach(() => {
    document.querySelector('meta[name="google-recaptcha-site-key"]')?.remove();
    delete window.grecaptcha;
  });

  it('executes the requested action with the configured public site key', async () => {
    const meta = document.createElement('meta');
    meta.name = 'google-recaptcha-site-key';
    meta.content = 'configured-public-site-key';
    document.head.appendChild(meta);
    const execute = vi.fn().mockResolvedValue('verified-token');
    window.grecaptcha = { ready: callback => callback(), execute };

    await expect(new GoogleRecaptchaService().execute('lead_submit')).resolves.toBe('verified-token');
    expect(execute).toHaveBeenCalledWith('configured-public-site-key', { action: 'lead_submit' });
  });

  it('fails closed without configuration and does not create a token', async () => {
    await expect(new GoogleRecaptchaService().execute('lead_submit'))
      .rejects.toThrow('Google reCAPTCHA is not configured.');
    expect(window.grecaptcha).toBeUndefined();
  });
});

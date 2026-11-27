import '@angular/compiler';
import { Injector } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, Subject } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { MarketingService, BackendDistributorApplication } from './marketing.service';
import { SessionService } from './session.service';
import { GoogleRecaptchaService } from './google-recaptcha.service';

function setup() {
  const response = new Subject<BackendDistributorApplication>();
  const enquiryResponse = new Subject<any>();
  const http = { post: vi.fn((url: string) => url === '/api/v1/enquiries' ? enquiryResponse.asObservable() : response.asObservable()) };
  const recaptcha = { execute: vi.fn(() => Promise.resolve('verified-google-token')) };
  const injector = Injector.create({ providers: [
    { provide: HttpClient, useValue: http },
    { provide: SessionService, useValue: {} },
    { provide: GoogleRecaptchaService, useValue: recaptcha },
    { provide: MarketingService, useFactory: () => new MarketingService() }
  ] });
  return { service: injector.get(MarketingService), http, response, enquiryResponse, recaptcha };
}
const payload = { organizationName: 'Local test company', countryCode: 'NG', documentIds: ['document-id'] };

describe('Distributor submission outcomes', () => {
  it('keeps the request key for safe replay and completes only with the server response', () => {
    const { service, http, response } = setup();
    const next = vi.fn();
    service.submitDistributorApplication(payload, 'stable-key').subscribe(next);
    expect(service.submittingDistributor()).toBe(true);
    expect(next).not.toHaveBeenCalled();
    expect(http.post).toHaveBeenCalledWith('/api/v1/distributor-applications', expect.objectContaining(payload), { headers: { 'Idempotency-Key': 'stable-key' } });
    const accepted: BackendDistributorApplication = { id: 'application-id', state: 'SUBMITTED', submittedAt: '2026-11-03T00:00:00Z', version: 0 };
    response.next(accepted); response.complete();
    expect(next).toHaveBeenCalledWith(accepted);
    expect(service.submittingDistributor()).toBe(false);
  });
  it('surfaces the backend failure instead of silently converting it to null', () => {
    const { service, response } = setup();
    const failure = { status: 422, error: { code: 'BUSINESS_RULE_VIOLATION' } };
    const next = vi.fn(), error = vi.fn();
    service.submitDistributorApplication(payload, 'stable-key').subscribe({ next, error });
    response.error(failure);
    expect(next).not.toHaveBeenCalled();
    expect(error).toHaveBeenCalledWith(failure);
    expect(service.submittingDistributor()).toBe(false);
  });
  it('unlocks the form after a cancelled request', () => {
    const { service } = setup();
    const request = service.submitDistributorApplication(payload, 'stable-key').subscribe();
    request.unsubscribe();
    expect(service.submittingDistributor()).toBe(false);
  });

  it('uses a real reCAPTCHA token for lead intake and sends no synthetic fallback', async () => {
    const { service, http, enquiryResponse, recaptcha } = setup();
    const next = vi.fn();
    service.submitEnquiry({
      type: 'CONTACT',
      name: 'Example User',
      email: 'user@example.com',
      subject: 'Real enquiry',
      message: 'Please contact me.'
    }).subscribe(next);

    await Promise.resolve();
    expect(recaptcha.execute).toHaveBeenCalledWith('lead_submit');
    expect(http.post).toHaveBeenCalledWith('/api/v1/enquiries', expect.objectContaining({
      antiSpamToken: 'verified-google-token'
    }), expect.anything());

    enquiryResponse.next({ id: 'lead-id' });
    enquiryResponse.complete();
    expect(next).toHaveBeenCalledWith(expect.objectContaining({ success: true, status: 201 }));
  });

  it('does not send lead intake when reCAPTCHA cannot provide a token', async () => {
    const { service, http, recaptcha } = setup();
    vi.mocked(recaptcha.execute).mockRejectedValue(new Error('Google reCAPTCHA is not configured.'));
    const result = await firstValueFrom(service.submitEnquiry({
      type: 'CONTACT',
      name: 'Example User',
      email: 'user@example.com',
      subject: 'Real enquiry',
      message: 'Please contact me.'
    }));

    expect(http.post).not.toHaveBeenCalled();
    expect(result).toEqual(expect.objectContaining({
      success: false,
      error: 'Google reCAPTCHA is not configured.'
    }));
  });
});

it('sends explicit wholesale type and business details without creating a contact lead',()=>{const {service,http,response}=setup();service.submitDistributorApplication({...payload,partnerType:'WHOLESALE',businessDetails:'Synthetic business profile'},'wholesale-key').subscribe();expect(http.post).toHaveBeenCalledWith('/api/v1/distributor-applications',expect.objectContaining({partnerType:'WHOLESALE',businessDetails:'Synthetic business profile'}),{headers:{'Idempotency-Key':'wholesale-key'}});response.complete();});

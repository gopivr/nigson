import '@angular/compiler';
import { HttpContext } from '@angular/common/http';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { describe, it, expect, vi } from 'vitest';
import { AccountService } from './account.service';
import { LOCAL_API_FAILURE_CODES } from './api-errors.service';

function service() {
  const instance: any = Object.create(AccountService.prototype);
  instance.http = { get: vi.fn(), post: vi.fn() };
  instance.session = { authenticated: () => true };
  instance.referral = signal(null);
  instance.referralError = signal(null);
  instance.loadingReferral = signal(false);
  return instance;
}

describe('AccountService referral code', () => {
  it('treats the documented "no code yet" 404 as a local, non-error result', () => {
    const s = service();
    s.http.get.mockReturnValue(throwError(() => ({ status: 404, error: { code: 'NOT_FOUND' } })));
    let resolved: any = 'unset';
    s.loadReferral().subscribe((value: any) => resolved = value);

    const [url, options] = s.http.get.mock.calls[0];
    expect(url).toBe('/api/v1/me/referral-code');
    expect(options.context).toBeInstanceOf(HttpContext);
    expect(options.context.get(LOCAL_API_FAILURE_CODES)).toContain('404:NOT_FOUND');
    // The profile card offers creation instead; no global failure or inline error.
    expect(resolved).toBeNull();
    expect(s.referral()).toBeNull();
    expect(s.referralError()).toBeNull();
    expect(s.loadingReferral()).toBe(false);
  });

  it('creates the code with an idempotency key and stores the returned link', () => {
    const s = service();
    const referral = { code: 'ABCDEF0123456789', link: 'https://shop.example/referrals/ABCDEF0123456789', qualifiedReferralCount: 0 };
    s.http.post.mockReturnValue(of(referral));

    let resolved: any;
    s.createReferral().subscribe((value: any) => resolved = value);

    const [url, body, options] = s.http.post.mock.calls[0];
    expect(url).toBe('/api/v1/me/referral-code');
    expect(body).toEqual({});
    expect(options.headers.get('Idempotency-Key')).toMatch(/[0-9a-f-]{36}/);
    expect(resolved).toEqual(referral);
    expect(s.referral()).toEqual(referral);
    expect(s.referralError()).toBeNull();
    expect(s.loadingReferral()).toBe(false);
  });

  it('explains a disabled referral configuration instead of inventing a code', () => {
    const s = service();
    s.http.post.mockReturnValue(throwError(() => ({ status: 422, error: { code: 'FEATURE_DISABLED' } })));

    let resolved: any = 'unset';
    s.createReferral().subscribe((value: any) => resolved = value);

    expect(resolved).toBeNull();
    expect(s.referral()).toBeNull();
    expect(s.referralError()).toBe('Referral links are not configured for this store yet.');
    expect(s.loadingReferral()).toBe(false);
  });
});

describe('Profile update failures',()=>{
 it('propagates the backend rejection without replacing the current profile',()=>{
  const s:any=Object.create(AccountService.prototype);const before={id:'account',displayName:'Before',phone:null};s.profile=signal(before);s.shop={user:signal({fullName:'Before'})};s.http={patch:vi.fn(()=>throwError(()=>({status:412,error:{code:'VERSION_MISMATCH'}})))};const next=vi.fn(),error=vi.fn();s.updateProfile({phone:'+2348012345678'}).subscribe({next,error});expect(next).not.toHaveBeenCalled();expect(error).toHaveBeenCalledOnce();expect(error.mock.calls[0][0].status).toBe(412);expect(s.profile()).toEqual(before);
 });
 it('uses the acknowledged phone when updating shared profile state',()=>{
  const s:any=Object.create(AccountService.prototype);s.profile=signal(null);s.shop={user:signal({fullName:'Before'})};s.http={patch:vi.fn(()=>of({id:'account',displayName:'After',phone:'+2348012345678'}))};s.updateProfile({phone:'+2348012345678'}).subscribe();expect(s.shop.user().phone).toBe('+2348012345678');
 });
});

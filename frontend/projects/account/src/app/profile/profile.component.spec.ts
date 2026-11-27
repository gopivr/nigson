import '@angular/compiler';
import { signal } from '@angular/core';
import { of, Subject } from 'rxjs';
import { describe, it, expect, vi } from 'vitest';
import { ProfileComponent } from './profile.component';

function component(create: () => any) {
  const instance: any = Object.create(ProfileComponent.prototype);
  instance.creatingReferral = signal(false);
  instance.copied = signal(false);
  instance.accountSvc = { createReferral: vi.fn(create) };
  return instance;
}

describe('Profile referral card', () => {
  it('creates the referral code and clears the pending flag', () => {
    const c = component(() => of({ code: 'ABCDEF0123456789', link: 'https://shop.example/referrals/ABCDEF0123456789', qualifiedReferralCount: 0 }));
    c.createReferral();
    expect(c.accountSvc.createReferral).toHaveBeenCalledTimes(1);
    expect(c.creatingReferral()).toBe(false);
  });

  it('does not request a second code while creation is in flight', () => {
    const pending = new Subject<any>();
    const c = component(() => pending.asObservable());
    c.createReferral();
    c.createReferral();
    expect(c.accountSvc.createReferral).toHaveBeenCalledTimes(1);
    expect(c.creatingReferral()).toBe(true);
    pending.next({ code: 'ABCDEF0123456789', link: 'x', qualifiedReferralCount: 0 });
    pending.complete();
    expect(c.creatingReferral()).toBe(false);
  });
});

function phoneProfile(response:any) {
 const c:any=Object.create(ProfileComponent.prototype);
 c.form=signal({fullName:'Test Customer',email:'test@example.invalid',phone:'08012345678'});
 c.saving=signal(false);c.saveSuccess=signal(false);c.saveError=signal(null);c.editing=signal(true);
 c.accountSvc={updateProfile:vi.fn(()=>response)};return c;
}
describe('Profile phone saving',()=>{
 it('normalizes phone and shows success only after confirmed save',()=>{
  vi.useFakeTimers();const c=phoneProfile(of({displayName:'Test Customer',email:'test@example.invalid',phone:'+2348012345678'}));c.save();expect(c.accountSvc.updateProfile).toHaveBeenCalledWith({displayName:'Test Customer',phone:'+2348012345678'});expect(c.saveSuccess()).toBe(true);expect(c.editing()).toBe(false);vi.runAllTimers();vi.useRealTimers();
 });
 it('blocks bare prefix and keeps the form editable',()=>{const c=phoneProfile(of(null));c.form().phone='+234';c.save();expect(c.accountSvc.updateProfile).not.toHaveBeenCalled();expect(c.saveError()).toContain('complete phone');expect(c.editing()).toBe(true);});
 it('clears an optional phone with null rather than storing a country prefix',()=>{const c=phoneProfile(of(null));c.form().phone='';c.save();expect(c.accountSvc.updateProfile).toHaveBeenCalledWith({displayName:'Test Customer',phone:null});});
 it('does not claim success for an unconfirmed response',()=>{const c=phoneProfile(of(null));c.save();expect(c.saveSuccess()).toBe(false);expect(c.editing()).toBe(true);expect(c.saveError()).toContain('not confirmed');});
 it('handles failure and prevents duplicate in-flight submissions',()=>{const pending=new Subject();const c=phoneProfile(pending);c.save();c.save();expect(c.accountSvc.updateProfile).toHaveBeenCalledOnce();pending.error({error:{detail:'Profile changed. Reload before saving.'}});expect(c.saving()).toBe(false);expect(c.editing()).toBe(true);expect(c.saveSuccess()).toBe(false);expect(c.saveError()).toContain('Reload');});
});

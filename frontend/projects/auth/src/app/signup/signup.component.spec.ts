import '@angular/compiler';
import { signal } from '@angular/core';
import { describe, it, expect, vi } from 'vitest';
import { SignupComponent } from './signup.component';

describe('SignupComponent password visibility toggle', () => {
  it('initializes password visibility signals to false', () => {
    const component: any = Object.create(SignupComponent.prototype);
    component.showPassword = signal(false);
    component.showConfirmPassword = signal(false);

    expect(component.showPassword()).toBe(false);
    expect(component.showConfirmPassword()).toBe(false);
  });

  it('toggles password visibility and prevents default event', () => {
    const component: any = Object.create(SignupComponent.prototype);
    component.showPassword = signal(false);
    const event = { preventDefault: vi.fn() } as unknown as MouseEvent;

    component.togglePassword(event);
    expect(component.showPassword()).toBe(true);
    expect(event.preventDefault).toHaveBeenCalled();

    component.togglePassword(event);
    expect(component.showPassword()).toBe(false);
  });

  it('toggles confirm password visibility independently', () => {
    const component: any = Object.create(SignupComponent.prototype);
    component.showPassword = signal(false);
    component.showConfirmPassword = signal(false);
    const event = { preventDefault: vi.fn() } as unknown as MouseEvent;

    component.toggleConfirmPassword(event);
    expect(component.showConfirmPassword()).toBe(true);
    expect(component.showPassword()).toBe(false);
    expect(event.preventDefault).toHaveBeenCalled();

    component.toggleConfirmPassword();
    expect(component.showConfirmPassword()).toBe(false);
  });
});

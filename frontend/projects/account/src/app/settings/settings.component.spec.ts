import '@angular/compiler';
import { describe, it, expect, vi } from 'vitest';
import { SettingsComponent } from './settings.component';

describe('Settings security card', () => {
  it('delegates password change to identity through the BFF', () => {
    const instance: any = Object.create(SettingsComponent.prototype);
    instance.session = { changePassword: vi.fn() };
    instance.changePassword();
    expect(instance.session.changePassword).toHaveBeenCalledTimes(1);
    expect(instance.session.changePassword).toHaveBeenCalledWith('/account/settings');
  });
});

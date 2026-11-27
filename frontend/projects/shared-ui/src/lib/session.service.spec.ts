import '@angular/compiler';
import { describe, it, expect } from 'vitest';
import { changePasswordUrl, PASSWORD_CHANGE_RETURN_PATH } from './session.service';

describe('changePasswordUrl', () => {
  it('starts the closed BFF password action and returns to Settings', () => {
    const url = new URL(changePasswordUrl());
    expect(url.pathname).toBe('/bff/login');
    expect(url.searchParams.get('action')).toBe('password');
    expect(url.searchParams.get('returnTo')).toBe(PASSWORD_CHANGE_RETURN_PATH);
  });

  it('keeps only safe in-app return paths', () => {
    const url = new URL(changePasswordUrl('/account/orders'));
    expect(url.searchParams.get('returnTo')).toBe('/account/orders');
    expect(url.searchParams.get('action')).toBe('password');

    for (const unsafe of ['//evil.example/steal', 'https://evil.example', '/account/../admin', '/other']) {
      expect(new URL(changePasswordUrl(unsafe)).searchParams.get('returnTo')).toBe(PASSWORD_CHANGE_RETURN_PATH);
    }
  });
});

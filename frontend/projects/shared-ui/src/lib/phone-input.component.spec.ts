import '@angular/compiler';
import {describe,it,expect,vi} from 'vitest';
import {PhoneInputComponent} from './phone-input.component';
import {normalizePhoneNumber} from './phone-number';
describe('shared phone input',()=>{
 it('defaults to Nigeria without emitting a fake number',()=>{const c=new PhoneInputComponent(),change=vi.fn();c.registerOnChange(change);c.writeValue(null);expect(c.mode).toBe('NG');expect(c.number).toBe('');expect(change).not.toHaveBeenCalled();});
 it('separates a persisted prefix and handles local trunk zero',()=>{const c=new PhoneInputComponent(),change=vi.fn();c.registerOnChange(change);c.writeValue('+2348012345678');expect(c.number).toBe('8012345678');c.input('08012345678');expect(change).toHaveBeenLastCalledWith('+2348012345678');});
 it('does not duplicate country code when pasting full numbers',()=>{const c=new PhoneInputComponent(),change=vi.fn();c.registerOnChange(change);for(const value of ['+2348012345678','2348012345678']){c.input(value);expect(c.number).toBe('8012345678');expect(change).toHaveBeenLastCalledWith('+2348012345678');}});
 it('preserves international values and detects international pastes',()=>{const c=new PhoneInputComponent(),change=vi.fn();c.registerOnChange(change);c.writeValue('+447700900123');expect(c.mode).toBe('international');expect(c.number).toBe('+447700900123');c.input('+15551234567');expect(change).toHaveBeenLastCalledWith('+15551234567');});
 it('emits empty on clearing, and bare prefix remains invalid',()=>{const c=new PhoneInputComponent(),change=vi.fn();c.registerOnChange(change);c.input('');expect(change).toHaveBeenLastCalledWith('');c.input('+234');expect(normalizePhoneNumber(change.mock.lastCall![0])).toBeNull();});
 it('does not alter a disabled field',()=>{const c=new PhoneInputComponent(),change=vi.fn();c.registerOnChange(change);c.setDisabledState(true);c.input('8012345678');c.changeMode('international');expect(change).not.toHaveBeenCalled();});
 it('clears the old value when the user explicitly changes country mode',()=>{const c=new PhoneInputComponent(),change=vi.fn();c.registerOnChange(change);c.writeValue('+447700900123');c.changeMode('NG');expect(c.number).toBe('');expect(change).toHaveBeenLastCalledWith('');});
});

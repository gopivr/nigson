import {describe,it,expect} from 'vitest';
import {normalizePhoneNumber} from './phone-number';
describe('phone normalization',()=>{
 it.each(['08012345678','8012345678','+2348012345678','2348012345678','+23408012345678','0801 234-5678','+234 (801) 234-5678'])('normalizes %s once to Nigerian E.164',value=>expect(normalizePhoneNumber(value)).toBe('+2348012345678'));
 it.each(['','+','+234','080123','12345678901','+23480123456789','+2342348012345678','phone','+2348012345678 ext 1'])('rejects incomplete/malformed %s',value=>expect(normalizePhoneNumber(value)).toBeNull());
 it('preserves an existing international phone',()=>expect(normalizePhoneNumber('+44 7700 900123')).toBe('+447700900123'));
});

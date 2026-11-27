/** Normalize user input for storage; this validates format, not number ownership. */
export function normalizePhoneNumber(value: string | null | undefined): string | null {
  let phone = (value ?? '').trim().replace(/[\s()-]/g, '');
  if (/^0[1-9][0-9]{9}$/.test(phone)) phone = '+234' + phone.slice(1);
  else if (/^[1-9][0-9]{9}$/.test(phone)) phone = '+234' + phone;
  else if (/^234[1-9][0-9]{9}$/.test(phone)) phone = '+' + phone;
  if (/^\+2340[1-9][0-9]{9}$/.test(phone)) phone = '+234' + phone.slice(5);
  if (phone.startsWith('+234')) return /^\+234[1-9][0-9]{9}$/.test(phone) ? phone : null;
  return /^\+[1-9][0-9]{7,14}$/.test(phone) ? phone : null;
}

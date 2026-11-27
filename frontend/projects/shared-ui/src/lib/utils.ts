import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export const LS_CART = 'nigson_cart';
export const LS_SAVED = 'nigson_saved';
export const LS_ORDERS = 'nigson_orders';
export const LS_USER = 'nigson_user';
export const LS_REVIEWS = 'nigson_reviews';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

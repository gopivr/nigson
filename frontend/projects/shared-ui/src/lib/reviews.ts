export interface Review {
  id: string;
  productId?: string;
  sku?: string;
  displayName?: string;
  name?: string;
  rating: number;
  body?: string;
  text?: string;
  publishedAt?: string;
  date?: string;
}

export type Rating = { average: number; count: number; breakdown: Record<1 | 2 | 3 | 4 | 5, number> };

export function relativeDate(iso?: string): string {
  if (!iso) return "Recently";
  const diff = Date.now() - new Date(iso).getTime();
  const day = Math.floor(diff / 86400000);
  if (day < 1) return "Today";
  if (day === 1) return "1 day ago";
  if (day < 7) return `${day} days ago`;
  if (day < 14) return "1 week ago";
  if (day < 30) return `${Math.floor(day / 7)} weeks ago`;
  if (day < 60) return "1 month ago";
  return `${Math.floor(day / 30)} months ago`;
}

/** Compute real Rating object from reviews array */
export function ratingForReviews(reviews: Review[]): Rating {
  if (!reviews || !reviews.length) {
    return { average: 0, count: 0, breakdown: { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 } };
  }
  const count = reviews.length;
  const sum = reviews.reduce((n, r) => n + (r.rating || 0), 0);
  const average = +(sum / count).toFixed(1);
  const breakdown: Record<1 | 2 | 3 | 4 | 5, number> = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
  reviews.forEach(r => {
    const star = Math.min(5, Math.max(1, Math.round(r.rating || 5))) as 1 | 2 | 3 | 4 | 5;
    breakdown[star] = (breakdown[star] || 0) + 1;
  });
  return { average, count, breakdown };
}

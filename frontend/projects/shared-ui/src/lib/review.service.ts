import { Injectable, inject, signal } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable, of, tap, catchError, map, throwError } from "rxjs";

/**
 * Backend Engagement Review (API-097 - TASK-52, TASK-55)
 */
export interface BackendReview {
  id: string;
  kind: "PRODUCT" | "TESTIMONIAL";
  productId?: string;
  orderLineId?: string;
  rating: number;
  title?: string;
  body: string;
  displayName: string;
  publishedAt: string;
}

export interface ReviewRatingSummary {
  average: number;
  count: number;
  breakdown: Record<1 | 2 | 3 | 4 | 5, number>;
}

export interface EngagementReviewPage {
  items?: BackendReview[];
  content?: BackendReview[];
  totalElements: number;
  totalPages: number;
  number?: number;
  page?: number;
  size: number;
}

@Injectable({ providedIn: "root" })
export class ReviewService {
  private http = inject(HttpClient);

  readonly reviews = signal<BackendReview[]>([]);
  readonly loading = signal(false);

  /** TASK-52: Get verified public reviews for a product UUID */
  getProductReviews(productId: string, page = 0, size = 20): Observable<BackendReview[]> {
    if (!productId) return of([]);
    this.loading.set(true);
    const params = new HttpParams()
      .set("productId", productId)
      .set("kind", "PRODUCT")
      .set("page", String(page))
      .set("size", String(size));

    return this.http.get<EngagementReviewPage>("/api/v1/reviews", { params }).pipe(
      map(pg => pg?.items ?? pg?.content ?? []),
      tap(revs => {
        this.reviews.set(revs);
        this.loading.set(false);
      }),
      catchError(err => {
        console.warn("[ReviewService] getProductReviews failed:", err);
        this.loading.set(false);
        return of([]);
      })
    );
  }

  /** TASK-54: Submit a product review */
  submitReview(payload: {
    kind: "PRODUCT" | "TESTIMONIAL";
    productId?: string;
    orderLineId?: string;
    rating?: number;
    title?: string;
    body: string;
  }): Observable<any> {
    const headers = {
      "Idempotency-Key": crypto.randomUUID()
    };
    return this.http.post<any>("/api/v1/reviews", payload, { headers }).pipe(
      catchError(err => {
        console.error("[ReviewService] submitReview failed:", err);
        return throwError(() => err);
      })
    );
  }

  /** TASK-53: Compute real average rating and star breakdown from review array */
  computeRating(reviews: BackendReview[]): ReviewRatingSummary {
    if (!reviews || reviews.length === 0) {
      return {
        average: 0,
        count: 0,
        breakdown: { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 }
      };
    }

    const count = reviews.length;
    const sum = reviews.reduce((acc, r) => acc + (r.rating || 0), 0);
    const average = +(sum / count).toFixed(1);

    const breakdown: Record<1 | 2 | 3 | 4 | 5, number> = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
    reviews.forEach(r => {
      const star = Math.min(5, Math.max(1, Math.round(r.rating))) as 1 | 2 | 3 | 4 | 5;
      breakdown[star] = (breakdown[star] || 0) + 1;
    });

    return { average, count, breakdown };
  }
}

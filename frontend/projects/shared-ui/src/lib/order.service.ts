import { Injectable, inject, signal } from "@angular/core";
import { HttpClient, HttpParams, HttpHeaders } from "@angular/common/http";
import { Observable, of, tap, catchError, map, switchMap } from "rxjs";
import { SessionService } from "./session.service";

/**
 * Backend Order DTO (TASK-41 to TASK-46)
 * Aligned to GET /api/v1/orders, GET /api/v1/orders/:orderId, and /api/v1/orders/:orderId/tracking.
 */
export interface OrderItem {
  lineId: string;
  skuId: string;
  sku: string;
  name: string;
  quantity: number;
  unitPriceMinorUnits: number;
  imageUrl?: string;
}

export interface OrderAddress {
  line1: string;
  city: string;
  state: string;
  countryCode: string;
  postalCode?: string;
}

export interface TrackingScan {
  timestamp: string;
  status: string;
  location?: string;
  description?: string;
}

export interface OrderTracking {
  orderId: string;
  trackingNumber?: string;
  carrier: string;
  status: string;
  estimatedDelivery?: string;
  scans: TrackingScan[];
}

export interface OrderCancellation {
  id: string;
  state: 'REQUESTED' | 'WAITING_PAYMENT' | 'WAITING_CARRIER' | 'CANCELLED' | 'REJECTED';
  refundState: string | null;
  refundId?: string | null;
  errorCode: string | null;
}

export interface BackendOrder {
  cancellation?: OrderCancellation | null;
  cancellationAllowed?: boolean;
  orderId: string;
  orderNumber: string;
  status: "PENDING" | "PAID" | "PROCESSING" | "SHIPPED" | "DELIVERED" | "CANCELLED" | "PAYMENT_HOLD" | "REFUNDING" | "REFUNDED";
  paymentStatus: "UNPAID" | "PENDING" | "PAID" | "PARTIALLY_REFUNDED" | "REFUNDED" | "FAILED";
  items: OrderItem[];
  subtotalMinorUnits: number;
  discountMinorUnits: number;
  shippingMinorUnits: number;
  taxMinorUnits: number;
  totalMinorUnits: number;
  refundedMinorUnits: number;
  currency: string;
  deliveryAddress: OrderAddress;
  buyerContact: { fullName: string; email: string; phone?: string };
  deliveryMethod: string;
  trackingNumber?: string;
  trackingUrl?: string;
  estimatedDeliveryDate?: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface OrdersPage {
  content: BackendOrder[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** API-237..242 delivered-product return request and status view. */
export type ReturnState = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'RECEIVED';

export interface ReturnLine {
  orderLineId: string;
  name: string;
  orderedQuantity: number;
  availableQuantity: number;
  quantityScale: number;
}

export interface OrderReturn {
  id: string;
  orderId: string;
  orderLineId: string;
  productName: string;
  quantity: number;
  reason: string;
  policyRevision: string;
  state: ReturnState;
  decisionReason: string | null;
  instructions: string | null;
  receiptReference: string | null;
  restock: boolean | null;
  receivedAt: string | null;
  refundId: string | null;
  refundState: string | null;
  version: number;
  createdAt: string;
}

export interface OrderReturns {
  orderId: string;
  orderVersion: number;
  delivered: boolean;
  lines: ReturnLine[];
  requests: OrderReturn[];
  refundableAmount: number;
  paystackRefundAvailable: boolean;
}

/** API-041 local payment state; `settled` is only true after independently verified provider success. */
export interface OrderPayment {
  orderId: string;
  state: string;
  settled: boolean;
  reference: string;
  checkoutUrl: string | null;
}

@Injectable({ providedIn: "root" })
export class OrderService {
  private http = inject(HttpClient);
  private session = inject(SessionService);

  readonly orders = signal<BackendOrder[]>([]);
  readonly loading = signal(false);
  readonly totalElements = signal(0);
  readonly cancellationError = signal<string | null>(null);
  readonly returnError = signal<string | null>(null);
  private cancellationKeys = new Map<string, string>();
  private returnKeys = new Map<string, string>();

  /** TASK-41: Load paginated order history */
  loadOrders(page = 0, size = 20): Observable<OrdersPage | null> {
    if (!this.session.authenticated()) return of(null);
    this.loading.set(true);
    const email = this.session.email() || '';
    const userId = this.session.userId() || '';

    let params = new HttpParams().set("page", String(page)).set("size", String(size));

    let headers = new HttpHeaders();
    if (email) headers = headers.set("x-user-email", email);
    if (userId) headers = headers.set("x-user-id", userId);

    return this.http.get<any>("/api/v1/orders", { params, headers }).pipe(
      map(pg => {
        const rawList = pg?.items || pg?.content || [];
        const mappedOrders: BackendOrder[] = rawList.map((raw: any) => this.mapOrder(raw, email));

        const resultPage: OrdersPage = {
          content: mappedOrders,
          totalElements: pg?.totalElements ?? mappedOrders.length,
          totalPages: pg?.totalPages ?? 1,
          number: pg?.page ?? page,
          size: pg?.size ?? size
        };
        return resultPage;
      }),
      tap(pg => {
        this.orders.set(pg.content);
        this.totalElements.set(pg.totalElements);
        this.loading.set(false);
      }),
      catchError(err => {
        console.error("[OrderService] Backend /api/v1/orders failed:", err?.status ?? err);
        this.orders.set([]);
        this.totalElements.set(0);
        this.loading.set(false);
        return of(null);
      })
    );
  }

  /** Map the commerce order DTO (lines/totals/address strings) onto the storefront view model. */
  private mapOrder(raw: any, fallbackEmail = ''): BackendOrder {
    const money = (value: unknown) => Math.round(parseFloat(String(value ?? '0')) * 100) || 0;
    const lines = raw?.lines || raw?.items || [];
    return {
      orderId: raw?.id || raw?.orderId || '',
      orderNumber: raw?.orderNumber || '',
      status: raw?.status,
      paymentStatus: raw?.paymentStatus,
      cancellation: raw?.cancellation ?? null,
      cancellationAllowed: raw?.cancellationAllowed === true,
      items: lines.map((l: any) => ({
        lineId: l.id || l.lineId,
        skuId: l.skuId,
        sku: l.skuCode || l.sku || '',
        name: l.name || '',
        quantity: Number(l.quantity) || 1,
        unitPriceMinorUnits: l.unitPriceMinorUnits ?? money(l.unitAmount),
        imageUrl: l.imageUrl
      })),
      subtotalMinorUnits: raw?.subtotalMinorUnits ?? money(raw?.totals?.subtotal),
      discountMinorUnits: raw?.discountMinorUnits ?? money(raw?.totals?.discount),
      shippingMinorUnits: raw?.shippingMinorUnits ?? money(raw?.totals?.delivery),
      taxMinorUnits: raw?.taxMinorUnits ?? money(raw?.totals?.tax),
      totalMinorUnits: raw?.totalMinorUnits ?? money(raw?.totals?.grandTotal),
      refundedMinorUnits: raw?.refundedMinorUnits ?? money(raw?.totals?.refunded),
      currency: raw?.currency || 'NGN',
      deliveryAddress: raw?.address ? {
        line1: raw.address.line1 || '',
        city: raw.address.city || '',
        state: raw.address.region || raw.address.state || '',
        countryCode: raw.address.countryCode || 'NG',
        postalCode: raw.address.postalCode
      } : { line1: '', city: '', state: '', countryCode: 'NG' },
      buyerContact: {
        fullName: raw?.address?.recipient || raw?.customerName || raw?.customer?.name || raw?.buyerContact?.fullName || '',
        email: raw?.customerEmail || raw?.customer?.email || raw?.buyerContact?.email || fallbackEmail,
        phone: raw?.address?.phone || raw?.customerPhone || raw?.customer?.phone || raw?.buyerContact?.phone || ''
      },
      deliveryMethod: raw?.fulfillmentStatus || raw?.delivery || raw?.shippingMethod || 'STANDARD',
      trackingNumber: raw?.trackingNumber,
      version: raw?.version,
      createdAt: raw?.createdAt || raw?.date || new Date().toISOString(),
      updatedAt: raw?.updatedAt || raw?.createdAt || raw?.date || new Date().toISOString()
    };
  }

  /** TASK-42: Get single order detail */
  getOrder(orderId: string): Observable<BackendOrder> {
    return this.http.get<any>("/api/v1/orders/" + orderId).pipe(map(raw => this.mapOrder(raw)));
  }

  /** API-237: Bounded delivered-product return options and prior return records for one owned order. */
  getReturns(orderId: string): Observable<OrderReturns | null> {
    return this.http.get<any>("/api/v1/orders/" + orderId + "/returns").pipe(
      map(raw => this.mapReturns(raw)),
      catchError(err => {
        if (err?.status !== 404) console.warn("[OrderService] getReturns:", err?.status ?? err);
        return of(null);
      })
    );
  }

  /** API-238: Reserve one product and exact quantity; a lost response reuses the same key and version. */
  requestReturn(orderId: string, lineId: string, quantity: number, reason: string): Observable<OrderReturn | null> {
    this.returnError.set(null);
    return this.getReturns(orderId).pipe(
      switchMap(view => {
        if (!view || !view.delivered) {
          this.returnError.set('Returns are available only after the order has been delivered.');
          return of(null);
        }
        const identity = `${orderId}:${lineId}:${quantity}:${reason}`;
        let key = this.returnKeys.get(identity);
        if (!key) {
          key = crypto.randomUUID();
          this.returnKeys.set(identity, key);
        }
        return this.http.post<any>(`/api/v1/orders/${orderId}/returns`,
          { orderLineId: lineId, quantity: String(quantity), reason },
          { headers: new HttpHeaders({ 'If-Match': `"${view.orderVersion}"`, 'Idempotency-Key': key }) })
          .pipe(
            map(raw => this.mapReturn(raw)),
            tap(() => this.returnKeys.delete(identity)),
            catchError(err => {
              const code = err?.error?.code;
              this.returnError.set(code === 'INVALID_TRANSITION'
                ? 'This product or quantity is no longer returnable. Refresh the order and try again.'
                : code === 'VERSION_MISMATCH'
                  ? 'The order changed. Refresh its latest status before requesting a return.'
                  : code === 'ORDER_RULE_VIOLATION'
                    ? 'The requested quantity exceeds the delivered, unreturned quantity.'
                    : 'We could not confirm your return request. Retry to check the same request.');
              if (code === 'VERSION_MISMATCH') this.returnKeys.delete(identity);
              return of(null);
            })
          );
      })
    );
  }

  private mapReturns(raw: any): OrderReturns {
    const quantity = (value: unknown) => Number(value ?? 0) || 0;
    return {
      orderId: raw?.orderId ?? '',
      orderVersion: Number(raw?.orderVersion ?? 0) || 0,
      delivered: raw?.delivered === true,
      lines: (raw?.lines ?? []).map((line: any) => ({
        orderLineId: line.orderLineId,
        name: line.name,
        orderedQuantity: quantity(line.orderedQuantity),
        availableQuantity: quantity(line.availableQuantity),
        quantityScale: Number(line.quantityScale ?? 0) || 0
      })),
      requests: (raw?.requests ?? []).map((item: any) => this.mapReturn(item)),
      refundableAmount: quantity(raw?.refundableAmount),
      paystackRefundAvailable: raw?.paystackRefundAvailable === true
    };
  }

  private mapReturn(raw: any): OrderReturn {
    return {
      id: raw?.id ?? '',
      orderId: raw?.orderId ?? '',
      orderLineId: raw?.orderLineId ?? '',
      productName: raw?.productName ?? '',
      quantity: Number(raw?.quantity ?? 0) || 0,
      reason: raw?.reason ?? '',
      policyRevision: raw?.policyRevision ?? '',
      state: raw?.state,
      decisionReason: raw?.decisionReason ?? null,
      instructions: raw?.instructions ?? null,
      receiptReference: raw?.receiptReference ?? null,
      restock: typeof raw?.restock === 'boolean' ? raw.restock : null,
      receivedAt: raw?.receivedAt ?? null,
      refundId: raw?.refundId ?? null,
      refundState: raw?.refundState ?? null,
      version: Number(raw?.version ?? 0) || 0,
      createdAt: raw?.createdAt ?? ''
    };
  }

  /** API-041: Verified local payment attempt state for the hosted-checkout return page. */
  getPayment(orderId: string): Observable<OrderPayment> {
    return this.http.get<OrderPayment>("/api/v1/orders/" + orderId + "/payment");
  }

  /** TASK-44: Cancel order (API-040: POST /api/v1/orders/:orderId/cancellation) */
  cancelOrder(orderId: string): Observable<BackendOrder | null> {
    this.cancellationError.set(null);
    // A retry after response loss reuses both the original version and request key.
    return this.getOrder(orderId).pipe(
      switchMap(order => {
        if (order.cancellation) return of(order);
        let saved = this.cancellationKeys.get(orderId);
        if (!saved) {
          saved = JSON.stringify({ key: crypto.randomUUID(), version: order.version });
          this.cancellationKeys.set(orderId, saved);
        }
        const command = JSON.parse(saved) as { key: string; version: number };
        return this.http.post<any>(`/api/v1/orders/${orderId}/cancellation`,
          { reason: 'Customer requested order cancellation' },
          { headers: new HttpHeaders({ 'If-Match': `"${command.version}"`, 'Idempotency-Key': command.key }) })
          .pipe(map(raw => this.mapOrder(raw, order.buyerContact?.email)));
      }),
      tap(updated => this.orders.update(list => list.map(order => order.orderId === orderId ? updated : order))),
      catchError(err => {
        const code = err?.error?.code;
        this.cancellationError.set(code === 'INVALID_TRANSITION'
          ? 'This order can no longer be cancelled online. Please contact support.'
          : code === 'VERSION_MISMATCH'
            ? 'The order changed. Please review its latest status and try again.'
            : 'We could not confirm your cancellation request. Retry to check the same request.');
        if (code === 'VERSION_MISMATCH') this.cancellationKeys.delete(orderId);
        return of(null);
      })
    );
  }

  /** TASK-45: Get order tracking (API-051: GET /api/v1/orders/:orderId/tracking) */
  getTracking(orderId: string): Observable<OrderTracking | null> {
    return this.http.get<any>("/api/v1/orders/" + orderId + "/tracking").pipe(
      map(raw => {
        if (!raw) return null;
        const shipments = Array.isArray(raw.shipments) ? raw.shipments : [];
        const events = Array.isArray(raw.events) ? raw.events : [];
        const latestShipment = shipments.length ? shipments[shipments.length - 1] : null;
        const latestEvent = events.length ? events[events.length - 1] : null;
        return {
          orderId: raw.orderId || orderId,
          carrier: latestShipment?.providerOrderId ? 'PandaGo' : '',
          trackingNumber: latestShipment?.providerOrderId || undefined,
          status: latestShipment?.status || latestEvent?.status || 'PROCESSING',
          scans: events.map((e: any) => ({
            timestamp: e.occurredAt || e.timestamp || '',
            status: e.status || '',
            description: e.note || e.description || ''
          }))
        };
      }),
      catchError(err => {
        console.warn("[OrderService] getTracking:", err);
        return of(null);
      })
    );
  }

  /** Format kobo to NGN display */
  formatPrice(minorUnits: number): string {
    return String.fromCharCode(8358) + (minorUnits / 100).toLocaleString("en-NG", {
      minimumFractionDigits: 2, maximumFractionDigits: 2
    });
  }

  /** Map backend status enum to display label */
  statusLabel(status: string): string {
    const map: Record<string, string> = {
      PENDING: "Pending", PAID: "Paid", PROCESSING: "Processing",
      SHIPPED: "Shipped", DELIVERED: "Delivered", CANCELLED: "Cancelled",
      PAYMENT_HOLD: "Payment hold", REFUNDING: "Refunding", REFUNDED: "Refunded",
      CONFIRMED: "Confirmed", PACKED: "Packed", OUT_FOR_DELIVERY: "Out For Delivery"
    };
    return map[status] ?? status;
  }

  /** Map status to CSS badge classes */
  statusColor(status: string): string {
    const colors: Record<string, string> = {
      PENDING: "bg-amber-100 text-amber-700",
      PAID: "bg-emerald-100 text-emerald-700",
      PROCESSING: "bg-blue-100 text-blue-700",
      SHIPPED: "bg-violet-100 text-violet-700",
      DELIVERED: "bg-emerald-100 text-emerald-700",
      CANCELLED: "bg-rose-100 text-rose-700",
      PAYMENT_HOLD: "bg-amber-100 text-amber-800",
      REFUNDING: "bg-orange-100 text-orange-700",
      REFUNDED: "bg-surface-alt text-muted-ink",
      CONFIRMED: "bg-blue-100 text-blue-700",
      PACKED: "bg-indigo-100 text-indigo-700",
      OUT_FOR_DELIVERY: "bg-orange-100 text-orange-700",
    };
    return colors[status] ?? "bg-surface-alt";
  }
}

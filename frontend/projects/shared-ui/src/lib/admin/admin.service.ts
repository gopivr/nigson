import {SectionKey, BackendRole, ROLE_PERMISSIONS, canAccessSection} from '../staff-access';
export type {SectionKey, BackendRole} from '../staff-access';
export {ROLE_PERMISSIONS} from '../staff-access';
import { Injectable, computed, signal, inject, effect, untracked, WritableSignal } from '@angular/core';
import { HttpClient, HttpContext, HttpHeaders } from '@angular/common/http';
import { Observable, of, tap, map, catchError, forkJoin, switchMap, defer, throwError, finalize, shareReplay } from 'rxjs';
import { LOCAL_API_FAILURE_CODES } from '../api-errors.service';
import type { OrderCancellation, OrderReturn, OrderReturns } from '../order.service';
export type { OrderReturn, OrderReturns } from '../order.service';
import { SessionService } from '../session.service';
import { CatalogService } from '../catalog.service';
import { applyInventory, mapStaffProducts, inventoryTotals } from './inventory-mapping';
import { distributorCommand, distributorDisplayState, leadDisplayState, distributorActionBlock, partnerLeadState, partnerLeadActionAllowed, applicationFailure } from './distributor-workflow';

export type AdminProduct = {
  id: string; version?: number; skuVersion?: number; packSize?: string; categoryIds?: string[]; slug?: string; brandId?: string; skuId?: string; name: string; sku: string; category: string; price: number;
  desc: string; image: string; images?: string[]; status: "DRAFT" | "ACTIVE" | "ARCHIVED" | "Active" | "Draft" | "Out of Stock" | "UNKNOWN";
  stock: number; reserved: number; onHand?: number; allocated?: number; available?: number; rating: number;
  cost?: number; retail?: number; lowLevel?: number;
  brand?: string;
  type?: string;
  priceSubtitle?: string;
  wholesale?: number;
  distributor?: number;
  features?: string;
  overview?: string;
  overviewExtra?: string;
  highlights?: string;
  specs?: string;
  specifications?: Record<string, any>;
  packaging?: string;
  cartonSpecs?: string;
  warehouseStatus?: string;
  dispatchTurnaround?: string;
  nationwideShipping?: string;
  minWholesaleOrder?: string;
  warranty?: string;
  connectors?: string;
  powerRating?: string;
  compatibility?: string;
  buildMaterial?: string;
  certification?: string;
  importer?: string;
  updatedAt?: string;
};

export type AdminPage = {
  id: string; name: string; path: string; heading: string; description: string;
  image: string; status: "Published" | "Draft"; updatedAt: string;
};

export type AdminReview = {
  id: string; version?: number; state?: string; sku: string; product: string; author: string; rating: number;
  text: string; date: string; status: "Published" | "Hidden";
};

export type LeadStatus = "Pending" | "Under Review" | "Approved" | "Rejected";

export type AdminApplication = {
  organizationId?: string | null;
  id: string; number: string; type: "Distributor" | "Wholesaler" | "Affiliate Marketer"; businessName: string;
  contactPerson: string; email: string; phone: string; location: string; category: string;
  date: string; status: string; note?: string; version?: number;
  rawState?: string; partnerType?:string; reviewerPartyId?: string | null; decisionReason?: string | null;
  source?: 'distributor' | 'lead';
  businessReference?: string;
  reviewReason?: string;
  documents?: Array<{
    id?: string;
    assetId?: string;
    fileId?: string;
    type?: string;
    kind?: string;
    scanStatus?: string;
    state?: string;
    originalFilename?: string;
    contentType?: string;
  }>;
};

export type AdminQuote = {
  id: string; number: string; customer: string; email: string; phone: string;
  product: string; sku: string; quantity: number; message: string; date: string;
  status: "Pending" | "Under Review" | "Quoted" | "Closed";
  version?: number;
};

export type AdminInquiry = {
  id: string; version?: number; name: string; email: string; phone: string; subject: string;
  message: string; date: string; status: "New" | "In Progress" | "Resolved";
};

export function isPartnerSubmission(item: { subject?: string; message?: string }): boolean {
  const s = (item.subject || '').toLowerCase();
  const m = (item.message || '').toLowerCase();
  if (
    s.includes('affiliate') ||
    s.includes('distributor application') ||
    s.includes('corporate distributor') ||
    s.includes('wholesale application') ||
    s.includes('partner application')
  ) {
    return true;
  }
  return (
    m.includes('affiliate marketer') ||
    m.includes('corporate regional distributor') ||
    m.includes('corporate distributor application') ||
    m.includes('wholesale trade distributor') ||
    m.includes('trade & bulk wholesale') ||
    m.includes('wholesale application')
  );
}

export type AdminOrderStatus = "Pending" | "Confirmed" | "Processing" | "Packed" | "Shipped" | "Delivered" | "Cancelled";

export type AdminRefund = { id: string; state: string; amount: string; currency: string; version: number };

export type AdminOrder = {
  cancellation?: OrderCancellation | null;
  afterSales?: { returnState: string; refundState: string | null; updatedAt: string } | null;
  id: string; version?: number; rawState?: string; number: string; date: string;
  customer: { name: string; email: string; phone: string; address: string };
  items: { lineId?: string; sku: string; name: string; qty: number; price: number }[];
  total: number; paymentStatus: "Paid" | "Pending" | "Refunded";
  status: AdminOrderStatus; delivery: string; timeline: { label: string; date: string }[];
};

export type AdminCustomer = {
  id: string; name: string; email: string; phone: string; location: string;
  joined: string; orders: number; spend: number; saved: number; quotes: number;
  status: "Active" | "Suspended";
};

export type AdminPost = {
  id: string; slug: string; title: string; excerpt: string; tag: string;
  image: string; date: string; status: "Published" | "Draft"; body?: string;
};

export type AdminUser = {
  id: string; name: string; email: string; roles?: string[]; roleId: string;
  status: "Active" | "Disabled"; lastLogin?: string; lastLoginAt?: string;
  version?: number;
};

export type AdminRole = { id: string; name: string; permissions: SectionKey[] };

export type StockLog = { id: string; sku: string; product: string; change: number; qty?: number; reason: string; date: string; by: string; supplier?: string; ref?: string; notes?: string; };

export type Activity = { id: string; text: string; date: string };

function getInitialStockLog(): StockLog[] {
  try {
    const saved = typeof localStorage !== 'undefined' ? localStorage.getItem('nigson_stock_movements') : null;
    if (saved) {
      const parsed = JSON.parse(saved);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed;
      }
    }
  } catch { }

  return [
    { id: "sl_init_1", sku: "CHAVEENPHONES", product: "chaveen phones", change: 100, qty: 100, reason: "Opening Stock", date: "2026-11-05T06:40:00.000Z", by: "Nigson Administrator", supplier: "Nigson HQ", ref: "INIT-CHAVEEN" },
    { id: "sl_init_2", sku: "DEMO-EL-01-01", product: "Demo True Wireless Earbuds", change: 18, qty: 18, reason: "Opening Stock", date: "2026-11-04T08:20:00.000Z", by: "System Bootstrap", supplier: "Shenzhen Factory", ref: "INIT-0101" },
    { id: "sl_init_3", sku: "DEMO-EL-01-02", product: "Demo Wireless Charging Stand", change: 9, qty: 9, reason: "Opening Stock", date: "2026-11-04T08:20:00.000Z", by: "System Bootstrap", supplier: "Shenzhen Factory", ref: "INIT-0102" },
    { id: "sl_init_4", sku: "DEMO-EL-01-03", product: "Demo Braided USB-C Cable (2m)", change: 24, qty: 24, reason: "Opening Stock", date: "2026-11-04T08:20:00.000Z", by: "System Bootstrap", supplier: "Shenzhen Factory", ref: "INIT-0103" },
    { id: "sl_init_5", sku: "DEMO-EL-02-01", product: "Demo Noise-Cancelling Headphones", change: 35, qty: 35, reason: "Opening Stock", date: "2026-11-04T08:20:00.000Z", by: "System Bootstrap", supplier: "Shenzhen Factory", ref: "INIT-0201" },
    { id: "sl_init_6", sku: "DEMO-EL-02-02", product: "Demo Smart Fitness Tracker", change: 12, qty: 12, reason: "Opening Stock", date: "2026-11-04T08:20:00.000Z", by: "System Bootstrap", supplier: "Local Distributor", ref: "INIT-0202" },
    { id: "sl_init_7", sku: "DEMO-EL-03-01", product: "Demo 65W GaN Fast Charger", change: 16, qty: 16, reason: "Opening Stock", date: "2026-11-04T08:20:00.000Z", by: "System Bootstrap", supplier: "Nigson HQ", ref: "INIT-0301" },
    { id: "sl_init_8", sku: "DEMO-EL-03-03", product: "Demo Heavy-Duty Power Bank 20000mAh", change: 22, qty: 22, reason: "Opening Stock", date: "2026-11-04T08:20:00.000Z", by: "System Bootstrap", supplier: "Nigson HQ", ref: "INIT-0303" },
    { id: "sl_init_9", sku: "DEMO-EL-04-01", product: "Demo Magnetic Phone Mount", change: 28, qty: 28, reason: "Opening Stock", date: "2026-11-04T08:20:00.000Z", by: "System Bootstrap", supplier: "Local Distributor", ref: "INIT-0401" },
    { id: "sl_init_10", sku: "DEMO-EL-04-02", product: "Demo Bluetooth Car FM Transmitter", change: 45, qty: 45, reason: "Opening Stock", date: "2026-11-04T08:20:00.000Z", by: "System Bootstrap", supplier: "Local Distributor", ref: "INIT-0402" },
    { id: "sl_init_11", sku: "DEMO-EL-07-03", product: "Demo Surge Protector Extension", change: 60, qty: 60, reason: "Opening Stock", date: "2026-11-04T08:20:00.000Z", by: "System Bootstrap", supplier: "Nigson HQ", ref: "INIT-0703" }
  ];
}

const LS_CUSTOMERS = 'nigson_customers';

const DEFAULT_CUSTOMERS: AdminCustomer[] = [
  {
    id: "cust-1",
    name: "Kene Nigson",
    email: "kene@example.com",
    phone: "+234 801 234 5678",
    location: "Victoria Island, Lagos",
    joined: "2026-07-15T09:00:00Z",
    orders: 3,
    spend: 285000,
    saved: 4,
    quotes: 1,
    status: "Active"
  },
  {
    id: "cust-2",
    name: "Chaveen Reddy",
    email: "chaveenreddy100@gmail.com",
    phone: "+234 802 345 6789",
    location: "Maitama, Abuja",
    joined: "2026-08-01T11:30:00Z",
    orders: 2,
    spend: 145000,
    saved: 2,
    quotes: 0,
    status: "Active"
  },
  {
    id: "cust-3",
    name: "E2E Customer",
    email: "e2e@nigson.ng",
    phone: "+234 803 456 7890",
    location: "Port Harcourt, Rivers",
    joined: "2026-08-20T14:15:00Z",
    orders: 4,
    spend: 420000,
    saved: 6,
    quotes: 2,
    status: "Active"
  },
  {
    id: "cust-4",
    name: "Retail Store A (Ikeja Hub)",
    email: "retaila@example.invalid",
    phone: "+234 805 678 1234",
    location: "Ikeja, Lagos",
    joined: "2026-09-05T10:00:00Z",
    orders: 5,
    spend: 650000,
    saved: 3,
    quotes: 1,
    status: "Active"
  },
  {
    id: "cust-5",
    name: "King Enterprise",
    email: "king@gmail.com",
    phone: "+234 807 890 2345",
    location: "Kano, Nigeria",
    joined: "2026-09-18T16:20:00Z",
    orders: 1,
    spend: 95000,
    saved: 1,
    quotes: 0,
    status: "Active"
  },
  {
    id: "cust-6",
    name: "Zarina Wholesale Group",
    email: "wholesale@nigson.ng",
    phone: "+234 809 123 4567",
    location: "Onitsha, Anambra",
    joined: "2026-10-02T13:45:00Z",
    orders: 3,
    spend: 510000,
    saved: 5,
    quotes: 3,
    status: "Active"
  }
];

function isStaffOrFixture(email?: string, name?: string): boolean {
  const e = (email || '').toLowerCase().trim();
  const n = (name || '').toLowerCase().trim();
  return (
    e.includes('admin@example.invalid') ||
    e.includes('operations@example.invalid') ||
    e.includes('analyst@example.invalid') ||
    n.includes('admin local fixture') ||
    n.includes('local fixture')
  );
}

function getInitialCustomers(): AdminCustomer[] {
  try {
    const raw = localStorage.getItem(LS_CUSTOMERS);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) {
        const filtered = parsed.filter((c: any) => !isStaffOrFixture(c.email, c.name));
        if (filtered.length > 0) return filtered;
      }
    }
  } catch { }
  return [...DEFAULT_CUSTOMERS];
}

const LS_ADMINS = 'nigson_admin_users';
const LS_ADMIN_OVERRIDES = 'nigson_admin_user_overrides';

function getAdminOverrides(): Record<string, Partial<AdminUser>> {
  try {
    const raw = typeof localStorage !== 'undefined' ? localStorage.getItem(LS_ADMIN_OVERRIDES) : null;
    if (raw) return JSON.parse(raw);
  } catch { }
  return {};
}

function saveAdminOverride(email: string, user: Partial<AdminUser>) {
  try {
    const current = getAdminOverrides();
    current[email.toLowerCase()] = user;
    localStorage.setItem(LS_ADMIN_OVERRIDES, JSON.stringify(current));
  } catch { }
}

function clearAdminOverride(email: string) {
  try {
    const current = getAdminOverrides();
    delete current[email.toLowerCase()];
    localStorage.setItem(LS_ADMIN_OVERRIDES, JSON.stringify(current));
  } catch { }
}

function getInitialAdmins(): AdminUser[] {
  try {
    const raw = typeof localStorage !== 'undefined' ? localStorage.getItem(LS_ADMINS) : null;
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
  } catch { }
  return [
    { id: "43c829a8-5ddc-5d8a-be64-557f8998c1a9", name: "admin Local Fixture", email: "admin@example.invalid", roleId: "ADMIN", status: "Active", lastLogin: new Date().toISOString(), version: 1 },
    { id: "20f23ef1-2395-5c7e-abe7-00e07dcde379", name: "Sales Specialist", email: "sales@example.invalid", roleId: "SALES", status: "Active", lastLogin: new Date().toISOString(), version: 1 },
    { id: "5cb1caac-6970-5ef9-99c6-8d3be9d2d793", name: "Operations Manager", email: "operations@example.invalid", roleId: "OPERATIONS", status: "Active", lastLogin: new Date().toISOString(), version: 1 },
    { id: "ef88a0dc-27c1-5ef9-b53e-a745d253d5cc", name: "Finance Officer", email: "financea@example.invalid", roleId: "FINANCE", status: "Active", lastLogin: new Date().toISOString(), version: 1 },
    { id: "3c2b8f13-37c3-5764-a6ea-f1085167acb4", name: "Data Analyst", email: "analyst@example.invalid", roleId: "ANALYST", status: "Active", lastLogin: new Date().toISOString(), version: 1 },
    { id: "au_2", name: "Chaveen Reddy", email: "chaveenreddy100@gmail.com", roleId: "ADMIN", status: "Active", lastLogin: new Date().toISOString(), version: 1 },
    { id: "au_3", name: "chaveen Customer", email: "chaveen@gmail.com", roleId: "ADMIN", status: "Active", lastLogin: new Date().toISOString(), version: 1 },
  ];
}

export const SEED_PRODUCTS: AdminProduct[] = [
  { id: '5570995e-1b59-5592-ba5c-fa95a0c02c20', skuId: 'ecac9622-ee78-5c52-8eb5-7c244e055d03', sku: 'NS03', name: 'NS03 Wireless Earbuds (TWS)', category: 'Earbuds', brand: 'Nigson', price: 18500, retail: 18500, cost: 12000, stock: 45, reserved: 2, status: 'Active', desc: 'High-fidelity Bluetooth wireless earbuds with clear bass and ergonomic fit.', image: '/assets/cat-earbuds.jpg', images: ['/assets/cat-earbuds.jpg'], rating: 4.8, lowLevel: 10 },
  { id: '8ba363e8-815a-52ee-be26-74da166eba83', skuId: 'c52cf637-25d0-5eb6-862a-e737389c7ad4', sku: 'NS06', name: 'NS06 ANC/ENC Wireless Earbuds', category: 'Earbuds', brand: 'Nigson', price: 28000, retail: 28000, cost: 18000, stock: 32, reserved: 1, status: 'Active', desc: 'Active noise cancellation earbuds with environmental noise suppression microphone.', image: '/assets/cat-earbuds.jpg', images: ['/assets/cat-earbuds.jpg'], rating: 4.9, lowLevel: 10 },
  { id: '41934e70-179f-5c0a-9498-28c8b6a4fd9f', skuId: '63573bb9-a11d-542a-97aa-ca6cb2c92290', sku: 'NS107', name: 'NS107 High-Power Car Charger with Retractable Cable', category: 'Chargers', brand: 'Nigson', price: 12500, retail: 12500, cost: 8000, stock: 28, reserved: 0, status: 'Active', desc: 'Dual-port fast vehicle charger with self-retracting high-durability charging lead.', image: '/assets/cat-charger.jpg', images: ['/assets/cat-charger.jpg'], rating: 4.7, lowLevel: 10 },
  { id: 'bff4b7e3-8e19-566b-9c95-bc941390f84f', skuId: '4907f781-8654-5559-88cf-1ce35df2cb15', sku: 'NS211C', name: 'NS211C Type-C to Type-C PD65W Cable', category: 'Cables', brand: 'Nigson', price: 4500, retail: 4500, cost: 2500, stock: 120, reserved: 5, status: 'Active', desc: 'Braided 65W Power Delivery fast charging cable with reinforced alloy connectors.', image: '/assets/cat-cable.jpg', images: ['/assets/cat-cable.jpg'], rating: 4.9, lowLevel: 20 },
  { id: '3bd11da9-6a93-5f56-ae87-8afdad3d52ab', skuId: 'c3002b2f-4326-59ec-b460-09ba406bc800', sku: 'NS211i', name: 'NS211i Type-C to Lightning PD30W Cable', category: 'Cables', brand: 'Nigson', price: 4500, retail: 4500, cost: 2500, stock: 95, reserved: 4, status: 'Active', desc: 'Fast Lightning charging and data cable compatible with Apple devices.', image: '/assets/cat-cable.jpg', images: ['/assets/cat-cable.jpg'], rating: 4.8, lowLevel: 20 },
  { id: '1908dc43-accf-5578-870c-ec519d73367c', skuId: 'f65f2023-ef23-53ca-bfa2-8b40877ed658', sku: 'NS25', name: 'NS25 4-in-1 Charging Cable', category: 'Cables', brand: 'Nigson', price: 6500, retail: 6500, cost: 4000, stock: 80, reserved: 2, status: 'Active', desc: 'Universal 4-connector heavy-duty charging lead for multiple mobile phone models.', image: '/assets/cat-cable.jpg', images: ['/assets/cat-cable.jpg'], rating: 4.6, lowLevel: 15 },
  { id: '51c9cd3c-5d6c-5049-b821-d80c7c512305', skuId: '812f8742-6c94-5b92-a313-f0c0fdee9559', sku: 'NS2526C', name: 'NS2526C High Power Fast Wall Charger, GaN65W', category: 'Chargers', brand: 'Nigson', price: 16000, retail: 16000, cost: 10500, stock: 54, reserved: 3, status: 'Active', desc: 'GaN technology 65W compact travel wall adapter with multi-voltage safety protection.', image: '/assets/cat-charger.jpg', images: ['/assets/cat-charger.jpg'], rating: 4.9, lowLevel: 10 },
  { id: '1fc9d7b4-1716-54fb-905a-ea5a80620a07', skuId: 'b5cf3ecb-5ed0-5e6f-9cea-4da88f91baa3', sku: 'NS2618', name: 'NS2618 All-in-One Universal Multiway Adapter, GaN65W', category: 'Chargers', brand: 'Nigson', price: 22000, retail: 22000, cost: 14000, stock: 40, reserved: 1, status: 'Active', desc: 'Worldwide travel plug converter with integrated 65W GaN charging sockets.', image: '/assets/cat-charger.jpg', images: ['/assets/cat-charger.jpg'], rating: 4.8, lowLevel: 10 },
  { id: '28cd1e1a-bc40-5728-bf56-4c785b8613f2', skuId: 'c4f089fb-7a27-5b3e-bc02-f9565d5653ff', sku: 'NS3416', name: 'NS3416 65W GaN Power Socket', category: 'Extension Sockets', brand: 'Nigson', price: 29500, retail: 29500, cost: 19000, stock: 35, reserved: 2, status: 'Active', desc: 'Heavy-duty multi-gang extension hub with built-in high power USB-C fast ports.', image: '/assets/cat-powerbank.jpg', images: ['/assets/cat-powerbank.jpg'], rating: 4.7, lowLevel: 10 },
  { id: 'cd9acab9-5bce-5274-a051-773e756d1b0e', skuId: '51c2d6f5-82ae-5eae-8f5d-5975fd7318fc', sku: 'NS3604C', name: 'NS3604C Multi-Socket Extension Box', category: 'Extension Sockets', brand: 'Nigson', price: 34000, retail: 34000, cost: 22000, stock: 25, reserved: 0, status: 'Active', desc: 'Overload and surge protected power distributor suitable for commercial and home setups.', image: '/assets/cat-powerbank.jpg', images: ['/assets/cat-powerbank.jpg'], rating: 4.8, lowLevel: 10 },
  { id: '95be469d-489f-5d09-9308-18f6941e4d41', skuId: 'b9f0156b-c3d2-5bf6-8113-b88f83c01e6d', sku: 'NS4', name: 'NS4 Home Charger', category: 'Chargers', brand: 'Nigson', price: 8500, retail: 8500, cost: 5000, stock: 65, reserved: 4, status: 'Active', desc: 'Standard high-efficiency home wall charger with short-circuit protection.', image: '/assets/cat-charger.jpg', images: ['/assets/cat-charger.jpg'], rating: 4.6, lowLevel: 15 },
  { id: '240af070-7cac-5840-93f7-731aff6bf976', skuId: 'e5e75b4f-26fa-56bd-a68f-c10aa50082f3', sku: 'NS6', name: 'NS6 Large Motion-Activated Cabinet Light (410MM)', category: 'Motion-Activated Lights', brand: 'Nigson', price: 14500, retail: 14500, cost: 9000, stock: 42, reserved: 1, status: 'Active', desc: 'Slim magnetic rechargeable PIR sensor strip light for closets, wardrobes and kitchens.', image: '/assets/hero-accessories.jpg', images: ['/assets/hero-accessories.jpg'], rating: 4.7, lowLevel: 10 },
  { id: '82478136-b526-5238-94c9-d076af6ebc47', skuId: '4af2eb38-f4cc-5bc0-b182-01e667ed00b7', sku: 'NS6-1', name: 'NS6-1 Medium Motion-Activated Cabinet Light (220MM)', category: 'Motion-Activated Lights', brand: 'Nigson', price: 11500, retail: 11500, cost: 7200, stock: 38, reserved: 2, status: 'Active', desc: 'Compact rechargeable warm-light bar with automatic motion trigger.', image: '/assets/hero-accessories.jpg', images: ['/assets/hero-accessories.jpg'], rating: 4.8, lowLevel: 10 },
  { id: '0a51dae9-2795-507b-84a6-100c06513332', skuId: '21d1fa9a-6d1e-54fb-98e0-72439394fe00', sku: 'NS901C', name: 'NS901C Type-C to Type-C Charging Cable', category: 'Cables', brand: 'Nigson', price: 3500, retail: 3500, cost: 2000, stock: 110, reserved: 6, status: 'Active', desc: 'Durable everyday USB Type-C sync and fast battery charging lead.', image: '/assets/cat-cable.jpg', images: ['/assets/cat-cable.jpg'], rating: 4.7, lowLevel: 25 },
  { id: '0de50201-b6bf-5270-b7d4-79766c7f9447', skuId: '70bdf50e-781f-5578-9afb-bcf69125a1f3', sku: 'NS901i', name: 'NS901i Type-C to Lightning Charging Cable', category: 'Cables', brand: 'Nigson', price: 3500, retail: 3500, cost: 2000, stock: 85, reserved: 3, status: 'Active', desc: 'Everyday Lightning mobile cable with anti-tangle rubber housing.', image: '/assets/cat-cable.jpg', images: ['/assets/cat-cable.jpg'], rating: 4.6, lowLevel: 20 },
  { id: '033336d7-5025-5112-9af2-726e6e02caf4', skuId: '49aca633-ca39-5771-aabd-0cd1448db881', sku: 'NS96U', name: 'NS96U 3-in-1 Charging Cable', category: 'Cables', brand: 'Nigson', price: 5500, retail: 5500, cost: 3200, stock: 75, reserved: 4, status: 'Active', desc: 'Triple connector lead supporting Lightning, USB-C and Micro-USB devices simultaneously.', image: '/assets/cat-cable.jpg', images: ['/assets/cat-cable.jpg'], rating: 4.8, lowLevel: 15 },
  { id: 'fee9c518-4071-5aa7-a72b-6ed0ed1b83d3', skuId: '04ae2d72-94ab-5ff8-82a4-28272755ee75', sku: 'PQ19', name: 'PQ19 Power Bank, 10,000mAh', category: 'Power Banks', brand: 'Nigson', price: 19500, retail: 19500, cost: 12500, stock: 60, reserved: 3, status: 'Active', desc: 'Pocket-sized slim high density backup battery with dual output.', image: '/assets/cat-powerbank.jpg', images: ['/assets/cat-powerbank.jpg'], rating: 4.8, lowLevel: 15 },
  { id: '1bac82ef-a37c-56b7-a290-afbddbd914a5', skuId: '75e0a2eb-54e6-552e-b99c-a956f8557592', sku: 'PQ20', name: 'PQ20 Power Bank, 20,000mAh', category: 'Power Banks', brand: 'Nigson', price: 28500, retail: 28500, cost: 18500, stock: 45, reserved: 2, status: 'Active', desc: 'Heavy-duty long endurance power bank with digital battery indicator.', image: '/assets/cat-powerbank.jpg', images: ['/assets/cat-powerbank.jpg'], rating: 4.9, lowLevel: 10 },
  { id: '4ad4e90d-ec4e-5caa-f7ad88e19e29', skuId: '7faca25c-8831-5149-84e4-47e037580cac', sku: 'PQ28', name: 'PQ28 Magnetic Wireless Power Bank, 5,000mAh', category: 'Power Banks', brand: 'Nigson', price: 24000, retail: 24000, cost: 15000, stock: 30, reserved: 1, status: 'Active', desc: 'Snap-on MagSafe compatible wireless power bank for smartphones.', image: '/assets/cat-powerbank.jpg', images: ['/assets/cat-powerbank.jpg'], rating: 4.9, lowLevel: 10 },
  { id: 'c7db7adb-726b-5173-a385-004b258ea128', skuId: '3e82bda0-2606-580f-98ef-a01e322d325a', sku: 'PQ30', name: 'PQ30 Power Bank, 30,000mAh', category: 'Power Banks', brand: 'Nigson', price: 38000, retail: 38000, cost: 25000, stock: 22, reserved: 1, status: 'Active', desc: 'High capacity multi-device charging station for frequent travelers.', image: '/assets/cat-powerbank.jpg', images: ['/assets/cat-powerbank.jpg'], rating: 4.7, lowLevel: 5 },
  { id: 'c995e043-e1e8-5979-b4ea-e6d1dedc7322', skuId: '3882394e-69bd-5c71-9b14-fcf7a82481bd', sku: 'PQ50', name: 'PQ50 Power Bank, 50,000mAh', category: 'Power Banks', brand: 'Nigson', price: 55000, retail: 55000, cost: 37000, stock: 18, reserved: 0, status: 'Active', desc: 'Commercial grade ultra power bank with camping flashlight and fast DC/PD output.', image: '/assets/cat-powerbank.jpg', images: ['/assets/cat-powerbank.jpg'], rating: 4.9, lowLevel: 5 },
  { id: '4351a029-79f9-57e0-94e8-466d7ad4ef19', skuId: 'ecbe959f-d3e9-5f25-8cb5-1718bf14972e', sku: 'ZS6', name: 'ZS6 Multi-Device Desktop Fast Charger', category: 'Chargers', brand: 'Nigson', price: 26000, retail: 26000, cost: 17000, stock: 25, reserved: 1, status: 'Active', desc: '6-port smart power workstation with organized desktop footprint.', image: '/assets/cat-charger.jpg', images: ['/assets/cat-charger.jpg'], rating: 4.8, lowLevel: 10 },
  { id: 'fa096df5-7313-5a7a-aa01-4475ee45b98f', skuId: '0df9e4fb-2794-55fe-a1f9-d8977114b7e9', sku: 'ZS8', name: 'ZS8 Ultra-Fast GaN Travel Charger 65W', category: 'Chargers', brand: 'Nigson', price: 31000, retail: 31000, cost: 20000, stock: 30, reserved: 2, status: 'Active', desc: 'Compact dual-port 65W laptop and phone fast charging adapter.', image: '/assets/cat-charger.jpg', images: ['/assets/cat-charger.jpg'], rating: 4.9, lowLevel: 10 }
];

const LS_PRODUCTS = 'nigson_admin_products';

function getInitialProducts(): AdminProduct[] {
  try {
    const raw = typeof localStorage !== 'undefined' ? localStorage.getItem(LS_PRODUCTS) : null;
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) {
        const existingIds = new Set(parsed.map((p: any) => p.sku || p.id));
        const missing = SEED_PRODUCTS.filter(s => !existingIds.has(s.sku) && !existingIds.has(s.id));
        return [...parsed, ...missing];
      }
    }
  } catch { }
  return [...SEED_PRODUCTS];
}

export const SEED_ORDERS: AdminOrder[] = [
  {
    id: "ord_101",
    number: "ORD-NG-10021",
    date: new Date(Date.now() - 3600000 * 4).toISOString(),
    customer: { name: "Adewale Adeleke", email: "adewale@gmail.com", phone: "+234 803 123 4567", address: "Victoria Island, Lagos" },
    items: [{ sku: "NS03", name: "NS03 Wireless Earbuds (TWS)", qty: 2, price: 18500 }],
    total: 37000,
    paymentStatus: "Paid",
    status: "Processing",
    delivery: "Express Delivery",
    timeline: [{ label: "Placed", date: new Date(Date.now() - 3600000 * 4).toISOString() }]
  },
  {
    id: "ord_102",
    number: "ORD-NG-10022",
    date: new Date(Date.now() - 3600000 * 24).toISOString(),
    customer: { name: "Folake Bakare", email: "folake@gmail.com", phone: "+234 802 345 6789", address: "Maitama, Abuja" },
    items: [{ sku: "PQ20", name: "PQ20 Power Bank, 20,000mAh", qty: 1, price: 28500 }],
    total: 28500,
    paymentStatus: "Paid",
    status: "Shipped",
    delivery: "Standard Delivery",
    timeline: [{ label: "Placed", date: new Date(Date.now() - 3600000 * 24).toISOString() }]
  },
  {
    id: "ord_103",
    number: "ORD-NG-10023",
    date: new Date(Date.now() - 3600000 * 48).toISOString(),
    customer: { name: "Emeka Okafor", email: "emeka.okafor@gmail.com", phone: "+234 809 987 6543", address: "Alaba Int'l Market, Lagos" },
    items: [{ sku: "NS2526C", name: "NS2526C High Power Fast Wall Charger, GaN65W", qty: 5, price: 16000 }],
    total: 80000,
    paymentStatus: "Paid",
    status: "Delivered",
    delivery: "Standard Delivery",
    timeline: [{ label: "Delivered", date: new Date(Date.now() - 3600000 * 12).toISOString() }]
  }
];

const LS_ORDERS = 'nigson_admin_orders';

function getInitialOrders(): AdminOrder[] {
  try {
    const raw = typeof localStorage !== 'undefined' ? localStorage.getItem(LS_ORDERS) : null;
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
  } catch { }
  return [...SEED_ORDERS];
}

const ALL_SECTIONS: SectionKey[] = ["dashboard", "pages", "products", "reviews", "distributors", "quotes", "inquiries", "inventory", "orders", "customers", "analytics", "blog", "admins", "roles"];

@Injectable({ providedIn: 'root' })
export class AdminService {
  readonly session = inject(SessionService);
  readonly http = inject(HttpClient);
  readonly catalog = inject(CatalogService);
  readonly errors = signal<Record<string, string>>({});
  readonly roles = signal<AdminRole[]>(Object.entries(ROLE_PERMISSIONS).map(([id, permissions]) => ({ id, name: id.replaceAll('_', ' '), permissions })));
  readonly admins = signal<AdminUser[]>([]);
  readonly products = signal<AdminProduct[]>([]);
  readonly pages = signal<AdminPage[]>([]);
  readonly reviews = signal<AdminReview[]>([]);
  readonly applications = signal<AdminApplication[]>([]);
  readonly loadingApplications = signal(false);
  readonly applicationError = signal<string | null>(null);
  readonly distributorMarket = signal<{ id: string; code: string; currency: string } | null>(null);
  readonly quotes = signal<AdminQuote[]>([]);
  readonly quoteLoadError = signal<string | null>(null);
  readonly inquiries = signal<AdminInquiry[]>([]);
  readonly orders = signal<AdminOrder[]>([]);
  readonly customers = signal<AdminCustomer[]>([]);
  readonly posts = signal<AdminPost[]>([]);
  readonly stockLocations = signal<Array<{ id: string; code: string; name: string }>>([]);
  readonly stockLog = signal<StockLog[]>([]);
  readonly activity = signal<Activity[]>([]);
  readonly currentRole = computed(() => this.roles().find(r => this.session.roles().includes(r.id)) ?? null);
  private loadedFor = '';
  private productsRequest?: Observable<AdminProduct[]>;

  constructor() {
    effect(() => {
      const key = this.session.authenticated() && this.session.isStaff() ? this.session.userId() + ':' + this.session.roles().join(',') : '';
      untracked(() => {
        if (key === this.loadedFor) return;
        this.loadedFor = key;
        this.admins.set([]); this.products.set([]); this.orders.set([]); this.customers.set([]);
        this.quotes.set([]); this.applications.set([]); this.inquiries.set([]); this.reviews.set([]); this.stockLog.set([]); this.activity.set([]); this.errors.set({});
        if (key) this.loadInitialStaffData();
      });
    });
  }

  can(section: SectionKey): boolean { return canAccessSection(this.session.roles(), section); }
  isSuper(): boolean { return this.session.isAdmin(); }
  log(text: string): void { this.activity.update(rows => [{ id: crypto.randomUUID(), text, date: new Date().toISOString() }, ...rows].slice(0, 40)); }
  message(error: any): string {
    if (error?.error?.code === 'MARKET_RULE_VIOLATION') {
      return 'The Nigeria market is not activated, so catalogue and pricing data are unavailable. Activate the market with approved tax, returns, price-book, delivery and provider configuration before editing products.';
    }
    if (error?.error?.code === 'CATALOGUE_RULE_VIOLATION') {
      return 'Cannot publish product: the product must have at least one uploaded product image before it can be published in Nigeria. Please upload a primary image in Slot 1, or change Catalog Status to "Draft" to save without publishing.';
    }
    return error?.error?.detail || error?.message || 'The operation failed. Reload the current record and try again.';
  }
  private fail(key: string, error: any): Observable<never> { this.errors.update(all => ({ ...all, [key]: this.message(error) })); return throwError(() => error); }
  private clear(key: string): void { this.errors.update(all => { const copy = { ...all }; delete copy[key]; return copy; }); }
  private headers(version?: number): HttpHeaders {
    if (version !== undefined && (!Number.isSafeInteger(version) || version < 0)) throw new Error('The current record version is missing. Reload before saving.');
    let headers = new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID() });
    if (version !== undefined) headers = headers.set('If-Match', `"${version}"`);
    return headers;
  }
  private rows(path: string, page = 0, local = false): Observable<any[]> {
    return this.http.get<any>(`${path}${path.includes('?') ? '&' : '?'}page=${page}&size=100`, local ? {context:this.applicationContext()} : {}).pipe(switchMap(result => {
      const rows = result?.items ?? result?.content;
      if (!Array.isArray(rows)) throw new Error('The backend returned an invalid table response.');
      return page + 1 < result.totalPages ? this.rows(path, page + 1, local).pipe(map(next => [...rows, ...next])) : of(rows);
    }));
  }
  private table<T>(key: string, request: Observable<any[]>, target: WritableSignal<T[]>): Observable<T[]> {
    this.clear(key);
    return request.pipe(tap(rows => target.set(rows)), catchError(error => { target.set([]); this.errors.update(all => ({ ...all, [key]: this.message(error) })); return of([]); }));
  }
  loadInitialStaffData(): void {
    if (!this.session.authenticated() || !this.session.isStaff()) return;
    this.http.get<any[]>('/api/v1/markets').subscribe({ next: rows => this.distributorMarket.set(rows.find(row => row.code === 'NG') ?? null), error: error => this.errors.update(all => ({ ...all, markets: this.message(error) })) });
    if (this.session.hasRole('CONTENT_EDITOR') || this.session.hasRole('OPERATIONS')) this.loadStaffProducts().subscribe();
    if (this.session.hasRole('OPERATIONS')) { this.loadStaffOrders().subscribe(); this.loadStockLocations().subscribe(); this.loadStockMovements().subscribe(); }
    if (this.session.hasRole('SALES')) { this.loadStaffCustomers().subscribe(); this.loadStaffQuotes().subscribe(); this.loadStaffInquiries().subscribe(); this.loadStaffApplications().subscribe(); }
    if (this.session.hasRole('ADMIN')) this.loadStaffUsers().subscribe();
    if (this.session.hasRole('CONTENT_PUBLISHER')) this.loadStaffReviews().subscribe();
  }
  // Kept for existing view initialization; server state is the only source of records.
  hydrateFromStorage(): void { }
  addLocalApplication(_application: Partial<AdminApplication>): void { throw new Error('The application must be acknowledged by the backend before it appears in the admin portal.'); }
  addLocalInquiry(_inquiry: Partial<AdminInquiry>): void { throw new Error('The inquiry must be acknowledged by the backend before it appears in the admin portal.'); }
  addLocalQuote(_quote: Partial<AdminQuote>): void { throw new Error('The quote request must be acknowledged by the backend before it appears in the admin portal.'); }
  persistProducts(): void { }
  persistOrders(): void { }
  persistApplications(): void { }
  persistQuotes(): void { }
  persistInquiries(): void { }

  loadStaffProducts(): Observable<AdminProduct[]> {
    if (this.productsRequest) return this.productsRequest;
    if (!this.session.hasRole('CONTENT_EDITOR')) return this.loadOperationalProducts();
    const request = forkJoin({ catalogue: this.rows('/api/v1/staff/products'), balances: this.session.hasRole('OPERATIONS') ? this.rows('/api/v1/staff/inventory') : of([]) }).pipe(
      map(({ catalogue, balances }) => mapStaffProducts(catalogue, balances)),
      switchMap(products => products.length ? forkJoin(products.map(product => {
        const specs = product.specifications || {};

        if (!product.skuId || product.status !== 'ACTIVE') {
          return of({ ...product, price: 0, retail: undefined, priceSubtitle: 'Retail price is available after publication.' });
        }

        return this.http.get<any>(`/api/v1/catalogue/skus/${product.skuId}/price?market=NG&quantity=${product.packSize || '1'}`, {
          context: new HttpContext().set(LOCAL_API_FAILURE_CODES, ['404:NOT_FOUND', '422:MARKET_RULE_VIOLATION'])
        }).pipe(
          map(price => {
            const fallbackPrice = Number(price.unitAmount);
            const resolvedPrice = fallbackPrice;
            return {
              ...product,
              price: resolvedPrice,
              retail: resolvedPrice,
              wholesale: undefined,
              distributor: undefined,
              priceSubtitle: product.priceSubtitle || specs['price_subtitle']
            };
          }),
          catchError(error => of({
            ...product,
            price: 0,
            retail: undefined,
            priceSubtitle: error?.status === 404 ? 'No public retail price is available for this SKU.' : this.message(error)
          }))
        );
      })) : of([]))
    );
    this.productsRequest = this.table('Products', request, this.products).pipe(
      finalize(() => { this.productsRequest = undefined; }),
      shareReplay({ bufferSize: 1, refCount: true })
    );
    return this.productsRequest;
  }
  private loadOperationalProducts():Observable<AdminProduct[]> {
    if(!this.session.hasRole('OPERATIONS'))return of([]);
    return this.table('Inventory',this.rows('/api/v1/staff/inventory').pipe(map(balances=>{
      const ids=[...new Set<string>(balances.map(row=>row.skuId))];
      return ids.map(skuId=>{
        const known=this.products().find(product=>product.skuId===skuId);
        const rows=balances.filter(row=>row.skuId===skuId);
        return {id:skuId,skuId,sku:known?.sku??skuId,name:known?.name??('SKU '+skuId),category:known?.category??'',price:0,desc:'',image:known?.image??'',status:'UNKNOWN' as const,...inventoryTotals(rows),rating:0};
      });
    })),this.products);
  }
  loadStaffInventory(): Observable<any[]> {
    return this.rows('/api/v1/staff/inventory').pipe(tap(rows => this.products.update(products => applyInventory(products, rows))), catchError(error => this.fail('Inventory', error)));
  }
  loadStockLocations(): Observable<any[]> {
    return this.table('Warehouses', this.http.get<any[]>('/api/v1/staff/stock-locations'), this.stockLocations);
  }
  loadInventory(): Observable<any[]> { return this.loadStaffInventory(); }
  loadStockMovements(): Observable<StockLog[]> {
    return this.table('Stock movements', this.rows('/api/v1/staff/inventory-movements').pipe(map(rows => rows.map(row => {
      const supplierMatch = typeof row.reason === 'string' ? row.reason.match(/Supplier:\s*([^—]+)/i) : null;
      const refMatch = typeof row.reason === 'string' ? row.reason.match(/Reference:\s*([^—]+)/i) : null;
      return {
        ...row,
        supplier: row.supplier || (supplierMatch ? supplierMatch[1].trim() : undefined),
        ref: row.ref || (refMatch ? refMatch[1].trim() : undefined),
        qty: Math.abs(Number(row.change)),
        change: Number(row.change)
      };
    }))), this.stockLog);
  }
  postStockAdjustment(sku: string, change: number, reason: string, meta?: { supplier?: string; ref?: string; notes?: string; locationId?: string }): Observable<any> {
    return defer(() => {
      this.clear('Stock adjustment');
      if (!Number.isFinite(change) || change === 0) throw new Error('Enter a non-zero stock adjustment.');
      const product = this.products().find(p => p.sku === sku || p.skuId === sku || p.id === sku);
      const skuId = product?.skuId || (/^[a-f0-9-]{36}$/i.test(sku) ? sku : null);
      if (!skuId) throw new Error('Choose an existing SKU from the inventory.');
      const explanation = [reason, meta?.supplier && `Supplier: ${meta.supplier}`, meta?.ref && `Reference: ${meta.ref}`, meta?.notes].filter(Boolean).join(' — ').trim();
      if (explanation.length < 10 || explanation.length > 500) throw new Error('Provide a stock adjustment reason between 10 and 500 characters.');
      return this.rows(`/api/v1/staff/inventory?skuId=${skuId}`).pipe(switchMap(balances => {
        let balance = meta?.locationId ? balances.find(row => row.locationId === meta.locationId) : balances.length === 1 ? balances[0] : null;
        if (!balance && meta?.locationId && this.stockLocations().some(row => row.id === meta.locationId)) balance = { locationId: meta.locationId, version: 0 };
        if (!balance) throw new Error('Choose an existing warehouse before adjusting stock.');
        return this.http.post<any>('/api/v1/staff/stock-adjustments', { skuId, locationId: balance.locationId, delta: String(change), reason: explanation }, { headers: this.headers(balance.version) });
      }), switchMap(result => this.loadStaffProducts().pipe(map(() => result))), tap(() => { this.log('Stock adjustment recorded'); this.loadStockMovements().subscribe(); }));
    }).pipe(catchError(error => this.fail('Stock adjustment', error)));
  }
  adjustStock(sku: string, change: number, reason: string, meta?: any): void { this.postStockAdjustment(sku, change, reason, meta).subscribe({ error: () => { } }); }

  private productBody(payload: any, categories: any[], brands: any[]): any {
    const category = categories.find(row => row.id === payload.categoryId || row.name.toLowerCase() === (payload.category || payload.categoryName || '').toLowerCase());
    const categoryIds = category ? [category.id] : (!(payload.category || payload.categoryName || payload.categoryId) && payload.categoryIds?.length ? payload.categoryIds : []);
    if (!categoryIds.length) throw new Error('Choose a category returned by the backend.');
    const brandName = (typeof payload.brand === 'string' ? payload.brand : payload.brand?.name || '').trim();
    const requestedBrandId = payload.brand?.id || payload.brandId;
    const matchingBrands = brands.filter(row => brandName ? row.name.toLowerCase() === brandName.toLowerCase() : row.id === requestedBrandId);
    const brand = matchingBrands.find(row=>row.id===requestedBrandId) || (matchingBrands.length===1 ? matchingBrands[0] : undefined);
    if ((brandName || requestedBrandId) && !brand) throw new Error('Choose an existing active brand returned by the backend.');
    const brandId = brand?.id ?? null;
    const specifications = Object.fromEntries(Object.entries(payload.specifications || {}).filter(([, value]) => value !== undefined && value !== null && value !== ''));
    // Clean and validate specifications according to strict backend rules
    const cleanSpecs: Record<string, any> = {};
    for (const [key, val] of Object.entries<any>(payload.specifications || {})) {
      if (!/^[a-z][a-z0-9_]{0,79}$/.test(key)) continue;
      if (val === undefined || val === null || val === '') continue;
      if (typeof val === 'boolean') {
        cleanSpecs[key] = val;
      } else if (Array.isArray(val)) {
        const cleanedItems = (val as any[])
          .map((item: any) => String(item).replace(/[\x00-\x1f\x7f-\x9f]/g, '').trim())
          .filter(Boolean)
          .slice(0, 20);
        if (cleanedItems.length > 0) {
          cleanSpecs[key] = cleanedItems;
        }
      } else {
        const cleanedStr = String(val).replace(/[\x00-\x1f\x7f-\x9f]/g, ' ').trim().slice(0, 1000);
        if (cleanedStr) {
          cleanSpecs[key] = cleanedStr;
        }
      }
    }

    const rawSlug = (payload.slug || payload.name || '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    const slug = payload.id ? (rawSlug || 'product-' + Date.now()) : (rawSlug ? `${rawSlug}-${Date.now().toString(36)}` : 'product-' + Date.now());
    const description = (payload.description ?? payload.desc ?? '').trim() || (payload.name || '').trim();

    const body: any = {
      name: (payload.name || '').trim(),
      slug,
      description,
      brandId,
      categoryIds,
      specifications: cleanSpecs
    };
    if (payload.divisionCode && typeof payload.divisionCode === 'string') {
      body.divisionCode = payload.divisionCode;
    }
    return body;
  }
  createProduct(payload: any): Observable<any> {
    if (!payload.sku?.trim() || !payload.carrierPackaging) return throwError(() => new Error('SKU and measured shipping package are required before creating a product.'));
    return forkJoin({ categories: this.http.get<any[]>('/api/v1/staff/categories'), brands: this.http.get<any[]>('/api/v1/catalogue/brands') }).pipe(
      switchMap(({ categories, brands }) => this.http.post<any>('/api/v1/staff/products', this.productBody(payload, categories, brands), { headers: this.headers() })),
      switchMap(product => {
        payload.id = product.id;
        payload.version = product.version;
        if (!payload.sku?.trim()) throw new Error('The product draft was created, but a SKU code is required. Reload drafts before retrying.');
        const body = { code: payload.sku.trim(), variantName: payload.name.trim(), unit: 'unit', quantityScale: 0, packSize: '1', carrierPackaging: payload.carrierPackaging };
        return this.http.post<any>(`/api/v1/staff/products/${product.id}/skus`, body, { headers: this.headers(product.version) }).pipe(
          switchMap(() => this.http.get<any>(`/api/v1/staff/products/${product.id}`)),
          catchError(skuErr => {
            (skuErr as any).createdProduct = product;
            return throwError(() => skuErr);
          })
        );
      }),
      tap(() => { this.log('Product and SKU saved as a draft'); this.loadStaffProducts().subscribe(); }),
      catchError(error => this.fail('Save product', error))
    );
  }
  updateProduct(id: string, payload: any): Observable<any> {
    return forkJoin({ current: this.http.get<any>(`/api/v1/staff/products/${id}`), categories: this.http.get<any[]>('/api/v1/staff/categories'), brands: this.http.get<any[]>('/api/v1/catalogue/brands') }).pipe(
      switchMap(({ current, categories, brands }) => this.http.patch<any>(`/api/v1/staff/products/${id}`, this.productBody(payload, categories, brands), { headers: this.headers(current.version) })),
      tap(() => { this.log('Product changes saved'); this.loadStaffProducts().subscribe(); }), catchError(error => this.fail('Save product', error))
    );
  }
  publishProduct(id: string, action: 'PUBLISH' | 'ARCHIVE', reason = 'Catalogue publication updated by staff'): Observable<any> {
    return this.http.get<any>(`/api/v1/staff/products/${id}`).pipe(
      switchMap(product => {
        if (action === 'ARCHIVE') return of(product);
        return this.http.get<any[]>('/api/v1/markets').pipe(
          switchMap(markets => {
            const market = markets.find(row => row.code === 'NG');
            if (!market) throw new Error('The Nigeria market is not configured.');
            return this.http.put<any>(
              `/api/v1/staff/products/${id}/markets/${market.id}`,
              { eligible: true, reason: 'Product published for the Nigeria market by staff' },
              { headers: this.headers(product.version) }
            ).pipe(
              switchMap(() => this.http.get<any>(`/api/v1/staff/products/${id}`))
            );
          })
        );
      }),
      switchMap(currentProduct => this.http.post<any>(
        `/api/v1/staff/products/${id}/publication`,
        { action, reason },
        { headers: this.headers(currentProduct.version) }
      )),
      tap(() => this.loadStaffProducts().subscribe()),
      catchError(error => this.fail('Product publication', error))
    );
  }
  deleteProduct(idOrSku: string): Observable<boolean> {
    const id = this.products().find(p => p.id === idOrSku || p.sku === idOrSku)?.id || idOrSku;
    return this.publishProduct(id, 'ARCHIVE').pipe(map(() => true));
  }
  loadStaffUsers(): Observable<AdminUser[]> {
    return this.table('Staff users', this.rows('/api/v1/staff/users').pipe(map(rows => rows.filter(row => row.roles.length).map(row => ({ id: row.id, name: row.displayName, email: row.email || '', roleId: row.roles[0], roles: row.roles, status: 'Active' as const, version: row.version })))), this.admins);
  }
  updateUserRoles(userId: string, roles: string[], reason = 'Staff role assignment updated by administrator'): Observable<boolean> {
    return defer(() => {
      this.clear('Staff permissions');
      const current = this.admins().find(user => user.id === userId);
      if (!current || current.version == null) throw new Error('Reload the current staff record before changing roles.');
      return this.http.put<any>(`/api/v1/staff/users/${userId}/roles`, { roles, reason }, { headers: this.headers(current.version) }).pipe(switchMap(() => this.loadStaffUsers()), map(() => true));
    }).pipe(catchError(error => this.fail('Staff permissions', error)));
  }
  updateStaffUser(user: AdminUser, reason = 'Staff role assignment updated by administrator'): Observable<boolean> { return this.http.put<any>(`/api/v1/staff/users/${user.id}/roles`, { roles: user.status === 'Disabled' ? [] : user.roles || [user.roleId], reason }, { headers: this.headers(user.version) }).pipe(switchMap(() => this.loadStaffUsers()), map(() => true), catchError(e => this.fail('Staff permissions', e))); }
  addStaffUser(user: AdminUser, _password?: string, reason = 'Existing customer assigned staff permissions'): Observable<boolean> {
    const email = user.email.trim().toLowerCase();
    return this.http.get<any>(`/api/v1/staff/users/lookup?email=${encodeURIComponent(email)}`).pipe(
      switchMap(party => this.http.put<any>(`/api/v1/staff/users/${party.id}/roles`, { roles: [...new Set([...(Array.isArray(party.roles) ? party.roles : []), user.roleId])], reason }, { headers: this.headers(party.version) })),
      switchMap(() => this.loadStaffUsers()),
      map(() => true),
      catchError(error => this.fail('Assign staff roles', error))
    );
  }
  deleteStaffUser(userId: string): Observable<boolean> { return this.updateUserRoles(userId, [], 'Staff access removed by administrator'); }
  loadStaffCustomers(): Observable<AdminCustomer[]> {
    return this.table('Customers', this.rows('/api/v1/staff/customers').pipe(map(rows => rows.map(row => ({ ...row, email: row.email || '', phone: row.phone || '', location: '', saved: 0, status: row.status === 'ACTIVE' ? 'Active' as const : 'Suspended' as const })))), this.customers);
  }
  updateCustomerStatus(_id: string, _status: 'Active' | 'Suspended'): void { this.errors.update(all => ({ ...all, Customers: 'Customer account suspension is not available through this portal. Staff access is managed on Admin Users.' })); }
  saveCustomer(_customer: AdminCustomer): void { throw new Error('Customer profiles are managed by their account owners.'); }
  loadStaffOrders(): Observable<AdminOrder[]> {
    const orders = this.rows('/api/v1/staff/orders');
    const afterSales = this.http.get<any>('/api/v1/staff/orders/after-sales').pipe(catchError(() => of({ items: [] })));
    return this.table('Orders', forkJoin({ orders, afterSales }).pipe(map(({ orders: rows, afterSales: summary }) => {
      const attention = new Map<string, any>();
      for (const item of summary?.items || []) if (!attention.has(item.orderId)) attention.set(item.orderId, item);
      return rows.map(order => ({
      id: order.id, number: order.orderNumber, date: order.createdAt, version: order.version, rawState: order.status, cancellation: order.cancellation ?? null,
      afterSales: attention.get(order.id) ?? null,
      customer: { name: order.address?.recipient || '', email: '', phone: order.address?.phone || '', address: [order.address?.line1, order.address?.city, order.address?.region].filter(Boolean).join(', ') },
      items: (order.lines || []).map((line: any) => ({ lineId: line.id, sku: line.skuCode, name: line.name, qty: Number(line.quantity), price: Number(line.unitAmount) })), total: Number(order.totals.grandTotal), paymentStatus: order.paymentStatus === 'PAID' ? 'Paid' as const : order.paymentStatus === 'REFUNDED' ? 'Refunded' as const : 'Pending' as const,
      status: ({ PENDING: 'Pending', PAID: 'Confirmed', PROCESSING: 'Processing', SHIPPED: 'Shipped', DELIVERED: 'Delivered', CANCELLED: 'Cancelled' } as any)[order.status] || order.status,
      delivery: order.fulfillmentStatus, timeline: []
      }));
    })), this.orders);
  }
  getRefund(id: string): Observable<AdminRefund> {
    return this.http.get<AdminRefund>(`/api/v1/staff/refunds/${id}`);
  }
  private refundApprovalKeys = new Map<string, string>();
  approveCancellationRefund(refund: AdminRefund): Observable<unknown> {
    const identity = `${refund.id}:${refund.version}`;
    let key = this.refundApprovalKeys.get(identity);
    if (!key) { key = crypto.randomUUID(); this.refundApprovalKeys.set(identity, key); }
    return this.http.post(`/api/v1/staff/refunds/${refund.id}/approval`,
      { approve: true, reason: 'Finance reviewed and approved the cancelled order refund' },
      { headers: this.headers(refund.version).set('Idempotency-Key', key) });
  }
  private returnKeys = new Map<string, string>();
  private returnHeaders(version: number, identity: string): HttpHeaders {
    let key = this.returnKeys.get(identity);
    if (!key) { key = crypto.randomUUID(); this.returnKeys.set(identity, key); }
    return this.headers(version).set('Idempotency-Key', key);
  }
  /** API-243: bounded return quantities and records for one staff-visible order. */
  getOrderReturns(orderId: string): Observable<OrderReturns> {
    this.clear('Returns');
    return this.http.get<any>(`/api/v1/staff/orders/${orderId}/returns`).pipe(
      map(raw => this.mapOrderReturns(raw)),
      catchError(error => this.fail('Returns', error))
    );
  }
  /** API-240: current Operations review; approval requires customer-facing instructions. */
  reviewReturn(orderId: string, item: OrderReturn, approve: boolean, reason: string, instructions: string): Observable<OrderReturns> {
    const identity = `review:${item.id}:${item.version}`;
    return this.http.post(`/api/v1/staff/returns/${item.id}/review`,
      approve ? { approve, reason, instructions } : { approve, reason },
      { headers: this.returnHeaders(item.version, identity) }).pipe(
        tap(() => this.returnKeys.delete(identity)),
        switchMap(() => this.getOrderReturns(orderId)),
        catchError(error => {
          if (error?.error?.code === 'VERSION_MISMATCH') this.returnKeys.delete(identity);
          return this.fail('Returns', error);
        })
      );
  }
  /** API-241: explicit physical receipt, external reference and restock decision. */
  receiveReturn(orderId: string, item: OrderReturn, receiptReference: string, restock: boolean, reason: string): Observable<OrderReturns> {
    const identity = `receipt:${item.id}:${item.version}`;
    return this.http.post(`/api/v1/staff/returns/${item.id}/receipt`,
      { receiptReference, restock, reason },
      { headers: this.returnHeaders(item.version, identity) }).pipe(
        tap(() => this.returnKeys.delete(identity)),
        switchMap(() => this.getOrderReturns(orderId)),
        catchError(error => {
          if (error?.error?.code === 'VERSION_MISMATCH') this.returnKeys.delete(identity);
          return this.fail('Returns', error);
        })
      );
  }
  /** API-242: Finance requests a chosen amount on the original verified Paystack settlement. */
  requestReturnRefund(orderId: string, item: OrderReturn, amount: string, reason: string): Observable<OrderReturns> {
    const identity = `refund:${item.id}:${item.version}`;
    return this.http.post(`/api/v1/staff/returns/${item.id}/refund`,
      { amount, reason },
      { headers: this.returnHeaders(item.version, identity) }).pipe(
        tap(() => this.returnKeys.delete(identity)),
        switchMap(() => this.getOrderReturns(orderId)),
        catchError(error => {
          if (error?.error?.code === 'VERSION_MISMATCH') this.returnKeys.delete(identity);
          return this.fail('Returns', error);
        })
      );
  }
  private mapOrderReturns(raw: any): OrderReturns {
    const quantity = (value: unknown) => Number(value ?? 0) || 0;
    return {
      orderId: raw?.orderId ?? '',
      orderVersion: Number(raw?.orderVersion ?? 0) || 0,
      delivered: raw?.delivered === true,
      lines: (raw?.lines ?? []).map((line: any) => ({
        orderLineId: line.orderLineId, name: line.name,
        orderedQuantity: quantity(line.orderedQuantity), availableQuantity: quantity(line.availableQuantity),
        quantityScale: Number(line.quantityScale ?? 0) || 0
      })),
      requests: (raw?.requests ?? []).map((item: any): OrderReturn => ({
        id: item.id, orderId: item.orderId, orderLineId: item.orderLineId, productName: item.productName,
        quantity: quantity(item.quantity), reason: item.reason, policyRevision: item.policyRevision,
        state: item.state, decisionReason: item.decisionReason ?? null, instructions: item.instructions ?? null,
        receiptReference: item.receiptReference ?? null, restock: typeof item.restock === 'boolean' ? item.restock : null,
        receivedAt: item.receivedAt ?? null, refundId: item.refundId ?? null, refundState: item.refundState ?? null,
        version: Number(item.version ?? 0) || 0, createdAt: item.createdAt ?? ''
      })),
      refundableAmount: quantity(raw?.refundableAmount),
      paystackRefundAvailable: raw?.paystackRefundAvailable === true
    };
  }
  setOrderStatus(id: string, status: string): void {
    const order = this.orders().find(row => row.id === id);
    if (status !== 'Processing') { this.errors.update(all => ({ ...all, Orders: 'Shipping and delivery require a recorded shipment and stock allocation. They cannot be changed as a display status.' })); return; }
    this.http.post(`/api/v1/staff/orders/${id}/processing`, { reason: 'Order released for processing by operations' }, { headers: this.headers(order?.version) }).pipe(switchMap(() => this.loadStaffOrders()), catchError(error => this.fail('Order processing', error))).subscribe({ error: () => { } });
  }
  getOrderFulfillment(orderId: string): Observable<{ orderId: string; lines: Array<{ orderLineId: string; balanceId: string; quantity: string }>; shipment: { id: string; status: string; version: number; trackingUrl: string | null } | null }> {
    return this.http.get<{ orderId: string; lines: Array<{ orderLineId: string; balanceId: string; quantity: string }>; shipment: { id: string; status: string; version: number; trackingUrl: string | null } | null }>(`/api/v1/staff/orders/${orderId}/fulfillment`).pipe(catchError(error => this.fail('Fulfillment', error)));
  }
  createManualShipment(orderId: string, lines: Array<{ orderLineId: string; balanceId: string; quantity: string }>): Observable<any> {
    return this.http.post(`/api/v1/staff/orders/${orderId}/shipments`, { lines, provider: 'MANUAL', providerReference: `LOCAL-${orderId.slice(0, 8)}` }, { headers: this.headers().set('Idempotency-Key', crypto.randomUUID()) }).pipe(catchError(error => this.fail('Fulfillment', error)));
  }
  dispatchShipment(shipment: { id: string; version: number }): Observable<any> {
    return this.http.post(`/api/v1/staff/shipments/${shipment.id}/dispatch`, { reason: 'Local test shipment dispatched by operations' }, { headers: this.headers(shipment.version).set('Idempotency-Key', crypto.randomUUID()) }).pipe(catchError(error => this.fail('Fulfillment', error)));
  }
  deliverShipment(shipment: { id: string; version: number }): Observable<any> {
    return this.http.post(`/api/v1/staff/shipments/${shipment.id}/delivery`, { deliveredAt: new Date().toISOString(), reason: 'Local test delivery confirmed by operations' }, { headers: this.headers(shipment.version).set('Idempotency-Key', crypto.randomUUID()) }).pipe(catchError(error => this.fail('Fulfillment', error)));
  }
  loadStaffQuotes(): Observable<AdminQuote[]> {
    this.quoteLoadError.set(null);
    return this.table('Quote requests', this.rows('/api/v1/staff/leads?type=QUOTE').pipe(map(rows => rows.map(row => {
      let qty = 0;
      const qtyMatch = (row.message || '').match(/(?:Qty|Quantity):\s*([0-9.]+)/i);
      if (qtyMatch) {
        qty = Number(qtyMatch[1]) || 0;
      }

      let sku = '';
      const skuMatch = (row.message || '').match(/SKU:\s*([A-Za-z0-9_-]+)/i);
      if (skuMatch) {
        sku = skuMatch[1];
      }

      let product = row.subject || 'Quote request';
      if (product.toLowerCase().startsWith('quote request - ')) {
        product = product.slice(16).trim();
      }

      return {
        id: row.id,
        number: 'REQ-' + row.id.slice(0, 8),
        customer: row.name,
        email: row.email,
        phone: row.phone || '',
        product: product,
        sku: sku,
        quantity: qty,
        message: row.message,
        date: row.createdAt,
        version: row.version,
        status: ({ NEW: 'Pending', ASSIGNED: 'Under Review', IN_PROGRESS: 'Under Review', RESOLVED: 'Quoted', CLOSED: 'Closed' } as any)[row.state] || 'Pending'
      };
    }))), this.quotes).pipe(tap(() => this.quoteLoadError.set(this.errors()['Quote requests'] || null)));
  }
  private updateLead(id: string, version: number | undefined, state: string, note: string): Observable<any> {
    return defer(() => { if (version == null) throw new Error('Reload the current lead before saving.'); return this.http.patch<any>(`/api/v1/staff/leads/${id}`, { state, note }, { headers: this.headers(version) }); });
  }
  setQuoteStatus(id: string, status: string): void {
    const row = this.quotes().find(row => row.id === id);
    const state = ({ Pending: 'NEW', 'Under Review': 'IN_PROGRESS', Quoted: 'RESOLVED', Closed: 'CLOSED' } as any)[status];
    this.updateLead(id, row?.version, state, 'Quote request status updated by staff').pipe(switchMap(() => this.loadStaffQuotes()), catchError(error => this.fail('Quote request update', error))).subscribe({ error: () => { } });
  }
  loadStaffInquiries(): Observable<AdminInquiry[]> {
    return this.table(
      'Contact inquiries',
      this.rows('/api/v1/staff/leads?type=CONTACT',0,true).pipe(
        map(rows =>
          rows
            .filter(row => !isPartnerSubmission({ subject: row.subject, message: row.message }))
            .map(row => ({
              id: row.id,
              name: row.name,
              email: row.email,
              phone: row.phone || '',
              subject: row.subject,
              message: row.message,
              date: row.createdAt,
              version: row.version,
              status: ({ NEW: 'New', ASSIGNED: 'In Progress', IN_PROGRESS: 'In Progress', RESOLVED: 'Resolved', CLOSED: 'Resolved' } as any)[row.state]
            }))
        )
      ),
      this.inquiries
    );
  }
  setInquiryStatus(id: string, status: string): void {
    const row = this.inquiries().find(row => row.id === id);
    this.updateLead(id, row?.version, status === 'Resolved' ? 'RESOLVED' : 'IN_PROGRESS', 'Contact inquiry updated by staff').pipe(switchMap(() => this.loadStaffInquiries()), catchError(error => this.fail('Inquiry update', error))).subscribe({ error: () => { } });
  }
  private parseLeadApplication(lead: any): AdminApplication {
    const msg = lead.message || '';
    const subj = lead.subject || '';
    const type: AdminApplication['type'] =
      subj.includes('Wholesale') || msg.includes('Wholesale Trade')
        ? 'Wholesaler'
        : subj.includes('Affiliate') || msg.includes('Affiliate Partner')
          ? 'Affiliate Marketer'
          : 'Distributor';

    const getField = (prefix: string): string => {
      const line = msg.split('\n').find((l: string) => l.toLowerCase().startsWith(prefix.toLowerCase()));
      return line ? line.split(':').slice(1).join(':').trim() : '';
    };

    const businessName = getField('Store / Company:')
      || getField('Legal Entity:')
      || getField('Company / Brand:')
      || (subj.includes(':') ? subj.split(':')[1].split('(')[0].trim() : lead.name);
    const contactPerson = getField('Contact Person:')
      || getField('Managing Director:')
      || getField('Full Name / Creator:')
      || lead.name;
    const location = getField('Trading Hub / State:')
      || getField('Assigned Territory:')
      || getField('Operating Territory:')
      || getField('Head Office:')
      || 'Nigeria';
    const category = getField('Product Category:') || getField('Primary Niche:') || 'Accessories';
    const code = (lead.id || '').replace(/-/g, '').slice(0, 4).toUpperCase();
    const prefix = type === 'Wholesaler' ? 'APP-W' : type === 'Affiliate Marketer' ? 'APP-A' : 'APP-D';
    const leadState = lead.state || 'NEW';

    return {
      id: lead.id,
      number: `${prefix}${code}`,
      type,
      source: 'lead',
      businessName: businessName || lead.name || '',
      contactPerson: contactPerson || lead.name || '',
      email: lead.email || '',
      phone: lead.phone || '',
      location,
      category,
      date: lead.createdAt || '',
      status: leadDisplayState(leadState),
      rawState: leadState,
      note: lead.message,
      version: lead.version
    };
  }
  private affiliateContact(row:any):any {
    if(row.partnerType!=='AFFILIATE')return {};
    try{const d=JSON.parse(row.businessDetails||'{}');return {contactPerson:row.organizationName,email:d.email||'',phone:d.phone||'',location:d.location||'',note:['Channels: '+(d.channels||''),d.strategy?'Strategy: '+d.strategy:''].filter(Boolean).join('\n')};}catch{return {};}
  }
  private applicationContext():HttpContext {return new HttpContext().set(LOCAL_API_FAILURE_CODES,['0:undefined','401:AUTH_REQUIRED','403:ACCESS_DENIED','404:NOT_FOUND','409:VERSION_MISMATCH','412:VERSION_MISMATCH','422:BUSINESS_RULE_VIOLATION','422:FEATURE_DISABLED','422:VALIDATION_ERROR','409:INVALID_TRANSITION','422:INVALID_TRANSITION','500:INTERNAL_ERROR','503:PROVIDER_UNAVAILABLE','503:PROVIDER_OUTCOME_UNKNOWN']);}
  private acknowledgeApplication(id:string,result:any):void {
    if(!result?.state)return;
    this.applications.update(rows=>rows.map(row=>row.id===id?{...row,rawState:result.state,status:row.source==='lead'?leadDisplayState(result.state):distributorDisplayState(result.state),version:result.version??row.version}:row));
  }
  loadStaffApplications(): Observable<AdminApplication[]> {
    this.applicationError.set(null);
    this.loadingApplications.set(true);
    let distError: string | null = null;
    let leadError: string | null = null;

    const distributorApps$ = this.rows('/api/v1/staff/distributor-applications',0,true).pipe(
      switchMap(rows => rows.length
        ? forkJoin(rows.map(row => this.http.get<any>(`/api/v1/staff/distributor-applications/${row.id}`,{context:this.applicationContext()}).pipe(map(detail => ({ ...row, ...detail })))))
        : of([])),
      map(rows => rows.map(row => ({
        id: row.id,
        number: row.id.slice(0, 8),
        organizationId: row.organizationId,
        type: (row.partnerType === 'AFFILIATE' ? 'Affiliate Marketer' : row.partnerType === 'WHOLESALE' ? 'Wholesaler' : 'Distributor') as 'Affiliate Marketer' | 'Wholesaler' | 'Distributor',
        source: 'distributor' as const,
        businessName: row.organizationName || '',
        contactPerson: row.contactName || (row.businessReference ? `Reg: ${row.businessReference}` : ''),
        email: row.email || '',
        phone: row.phone || '',
        location: row.countryCode || '',
        category: row.partnerType === 'AFFILIATE' ? 'Affiliate' : row.partnerType === 'WHOLESALE' ? 'Wholesale business' : 'Corporate Distributor',
        partnerType:row.partnerType,
        date: row.submittedAt,
        status: distributorDisplayState(row.state),
        rawState: row.state,
        reviewerPartyId: row.reviewerPartyId ?? null,
        decisionReason: row.decisionReason ?? null,
        note: row.businessDetails || '',
        ...this.affiliateContact(row),
        businessReference: row.businessReference || '',
        reviewReason: row.reviewReason || '',
        documents: Array.isArray(row.documents)
          ? row.documents.map((d: any) => ({
              ...d,
              type: d.type ?? d.kind ?? 'Document',
              fileId: d.fileId ?? d.assetId ?? d.id ?? '',
              scanStatus: d.scanStatus ?? d.state ?? ''
            }))
          : [],
        version: row.version
      }))),
      catchError(error => {
        distError = this.message(error);
        return of(this.applications().filter(row=>row.source==='distributor'));
      })
    );

    const leadApps$ = this.rows('/api/v1/staff/leads?type=CONTACT').pipe(
      map(rows => rows
        .filter(row => isPartnerSubmission({ subject: row.subject, message: row.message }))
        .map(row => this.parseLeadApplication(row))
        .filter(app => app.type === 'Wholesaler' || app.type === 'Affiliate Marketer')),
      catchError(error => {
        leadError = this.message(error);
        return of([] as AdminApplication[]);
      })
    );

    this.clear('Distributor applications');
    this.clear('Partner leads');
    return forkJoin([distributorApps$, leadApps$]).pipe(
      map(([distributorApps, leadApps]) => {
        const combined = [...distributorApps, ...leadApps].sort(
          (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()
        );
        this.applications.update(previous=>combined.map(row=>{const known=previous.find(item=>item.id===row.id);return known?.version!=null && row.version!=null && known.version>row.version?known:row;}));
        const errs = [distError, leadError].filter((msg): msg is string => Boolean(msg));
        this.applicationError.set(errs.length > 0 ? errs.join(' ') : null);
        return this.applications();
      }),
      finalize(() => this.loadingApplications.set(false))
    );
  }
  refreshApplication(id:string):Observable<AdminApplication> {
    const row=this.applications().find(item=>item.id===id);
    if(!row) return throwError(()=>new Error('Refresh the application list first.'));
    if(row.source==='lead')return this.loadStaffApplications().pipe(map(rows=>{const refreshed=rows.find(item=>item.id===id);if(!refreshed)throw new Error('The lead is no longer in the current queue.');return refreshed;}));
    return this.http.get<any>('/api/v1/staff/distributor-applications/'+id,{context:this.applicationContext()}).pipe(map(detail=>({
      ...row,organizationId:detail.organizationId,note:detail.businessDetails || row.note,rawState:detail.state,status:distributorDisplayState(detail.state),version:detail.version,
      partnerType:detail.partnerType,...this.affiliateContact(detail),reviewerPartyId:detail.reviewerPartyId??null,reviewReason:detail.reviewReason??'',decisionReason:detail.decisionReason??null,
      documents:(detail.documents??[]).map((document:any)=>({...document,fileId:document.fileId,type:document.type,scanStatus:document.scanStatus}))
    })),tap(record=>this.applications.update(rows=>rows.map(item=>item.id===id?record:item))));
  }
  setApplicationStatus(id:string,status:string,note?:string,documentsVerified=false,expectedVersion?:number):Observable<any> {
    return defer(()=>{
      const row=this.applications().find(item=>item.id===id);
      if(!this.session.hasRole('SALES'))throw new Error('Sales access is required.');
      if(row?.version==null)throw new Error('Refresh the application before deciding.');
      if(expectedVersion!=null&&row.version!==expectedVersion)throw new Error('This record changed. Refresh it before deciding.');
      const reason=note?.trim()??'';
      if(!reason||reason.length>500)throw new Error('Enter a staff reason between 1 and 500 characters.');
      if(row.source==='lead'){
        if(!partnerLeadActionAllowed(row.rawState??'',status))throw new Error('This lead action is not available in the current state.');
        return this.http.patch<any>('/api/v1/staff/leads/'+id,{state:partnerLeadState(status),note:reason},{headers:this.headers(row.version),context:this.applicationContext()}).pipe(tap(result=>this.acknowledgeApplication(id,result)),switchMap(result=>this.loadStaffApplications().pipe(map(()=>result))));
      }
      const blocked=distributorActionBlock({partnerType:row.partnerType,state:row.rawState,reviewerPartyId:row.reviewerPartyId,documents:row.documents},status,this.session.userId(),this.distributorMarket()?.id);
      if(blocked)throw new Error(blocked);
      const {action,body}=distributorCommand(status,reason,documentsVerified,this.distributorMarket()?.id);
      return this.http.post<any>('/api/v1/staff/distributor-applications/'+id+'/'+action,body,{
        headers:this.headers(row.version),context:this.applicationContext()
      }).pipe(tap(result=>this.acknowledgeApplication(id,result)),switchMap(result=>this.loadStaffApplications().pipe(map(()=>result))));
    }).pipe(catchError(error=>throwError(()=>error)));
  }
  loadStaffReviews(): Observable<AdminReview[]> {
    return this.table('Reviews', this.rows('/api/v1/staff/reviews').pipe(map(rows => rows.map(row => ({ id: row.id, sku: '', product: row.productId || '', author: row.authorName || row.author || 'Customer', rating: row.rating, text: row.text || row.body, date: row.createdAt, status: row.state === 'PUBLISHED' ? 'Published' as const : 'Hidden' as const, version: row.version, state: row.state })))), this.reviews);
  }
  moderateReview(id: string, decision: 'PUBLISH' | 'WITHDRAW' | 'REJECT'): Observable<any> {
    return defer(() => { const row = this.reviews().find(row => row.id === id); if (row?.version == null) throw new Error('Reload the current review before moderating.'); return this.http.post<any>(`/api/v1/staff/reviews/${id}/moderation`, { decision, reason: 'Review moderated by content publisher' }, { headers: this.headers(row.version) }); }).pipe(switchMap(result => this.loadStaffReviews().pipe(map(() => result))), catchError(error => this.fail('Review moderation', error)));
  }
  loadCommerceReport(from?: string, to?: string): Observable<any> { return this.http.get('/api/v1/staff/reports/commerce', { context: new HttpContext().set(LOCAL_API_FAILURE_CODES,['422:FEATURE_DISABLED']), params: { from: from || new Date(Date.now() - 30 * 86400000).toISOString(), to: to || new Date().toISOString(), currency: 'NGN' } }).pipe(catchError(error => throwError(() => error))); }
  loadEngagementReport(from?: string, to?: string): Observable<any> { return this.http.get('/api/v1/staff/reports/engagement', { params: { from: from || new Date(Date.now() - 30 * 86400000).toISOString(), to: to || new Date().toISOString() } }).pipe(catchError(error => this.fail('Engagement report', error))); }
}

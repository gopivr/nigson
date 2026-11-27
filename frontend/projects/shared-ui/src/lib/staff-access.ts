export type SectionKey = 'dashboard'|'pages'|'products'|'reviews'|'distributors'|'quotes'|'inquiries'|'inventory'|'orders'|'customers'|'analytics'|'blog'|'admins'|'roles'|'affiliatePayouts'|'categories';
export type BackendRole = 'ADMIN'|'SALES'|'OPERATIONS'|'FINANCE'|'CONTENT_EDITOR'|'CONTENT_PUBLISHER'|'ANALYST';

/** Mirrors current owner API permissions; ADMIN is not an implicit domain grant. */
export const ROLE_PERMISSIONS:Record<BackendRole,SectionKey[]> = {
 ADMIN:['dashboard','admins','roles','affiliatePayouts'],
 SALES:['dashboard','orders','quotes','distributors','customers','inquiries'],
 OPERATIONS:['dashboard','orders','inventory'],
 FINANCE:['dashboard','orders','affiliatePayouts'],
 CONTENT_EDITOR:['dashboard','products','categories','pages','blog'],
 CONTENT_PUBLISHER:['dashboard','reviews'],
 ANALYST:['dashboard','analytics']
};
export function canAccessSection(roles:readonly string[],section:SectionKey):boolean {
 return roles.some(role=>ROLE_PERMISSIONS[role.toUpperCase() as BackendRole]?.includes(section));
}
export function staffLanding(roles:readonly string[]):string {
 const current=roles.map(role=>role.toUpperCase());
 if(current.includes('ADMIN'))return '/admin';
 for(const [role,path] of [['OPERATIONS','/admin/inventory'],['SALES','/admin/quotes'],['CONTENT_EDITOR','/admin/pages'],['CONTENT_PUBLISHER','/admin/reviews'],['ANALYST','/admin/analytics']] as const)if(current.includes(role))return path;
 return current.includes('FINANCE')?'/admin':'/account';
}
export function staffPathSection(url:string):SectionKey|undefined {
 const path=url.split(/[?#]/)[0].replace(/\/$/,'');
 if(path==='/admin')return 'dashboard';
 if(/^\/admin\/inventory\/products(?:\/(?:new|import)|\/[^/]+\/edit)?$/.test(path))return 'products';
 const routes:Record<string,SectionKey>={categories:'categories','affiliate-payouts':'affiliatePayouts',analytics:'analytics',users:'admins',roles:'roles',pages:'pages',reviews:'reviews',blog:'blog',inventory:'inventory',orders:'orders',customers:'customers',quotes:'quotes',applications:'distributors',inquiries:'inquiries'};
 const suffix=path.slice('/admin/'.length);
 if(path.startsWith('/admin/')&&routes[suffix])return routes[suffix];
 if(/^\/admin\/pages\/[^/]+$/.test(path))return 'pages';
 if(/^\/admin\/inventory\/(stock-in|adjustment|movements|low-stock|out-of-stock)$/.test(path))return 'inventory';
 return undefined;
}
export function safeReturnPath(raw:unknown):string|undefined {
 if(typeof raw!=='string'||!raw.startsWith('/')||raw.startsWith('//')||/[\\\x00-\x20]/.test(raw))return undefined;
 const path=raw.split(/[?#]/)[0];
 try { const decoded=decodeURIComponent(path);if(decoded.split('/').some(part=>part==='.'||part==='..')||decoded.includes('\\'))return undefined; } catch { return undefined; }
 if(staffPathSection(raw)||/^\/(account|checkout|cart|distributor|shop|products)(\/|$)/.test(path)||path==='/')return raw;
 return undefined;
}
export function loginDestination(roles:readonly string[],requested?:string):string {
 const target=safeReturnPath(requested);
 if(!target)return staffLanding(roles);
 if(target.startsWith('/admin')){const section=staffPathSection(target);return section&&canAccessSection(roles,section)?target:staffLanding(roles);}
 return target;
}

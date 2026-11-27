import {Component,computed,inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterLink} from '@angular/router';
import {AdminService,AdminHeadingComponent,BadgeComponent,ROLE_PERMISSIONS,canAccessSection} from 'shared-ui';
import type {BackendRole,SectionKey} from 'shared-ui';
import {LucideShieldCheck} from '@lucide/angular';

type RoleGuide={purpose:string;actions:string[];requirements:string[]};
const PAGE_LABELS:Record<SectionKey,string>={categories:'Categories',affiliatePayouts:'Affiliate payouts',dashboard:'Dashboard',pages:'Website pages',products:'Products',reviews:'Reviews',distributors:'Distributor applications',quotes:'Quote requests',inquiries:'Contact enquiries',inventory:'Inventory & stock',orders:'Orders',customers:'Customers',analytics:'Business analytics',blog:'Blog drafts',admins:'Staff accounts',roles:'Roles & permissions'};
const GUIDES:Record<BackendRole,RoleGuide>={
 ADMIN:{purpose:'Administers staff access and manages selling prices.',actions:['Review affiliate bank details and record monthly manual payouts with normal login.','Assign and remove staff roles.','Create, revise, activate and assign retail price books.'],requirements:['Selling-price changes use normal admin login, without OTP. Staff role changes still require recent MFA.','Other business areas require their own domain roles. Opening the product editor requires Content Editor access.']},
 SALES:{purpose:'Handles customer and sales enquiries.',actions:['Review customers, quotes and distributor applications.','Update supported lead and application workflows.'],requirements:['This role does not grant order processing, stock adjustments or price activation.','Each workflow still applies its configured eligibility and approval rules.']},
 OPERATIONS:{purpose:'Handles orders and physical stock.',actions:['View orders and release eligible paid orders for processing.','View inventory and record warehouse stock adjustments.'],requirements:['Catalogue editing requires Content Editor; publishing or archiving products requires Content Publisher too.','Inventory can identify items by SKU ID when catalogue metadata is not available to this role.']},
 FINANCE:{purpose:'Reviews financial order information.',actions:['Review affiliate bank details and record monthly manual payouts with normal login.','View orders, subject to the existing recent-MFA check.','Activate price books through the Finance approval workflow.'],requirements:['Finance order access and the non-admin Finance pricing workflow require recent MFA.','Analytics requires Analyst. This role does not grant Operations order processing.']},
 CONTENT_EDITOR:{purpose:'Maintains product information and content drafts.',actions:['Read and edit products, website drafts and blog drafts.','Save drafts without publishing them.'],requirements:['Publishing or archiving products, and the complete content publication workflow, also require Content Publisher.','Stock access requires Operations. Price changes require Admin, or the existing Sales and Finance approval roles.']},
 CONTENT_PUBLISHER:{purpose:'Moderates reviews and authorizes content publication.',actions:['Read the review queue and moderate reviews.','Publish content and products when the required read/edit roles are also assigned.'],requirements:['The current CMS draft API requires Content Editor. Publisher alone does not open the draft editor.','For the current complete CMS publication workflow, assign both Content Editor and Content Publisher when intended.']},
 ANALYST:{purpose:'Reads the aggregated Commerce analytics report.',actions:['View report sales, order/customer counts and available product and stock summaries.'],requirements:['Reports depend on configured backend feature availability.','This role does not grant individual customer, order, stock or content editing access.']}
};

@Component({
 selector:'app-admin-roles',standalone:true,
 imports:[CommonModule,RouterLink,AdminHeadingComponent,BadgeComponent,LucideShieldCheck],
 template:`
  <lib-admin-heading title="Roles & Permissions" subtitle="Understand the fixed roles, the pages they open, and the actions they allow.">
   <a routerLink="/admin/users" class="inline-flex rounded-full bg-brand px-5 py-2.5 text-sm font-bold text-white">Assign staff roles</a>
  </lib-admin-heading>

  <section class="mb-6 rounded-2xl border border-hairline bg-white p-5" aria-labelledby="combined-access-title">
   <h2 id="combined-access-title" class="font-extrabold text-ink">Your combined access</h2>
   <p class="mt-2 text-sm text-muted-ink">An account can have several roles. Its access combines those roles; the Admin card alone does not describe every page your account can use.</p>
   <p class="mt-4 text-[10px] font-bold uppercase tracking-widest text-muted-ink">Your assigned staff roles</p>
   <div class="mt-2 flex flex-wrap gap-2"><lib-badge *ngFor="let role of assignedRoles()" tone="neutral">{{ role.replaceAll('_',' ') }}</lib-badge></div>
   <p class="mt-4 text-[10px] font-bold uppercase tracking-widest text-muted-ink">Pages available to your account</p>
   <div class="mt-2 flex flex-wrap gap-2"><lib-badge *ngFor="let section of combinedPages()" tone="neutral">{{ pageLabel(section) }}</lib-badge></div>
   <p class="mt-4 text-sm text-muted-ink">Page access does not automatically allow every action on that page. The action requirements below still apply.</p>
  </section>

  <aside class="mb-6 rounded-2xl border border-brand/20 bg-brand/5 p-5">
   <h2 class="font-bold text-ink">Selling-price exception for admins</h2>
   <p class="mt-2 text-sm text-ink">An active Admin can change selling prices with normal login, without OTP. The product editor also needs Content Editor access to read the product. Other Finance and staff-management MFA checks remain in place.</p>
  </aside>

  <p class="mb-4 text-sm text-muted-ink">Each card describes one role. People with multiple roles are counted in each matching card. Assign roles on Staff Accounts; this page explains the fixed rules.</p>
  <div class="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
   <article *ngFor="let r of admin.roles()" class="rounded-2xl border border-hairline bg-white p-5 shadow-sm">
    <header class="flex items-start gap-3">
     <div class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand/10 text-brand"><svg lucideShieldCheck class="h-5 w-5"></svg></div>
     <div><h2 class="font-extrabold text-ink">{{ r.name }}</h2><p class="text-xs text-muted-ink">{{ membersCount(r.id) }} members with this role</p></div>
    </header>
    <p class="mt-4 text-sm text-muted-ink">{{ guide(r.id)?.purpose }}</p>
    <div class="mt-4 border-t border-hairline pt-4">
     <h3 class="text-[10px] font-bold uppercase tracking-widest text-muted-ink">Page access</h3>
     <div class="mt-2 flex flex-wrap gap-1.5"><lib-badge *ngFor="let section of r.permissions" tone="neutral">{{ pageLabel(section) }}</lib-badge></div>
    </div>
    <div class="mt-4">
     <h3 class="text-[10px] font-bold uppercase tracking-widest text-muted-ink">Allowed actions</h3>
     <ul class="mt-2 list-disc space-y-2 pl-4 text-sm text-ink"><li *ngFor="let action of guide(r.id)?.actions">{{ action }}</li></ul>
    </div>
    <div class="mt-4 rounded-xl bg-surface-alt p-3">
     <h3 class="text-[10px] font-bold uppercase tracking-widest text-muted-ink">Additional requirements & limits</h3>
     <ul class="mt-2 list-disc space-y-2 pl-4 text-xs text-muted-ink"><li *ngFor="let requirement of guide(r.id)?.requirements">{{ requirement }}</li></ul>
    </div>
   </article>
  </div>
 `
})
export class AdminRolesComponent {
 readonly admin=inject(AdminService);
 readonly assignedRoles=computed(()=>this.admin.session.roles().filter(role=>Object.prototype.hasOwnProperty.call(ROLE_PERMISSIONS,role)));
 readonly combinedPages=computed(()=>[...new Set(Object.values(ROLE_PERMISSIONS).flat())].filter(section=>canAccessSection(this.admin.session.roles(),section)));
 pageLabel(section:SectionKey):string{return PAGE_LABELS[section];}
 guide(role:string):RoleGuide|undefined{return GUIDES[role as BackendRole];}
 membersCount(roleId:string):number{return this.admin.admins().filter(user=>(user.roles||[user.roleId]).includes(roleId)).length;}
}

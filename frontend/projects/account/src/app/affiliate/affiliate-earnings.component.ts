import {Component,inject,OnInit,signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {AffiliateService,AffiliateEarnings} from 'shared-ui';
import {finalize} from 'rxjs';

@Component({selector:'app-affiliate-earnings',standalone:true,imports:[CommonModule],template:`
<details class="rounded-2xl border border-hairline bg-white text-ink">
  <summary class="cursor-pointer rounded-2xl p-6 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand md:px-8"><span class="font-semibold">Commission details</span><p class="mt-1 text-sm text-muted-ink">View the orders behind your earnings.</p></summary>
  <div class="space-y-4 border-t border-hairline p-6 md:px-8">
   <div class="flex flex-wrap items-center justify-between gap-3"><p class="max-w-xl text-sm leading-relaxed text-muted-ink">Commission is 5% after merchandise discounts, excluding shipping and tax. Orders clear seven days after complete delivery. These amounts are before payout deductions; your remaining balance is shown above.</p><button type="button" (click)="load(result()?.page || 0)" [disabled]="loading()" class="rounded-full border border-hairline px-4 py-2 text-sm font-semibold disabled:opacity-50">Refresh</button></div>
   <p *ngIf="loading()" role="status" class="text-sm text-muted-ink">Loading commissions...</p><p *ngIf="error()" role="alert" class="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">{{error()}}</p>
   <ng-container *ngIf="result() as earnings">
    <p *ngIf="!earnings.items.length" class="rounded-xl bg-surface p-6 text-center text-sm text-muted-ink">No qualifying paid orders yet. Share a product to get started.</p>
    <div *ngIf="earnings.items.length" class="overflow-x-auto rounded-xl border border-hairline"><table class="w-full min-w-[560px] text-left text-sm"><thead class="border-b border-hairline bg-surface text-xs uppercase tracking-wide text-muted-ink"><tr><th class="p-4">Order</th><th class="p-4">Commission</th><th class="p-4">Status</th><th class="p-4">Clears from</th></tr></thead><tbody class="divide-y divide-hairline"><tr *ngFor="let item of earnings.items"><td class="max-w-[200px] break-all p-4 text-xs">{{item.orderNumber}}</td><td class="p-4 font-semibold">{{item.net | currency:earnings.currency:'symbol-narrow'}}<span *ngIf="item.reversed!=='0.00'" class="mt-1 block text-xs font-normal text-muted-ink">Refund adjustment: {{item.reversed | currency:earnings.currency:'symbol-narrow'}}</span></td><td class="p-4">{{item.state==='HELD'?'On hold':item.state==='AVAILABLE'?'Cleared':item.state==='REVERSED'?'Refunded':'Pending'}}</td><td class="p-4 text-muted-ink">{{item.availableAt?(item.availableAt | date:'mediumDate'):'After delivery'}}</td></tr></tbody></table></div>
    <nav *ngIf="earnings.totalPages>1" aria-label="Commission pages" class="flex items-center justify-between gap-3 text-sm"><button type="button" (click)="load(earnings.page-1)" [disabled]="loading() || earnings.page===0" class="rounded-full border border-hairline px-4 py-2 disabled:opacity-40">Previous</button><span class="text-muted-ink">Page {{earnings.page+1}}</span><button type="button" (click)="load(earnings.page+1)" [disabled]="loading() || earnings.page+1>=earnings.totalPages" class="rounded-full border border-hairline px-4 py-2 disabled:opacity-40">Next</button></nav>
   </ng-container>
  </div>
 </details>
`})
export class AffiliateEarningsComponent implements OnInit {
 private affiliate=inject(AffiliateService);readonly result=signal<AffiliateEarnings|null>(null);readonly loading=signal(false);readonly error=signal('');
 ngOnInit(){this.load(0);}
 load(page:number){if(this.loading()||page<0)return;this.loading.set(true);this.error.set('');this.affiliate.earnings(page).pipe(finalize(()=>this.loading.set(false))).subscribe({next:value=>this.result.set(value),error:()=>{this.result.set(null);this.error.set('Earnings could not be loaded. Please try again.');}});}
}

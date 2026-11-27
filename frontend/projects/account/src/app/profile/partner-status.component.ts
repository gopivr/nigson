import {Component,DestroyRef,effect,inject,signal,untracked} from '@angular/core';
import {CommonModule} from '@angular/common';
import {HttpClient,HttpContext} from '@angular/common/http';
import {RouterLink} from '@angular/router';
import {Subscription} from 'rxjs';
import {SessionService,LOCAL_API_FAILURE_CODES,apiFailureDetail,BackendDistributorApplication} from 'shared-ui';
@Component({selector:'app-partner-status',standalone:true,imports:[CommonModule,RouterLink],template:`
<section class="rounded-2xl border border-hairline bg-white p-6 space-y-3">
 <div class="flex items-center justify-between gap-3"><h2 class="font-bold">Partner applications</h2><button type="button" (click)="refresh()" [disabled]="loading()" class="text-sm font-semibold text-brand">Refresh status</button></div>
 <p class="text-sm text-muted-ink">Your customer account stays active. You may hold one partner type: wholesaler, distributor or affiliate.</p>
 <p *ngIf="loading()" role="status">Loading applications...</p><p *ngIf="error()" role="alert" class="text-sm text-red-700">{{ error() }}</p>
 <p *ngIf="!loading() && !error() && !rows().length" class="text-sm">No partner applications submitted yet.</p>
 <div *ngFor="let row of rows()" class="rounded-xl border border-hairline p-3 text-sm"><strong>{{ row.partnerType === 'AFFILIATE' ? 'Affiliate' : row.partnerType === 'WHOLESALE' ? 'Wholesaler' : 'Distributor' }}</strong><span class="ml-3">{{ label(row.state) }}</span><p class="mt-1 text-muted-ink">Submitted {{ row.submittedAt | date:'mediumDate' }}</p><p *ngIf="row.state==='APPROVED'" class="mt-2">{{ row.partnerType==='AFFILIATE' ? 'Affiliate application approved.' : 'Business access approved. Business purchasing requires a valid price assignment.' }}</p><a *ngIf="row.partnerType==='AFFILIATE' && row.state==='APPROVED'" routerLink="/account/affiliate" class="mt-2 inline-block underline">Open affiliate product links</a></div>
 <a routerLink="/distributor" class="inline-block text-sm font-semibold text-brand">View partner applications</a>
</section>`})
export class PartnerStatusComponent {
 private http=inject(HttpClient);private session=inject(SessionService);private request?:Subscription;
 readonly rows=signal<BackendDistributorApplication[]>([]);readonly loading=signal(false);readonly error=signal('');
 constructor(){inject(DestroyRef).onDestroy(()=>this.request?.unsubscribe());effect(()=>{const user=this.session.userId();const authenticated=this.session.authenticated();untracked(()=>{this.request?.unsubscribe();this.rows.set([]);this.error.set('');this.loading.set(false);if(user&&authenticated)this.refresh();});});}
 refresh():void{if(!this.session.authenticated()||this.loading())return;this.error.set('');this.loading.set(true);this.request=this.http.get<BackendDistributorApplication[]>('/api/v1/me/distributor-applications',{context:new HttpContext().set(LOCAL_API_FAILURE_CODES,['AUTH_REQUIRED','ACCESS_DENIED','PROVIDER_UNAVAILABLE','FEATURE_DISABLED'])}).subscribe({next:rows=>{this.rows.set(rows);this.loading.set(false);},error:error=>{this.error.set(apiFailureDetail(error,'Unable to load partner applications.'));this.loading.set(false);}});}
 label(state:string):string{return ({SUBMITTED:'Submitted',UNDER_REVIEW:'Under review',ACTIVATION_PENDING:'Approval processing',APPROVED:'Approved',REJECTED:'Rejected',SUSPENSION_PENDING:'Suspension processing',SUSPENDED:'Suspended'} as Record<string,string>)[state]??'Unknown status';}
}

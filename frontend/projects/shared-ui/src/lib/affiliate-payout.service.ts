import {Injectable,inject} from '@angular/core';
import {HttpClient,HttpHeaders} from '@angular/common/http';
export interface AffiliateBank {id:string;version:number;revision:number;bankName:string;accountName:string;accountLast4:string;accountNumber:string|null;status:string;reviewReason:string|null;}
export interface AffiliatePayout {id:string;version:number;period:string;state:string;amount:string;transferReference:string|null;createdAt:string;paidAt:string|null;}
export interface AffiliatePayoutOverview {partyId:string;name:string;affiliateState:string;bank:AffiliateBank|null;balance:{pending:string;available:string;held:string;reversed:string;paid:string;reserved:string;recovery:string};payouts:AffiliatePayout[];page:number;totalPages:number;canPrepare:boolean;canTransfer:boolean;}
@Injectable({providedIn:'root'})
export class AffiliatePayoutService {
 private http=inject(HttpClient);
 private headers(){return {headers:new HttpHeaders({'Idempotency-Key':crypto.randomUUID()})};}
 own(page=0){return this.http.get<AffiliatePayoutOverview>('/api/v1/me/affiliate/payouts',{params:{page:String(page)}});}
 save(bank:{expectedBankId:string|null;bankName:string;accountName:string;accountNumber:string}){return this.http.put<AffiliatePayoutOverview>('/api/v1/me/affiliate/bank-details',bank,this.headers());}
 list(page=0){return this.http.get<{items:AffiliatePayoutOverview[];page:number;totalPages:number}>('/api/v1/staff/affiliates',{params:{page:String(page)}});}
 detail(party:string,page=0){return this.http.get<AffiliatePayoutOverview>('/api/v1/staff/affiliates/'+encodeURIComponent(party)+'/payouts',{params:{page:String(page)}});}
 review(party:string,bank:AffiliateBank,decision:string,reason:string){return this.http.post('/api/v1/staff/affiliates/'+encodeURIComponent(party)+'/bank-review',{bankId:bank.id,version:bank.version,decision,reason},this.headers());}
 prepare(party:string,bank:AffiliateBank){return this.http.post<AffiliatePayout>('/api/v1/staff/affiliates/'+encodeURIComponent(party)+'/payouts',{bankId:bank.id},this.headers());}
 resolve(payout:AffiliatePayout,paid:boolean,value:string,confirmed:boolean){return this.http.post<AffiliatePayout>('/api/v1/staff/affiliate-payouts/'+encodeURIComponent(payout.id)+(paid?'/paid':'/cancel'),paid?{version:payout.version,transferReference:value,confirmTransferred:confirmed}:{version:payout.version,reason:value,confirmNoTransfer:confirmed},this.headers());}
}

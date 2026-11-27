import '@angular/compiler';
import {signal} from '@angular/core';
import {of,firstValueFrom} from 'rxjs';
import {describe,it,expect,vi} from 'vitest';
import {AdminService} from './admin.service';
function setup(state='SUBMITTED',actor='reviewer'){
 let detail:any={id:'app',organizationName:'Company',state,reviewerPartyId:state==='UNDER_REVIEW'?'first-reviewer':null,version:1,documents:[{fileId:'file',type:'REGISTRATION',scanStatus:'CLEAN'}]};
 const row:any={id:'app',number:'APP',source:'distributor',type:'Distributor',rawState:state,status:'Pending',version:1,reviewerPartyId:detail.reviewerPartyId,documents:detail.documents};
 const http={get:vi.fn((url:string)=>of(url.includes('/distributor-applications/')?detail:url.includes('/distributor-applications?')?{items:[detail],totalPages:1}:{items:[],totalPages:1})),post:vi.fn((url:string)=>{detail={...detail,state:url.endsWith('/review')?'UNDER_REVIEW':'ACTIVATION_PENDING',reviewerPartyId:url.endsWith('/review')?actor:detail.reviewerPartyId,version:2};return of({state:detail.state,version:2});}),patch:vi.fn()};
 const service=Object.create(AdminService.prototype) as AdminService;Object.assign(service,{http,applications:signal([row]),admins:signal([]),errors:signal({}),applicationError:signal(null),loadingApplications:signal(false),distributorMarket:signal({id:'market',code:'NG'}),session:{hasRole:(role:string)=>role==='SALES',userId:()=>actor}});
 return {service,http};
}
describe('Distributor owner requests',()=>{
 it('does not issue a decision for submitted or self-reviewed applications',async()=>{
  const pending=setup();await expect(firstValueFrom(pending.service.setApplicationStatus('app','Approved','Decision'))).rejects.toThrow('review');expect(pending.http.post).not.toHaveBeenCalled();
  const own=setup('UNDER_REVIEW','first-reviewer');await expect(firstValueFrom(own.service.setApplicationStatus('app','Approved','Decision'))).rejects.toThrow('Another');expect(own.http.post).not.toHaveBeenCalled();
 });
 it('sends a versioned verified review and preserves the returned reviewer',async()=>{
  const {service,http}=setup();await firstValueFrom(service.setApplicationStatus('app','Under Review','Documents inspected',true,1));
  expect(http.post.mock.calls[0][1]).toEqual({reason:'Documents inspected',documentsVerified:true});expect(service.applications()[0].reviewerPartyId).toBe('reviewer');expect(service.applications()[0].rawState).toBe('UNDER_REVIEW');
 });
 it('keeps backend activation pending after an independent approval',async()=>{
  const {service}=setup('UNDER_REVIEW','approver');await firstValueFrom(service.setApplicationStatus('app','Approved','Independent approval',false,1));expect(service.applications()[0].status).toBe('Activation Pending');
 });
 it('rejects an obsolete open-dialog version before issuing a request',async()=>{
  const {service,http}=setup();await expect(firstValueFrom(service.setApplicationStatus('app','Under Review','Review',true,0))).rejects.toThrow('changed');expect(http.post).not.toHaveBeenCalled();
 });
});

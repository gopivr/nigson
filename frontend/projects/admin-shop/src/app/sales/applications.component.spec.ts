import '@angular/compiler';
import {Injector,signal} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {of,Subject,throwError} from 'rxjs';
import {describe,it,expect,vi} from 'vitest';
import {AdminService} from 'shared-ui';
import {AdminApplicationsComponent} from './applications.component';
const base={id:'application',number:'APP',type:'Distributor',source:'distributor',businessName:'Test company',contactPerson:'Applicant',email:'',phone:'',location:'NG',category:'Corporate Distributor',date:'2026-11-14T00:00:00Z',status:'Pending',rawState:'SUBMITTED',version:1,reviewerPartyId:null,documents:[{fileId:'file',scanStatus:'CLEAN'}]};
function setup(overrides:any={},actor='reviewer'){
 const record={...base,...overrides};const applications=signal<any[]>([record]);
 const admin={applications,admins:signal([]),applicationError:signal(null),loadingApplications:signal(false),distributorMarket:signal({id:'market',code:'NG'}),session:{hasRole:(role:string)=>role==='SALES',userId:()=>actor},loadStaffApplications:vi.fn(()=>of(applications())),refreshApplication:vi.fn(()=>of(applications()[0])),setApplicationStatus:vi.fn(()=>of({state:'REJECTED',version:2}))};
 const injector=Injector.create({providers:[{provide:AdminService,useValue:admin},{provide:AdminApplicationsComponent,useFactory:()=>new AdminApplicationsComponent()}]});
 const component=injector.get(AdminApplicationsComponent);component.viewDetail(record);return {component,admin,record};
}
describe('Guided distributor decisions',()=>{
 it('blocks direct approval of a pending record and approval by its reviewer',()=>{
  const pending=setup();pending.component.reviewerNote='Approve after checks';pending.component.act('Approved');expect(pending.admin.setApplicationStatus).not.toHaveBeenCalled();
  const reviewed=setup({rawState:'UNDER_REVIEW',status:'Under Review',reviewerPartyId:'reviewer'});reviewed.component.reviewerNote='Approve';reviewed.component.act('Approved');expect(reviewed.admin.setApplicationStatus).not.toHaveBeenCalled();expect(reviewed.component.actionError()).toContain('Another');
 });
 it('requires document confirmation and prevents duplicate review requests',()=>{
  const {component,admin}=setup();const response=new Subject<any>();admin.setApplicationStatus.mockReturnValue(response);
  component.reviewerNote='Registration documents inspected';component.act('Under Review');expect(admin.setApplicationStatus).not.toHaveBeenCalled();
  component.documentsVerified=true;component.act('Under Review');component.act('Under Review');expect(admin.setApplicationStatus).toHaveBeenCalledTimes(1);expect(component.busy()).toBe('Under Review');
  response.next({state:'UNDER_REVIEW',version:2});response.complete();expect(component.view()?.rawState).toBe('UNDER_REVIEW');expect(component.busy()).toBeNull();
 });
 it('keeps an acknowledged pending activation distinct from approved',()=>{
  const {component,admin}=setup({rawState:'UNDER_REVIEW',status:'Under Review',reviewerPartyId:'first-reviewer',version:2},'approver');admin.setApplicationStatus.mockReturnValue(of({state:'ACTIVATION_PENDING',version:3}));
  component.reviewerNote='Independent approval';component.act('Approved');expect(component.view()?.status).toBe('Activation Pending');expect(component.success()).toContain('not yet confirmed');expect(component.editable(component.view()!)).toBe(true);expect(component.block('Approved')).toBeTruthy();expect(component.block('Retry activation')).toBeNull();
 });
 it('requires a separate rejection confirmation and rejection reason',()=>{
  const {component,admin}=setup();component.act('Rejected');expect(admin.setApplicationStatus).not.toHaveBeenCalled();component.rejecting.set(true);component.act('Rejected');expect(admin.setApplicationStatus).not.toHaveBeenCalled();
  component.rejectionReason='Registration evidence is invalid';component.act('Rejected');expect(admin.setApplicationStatus).toHaveBeenCalledWith('application','Rejected','Registration evidence is invalid',false,1);expect(component.view()?.status).toBe('Rejected');
 });
 it('requires refresh after an uncertain mutation and catches its error locally',()=>{
  const {component,admin}=setup({rawState:'UNDER_REVIEW',status:'Under Review',reviewerPartyId:'other'},'approver');admin.setApplicationStatus.mockReturnValue(throwError(()=>new HttpErrorResponse({status:503,error:{code:'PROVIDER_OUTCOME_UNKNOWN'}})));
  component.reviewerNote='Independent approval';component.act('Approved');component.act('Approved');expect(admin.setApplicationStatus).toHaveBeenCalledTimes(1);expect(component.success()).toBe('');expect(component.detailReady()).toBe(false);expect(component.actionError()).toContain('Refresh');
 });
 it('does not allow actions after the latest detail read fails',()=>{
  const {component,admin,record}=setup();admin.refreshApplication.mockReturnValue(throwError(()=>new Error('Detail unavailable')));component.viewDetail(record);component.reviewerNote='Review';component.documentsVerified=true;component.act('Under Review');expect(admin.setApplicationStatus).not.toHaveBeenCalled();expect(component.detailReady()).toBe(false);
 });
 it('uses lead resolution actions without distributor approval semantics',()=>{
  const {component,admin}=setup({source:'lead',type:'Wholesaler',rawState:'NEW',status:'New'});component.reviewerNote='Contact request handled';component.act('Approved');expect(admin.setApplicationStatus).not.toHaveBeenCalled();
  admin.setApplicationStatus.mockReturnValue(of({state:'RESOLVED',version:2}));component.act('Resolve');expect(component.view()?.status).toBe('Resolved');expect(admin.setApplicationStatus.mock.calls[0][1]).toBe('Resolve');
 });
});

it('preserves category filtering and phone search while switching partner tabs',()=>{
 const {component,admin,record}=setup();admin.applications.set([{...record,category:'Earbuds'},{...record,id:'second',category:'Cables',phone:'0800123'}]);
 component.catFilter.set('Earbuds');expect(component.filteredList()).toHaveLength(1);
 component.searchQuery.set('0800123');expect(component.filteredList()).toHaveLength(0);
 component.catFilter.set('All categories');expect(component.filteredList()[0].id).toBe('second');
 component.changeTab('Wholesalers');expect(component.catFilter()).toBe('All categories');
});

it('shows reviewed wholesale applications on the wholesale tab and preserves independent approval checks',()=>{const {component,record}=setup({type:'Wholesaler',rawState:'UNDER_REVIEW',status:'Under Review',reviewerPartyId:'reviewer'});expect(component.tabRows()).toHaveLength(0);component.changeTab('Wholesalers');expect(component.tabRows()).toHaveLength(1);expect(component.stats().find(x=>x.label==='Under review')?.value).toBe(1);component.view.set(record as any);expect(component.block('Approved')).toContain('Another');});

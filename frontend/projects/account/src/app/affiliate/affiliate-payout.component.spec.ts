import '@angular/compiler';
import {Injector} from '@angular/core';
import {of,throwError} from 'rxjs';
import {describe,it,expect,vi} from 'vitest';
import {AffiliatePayoutService} from 'shared-ui';
import {AffiliatePayoutComponent} from './affiliate-payout.component';
const account:any={partyId:'party',name:'Affiliate',affiliateState:'ACTIVE',bank:null,balance:{available:'0.00',reserved:'0.00',paid:'0.00',recovery:'0.00'},payouts:[],page:0,totalPages:0,canPrepare:false,canTransfer:false};
function fixture(service:any){return Injector.create({providers:[{provide:AffiliatePayoutService,useValue:service},{provide:AffiliatePayoutComponent,useFactory:()=>new AffiliatePayoutComponent()}]}).get(AffiliatePayoutComponent);}
describe('Affiliate bank form',()=>{
 it('sends actual bank details and clears the full number after confirmed save',()=>{const saved={...account,bank:{id:'bank',status:'PENDING',bankName:'Synthetic',accountName:'Synthetic Affiliate',accountLast4:'6789',accountNumber:null}};const service={own:vi.fn(()=>of(account)),save:vi.fn(()=>of(saved))};const c=fixture(service);c.ngOnInit();c.bankName='Synthetic';c.accountName='Synthetic Affiliate';c.accountNumber='0123456789';c.save();expect(service.save).toHaveBeenCalledWith({expectedBankId:null,bankName:'Synthetic',accountName:'Synthetic Affiliate',accountNumber:'0123456789'});expect(c.accountNumber).toBe('');expect(c.result()?.bank?.status).toBe('PENDING');expect(c.notice()).toContain('saved for review');});
 it('rejects an incomplete account number without sending a command',()=>{const service={own:()=>of(account),save:vi.fn()};const c=fixture(service);c.ngOnInit();c.accountNumber='123';c.save();expect(service.save).not.toHaveBeenCalled();expect(c.error()).toContain('10-digit');});
 it('does not announce success when the save outcome is uncertain',()=>{const service={own:()=>of(account),save:()=>throwError(()=>new Error('connection lost'))};const c=fixture(service);c.ngOnInit();c.bankName='Synthetic';c.accountName='Synthetic';c.accountNumber='0123456789';c.save();expect(c.notice()).toBe('');expect(c.error()).toContain('Refresh before retrying');expect(c.busy()).toBe(false);});
});

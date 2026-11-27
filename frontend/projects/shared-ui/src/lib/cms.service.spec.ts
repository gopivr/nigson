import '@angular/compiler';
import {Injector} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of,throwError} from 'rxjs';
import {describe,it,expect,vi} from 'vitest';
import {CmsService} from './cms.service';
import {SessionService} from './session.service';
function setup(){
 const http={get:vi.fn(()=>of({items:[],totalPages:1})),post:vi.fn()};
 const session={hasRole:(_role:string)=>true};
 const injector=Injector.create({providers:[{provide:HttpClient,useValue:http},{provide:SessionService,useValue:session},{provide:CmsService,useFactory:()=>new CmsService()}]});
 return {service:injector.get(CmsService),http,session};
}
describe('Admin content persistence',()=>{
 it('uses each acknowledged revision version throughout publication',()=>{
  const {service,http}=setup();
  http.post.mockImplementation((url:string,_body:any,options:any)=>{
   const stage=url.endsWith('/review')?1:url.endsWith('/approval')?2:url.endsWith('/publication')?3:0;
   if(stage)expect(options.headers['If-Match']).toBe(`"${stage-1}"`);
   return of({id:'revision',version:stage,state:['DRAFT','REVIEW','APPROVED','PUBLISHED'][stage]});
  });
  const next=vi.fn();service.executePublishPipeline('home').subscribe(next);
  expect(next).toHaveBeenCalledWith(true);expect(http.post).toHaveBeenCalledTimes(4);
  expect(http.post.mock.calls[0][1].metadata).toEqual(expect.objectContaining({title:'Home | Nigson',robots:'INDEX',structuredData:{}}));
  expect(service.isPublishing()).toBe(false);expect(service.published()['home'].status).toBe('Published');
 });
 it('does not publish or report success when approval is rejected',()=>{
  const {service,http}=setup();const failure={error:{detail:'Approval denied'},status:403};
  http.post.mockImplementation((url:string)=>url.endsWith('/approval')?throwError(()=>failure):of({id:'revision',version:url.endsWith('/review')?1:0,state:url.endsWith('/review')?'REVIEW':'DRAFT'}));
  const next=vi.fn(),error=vi.fn();service.executePublishPipeline('home').subscribe({next,error});
  expect(next).not.toHaveBeenCalled();expect(error).toHaveBeenCalledWith(failure);expect(service.error()).toBe('Approval denied');expect(service.isPublishing()).toBe(false);expect(service.published()['home'].status).toBe('Draft');expect(http.post).toHaveBeenCalledTimes(3);
 });
 it('uses the current content item version when editing an existing draft',()=>{
  const {service,http}=setup();http.get.mockReturnValue(of({items:[{type:'HOME',slug:'home',locale:'en',contentItemId:'item',itemVersion:8}],totalPages:1}));http.post.mockReturnValue(of({id:'revision',version:0,state:'DRAFT'}));
  service.saveDraft('home').subscribe();expect(http.post.mock.calls[0][0]).toBe('/api/v1/staff/content/item/revisions');expect(http.post.mock.calls[0][2].headers['If-Match']).toBe('"8"');
 });
});

describe('CMS role-specific actions',()=>{
 it('lets an editor save drafts but does not send a publication request',()=>{
  const {service,http,session}=setup();session.hasRole=role=>role==='CONTENT_EDITOR';
  const error=vi.fn();service.publishContent({id:'revision',state:'DRAFT',version:0}).subscribe({error});
  expect(error).toHaveBeenCalled();expect(http.post).not.toHaveBeenCalled();expect(service.canPublish()).toBe(false);
 });
});

it('rejects a full publish pipeline before creating a draft for an editor-only user',()=>{
 const {service,http,session}=setup();session.hasRole=role=>role==='CONTENT_EDITOR';
 const error=vi.fn();service.executePublishPipeline('home').subscribe({error});
 expect(error).toHaveBeenCalled();expect(http.post).not.toHaveBeenCalled();
});

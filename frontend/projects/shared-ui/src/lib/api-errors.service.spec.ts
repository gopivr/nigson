import '@angular/compiler';
import {HttpContext,HttpErrorResponse,HttpRequest} from '@angular/common/http';
import {Injector,runInInjectionContext} from '@angular/core';
import {throwError} from 'rxjs';
import {describe,it,expect} from 'vitest';
import {ApiErrorsService,apiFailureDetail,apiErrorInterceptor,LOCAL_API_FAILURE_CODES} from './api-errors.service';
describe('Actionable API errors',()=>{
 it('explains a plain proxy 502 without claiming the backend rejected it',()=>{
  const value=new ApiErrorsService().describe(new HttpErrorResponse({status:502,statusText:'Bad Gateway',error:''}),'GET','/bff/session');
  expect(value).toContain('API proxy could not reach the backend');expect(value).not.toContain('backend rejected');
 });
 it('keeps server configuration guidance and correlation references',()=>{
  const error=new HttpErrorResponse({status:503,error:{detail:'Service unavailable',correlationId:'safe-reference',violations:[{field:'document',message:'Document storage is not configured.'}]}});
  expect(apiFailureDetail(error)).toContain('Document storage is not configured.');expect(new ApiErrorsService().describe(error)).toContain('safe-reference');
 });
 it('explains an unactivated market as configuration, not a missing resource',()=>{
  const error=new HttpErrorResponse({status:422,error:{code:'MARKET_RULE_VIOLATION',detail:'The market operation does not meet approved selling rules.',correlationId:'market-reference'}});
  const value=new ApiErrorsService().describe(error,'GET','/api/v1/catalogue/categories?market=NG');
  expect(value).toContain('not activated for selling');expect(value).toContain('market-reference');
  expect(value).not.toContain('The requested resource is unavailable');
 });
});

describe('Locally handled pricing failures', () => {
 function requestFailure(status:number,code:string,local:boolean) {
  const errors=new ApiErrorsService();
  const injector=Injector.create({providers:[{provide:ApiErrorsService,useValue:errors}]});
  const failure=new HttpErrorResponse({status,error:{code,detail:'Unavailable'}});
  const context=new HttpContext();
  if(local)context.set(LOCAL_API_FAILURE_CODES,['404:NOT_FOUND','422:MARKET_RULE_VIOLATION']);
  const request=new HttpRequest('GET','/api/v1/catalogue/skus/sku/price',{context});
  let received:unknown;
  runInInjectionContext(injector,()=>apiErrorInterceptor(request,()=>throwError(()=>failure)))
   .subscribe({error:error=>{received=error;}});
  expect(received).toBe(failure);
  return errors.failures();
 }
 it('leaves expected price failures to the product view without swallowing them',()=>{
  expect(requestFailure(404,'NOT_FOUND',true)).toHaveLength(0);
  expect(requestFailure(422,'MARKET_RULE_VIOLATION',true)).toHaveLength(0);
 });
 it('continues reporting authentication, server and unhandled failures',()=>{
  expect(requestFailure(401,'AUTH_REQUIRED',true)).toHaveLength(1);
  expect(requestFailure(500,'INTERNAL_ERROR',true)).toHaveLength(1);
  expect(requestFailure(404,'NOT_FOUND',false)).toHaveLength(1);
 });
});

describe('Page-scoped API errors',()=>{
 it('keeps staff-role errors on their originating page',()=>{
  const errors=new ApiErrorsService();errors.report('Role update denied','/admin/users');
  expect(errors.visibleFor('/admin/users')).toHaveLength(1);expect(errors.visibleFor('/admin/applications')).toHaveLength(0);
 });
 it('keeps explicitly global session failures visible',()=>{
  const errors=new ApiErrorsService();errors.report('Session unavailable');expect(errors.visibleFor('/admin/applications')).toHaveLength(1);
 });
});

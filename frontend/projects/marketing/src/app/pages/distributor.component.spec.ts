import '@angular/compiler';
import { HttpClient } from '@angular/common/http';
import { Injector, signal } from '@angular/core';
import { of, Subject } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SessionService, MarketingService } from 'shared-ui';
import { DistributorComponent } from './distributor.component';

function setup(authenticated = true) {
  const requests: Subject<any>[] = [];
  const http = { get: vi.fn(), post: vi.fn() };
  const submitEnquiry = vi.fn(() => of({ success: true, status: 201 }));
  const submitApplication = vi.fn(() => {
    const request = new Subject<any>();
    requests.push(request);
    return request;
  });
  const session = { authenticated: signal(authenticated), userId: signal('party-id'), signIn: vi.fn() };
  const injector = Injector.create({ providers: [
    { provide: SessionService, useValue: session },
    { provide: MarketingService, useValue: { submitEnquiry, submitDistributorApplication: submitApplication } },
    { provide: HttpClient, useValue: http },
    { provide: DistributorComponent, useFactory: () => new DistributorComponent() }
  ] });
  const component = injector.get(DistributorComponent);
  Object.assign(component.dist, {
    organizationName: 'Example Trading Ltd',
    businessReference: 'RC12345',
    territory: 'Lagos',
    directorName: 'Example Director',
    email: 'director@example.com',
    phone: '+2348012345678',
    headOfficeAddress: 'Lagos'
  });
  return { component, submitEnquiry, submitApplication, requests, session, http };
}

afterEach(() => vi.unstubAllGlobals());

describe('Distributor form submission', () => {
  it('requires a verified uploaded document for an authenticated application', () => {
    const { component, submitEnquiry, submitApplication } = setup();
    component.onDistributorSubmit(new Event('submit'));
    expect(submitEnquiry).not.toHaveBeenCalled();
    expect(submitApplication).not.toHaveBeenCalled();
    expect(component.submitError()).toContain('Upload a business-registration document');
  });

  it('sends a real uploaded document ID to the application API', () => {
    const { component, submitEnquiry, submitApplication, requests } = setup();
    const documentId = '00000000-0000-4000-8000-000000000001';
    component.selectedDocumentId = documentId;
    component.documentUploadState.set('CLEAN');
    component.onDistributorSubmit(new Event('submit'));
    expect(submitEnquiry).not.toHaveBeenCalled();
    expect(submitApplication).toHaveBeenCalledWith(expect.objectContaining({ documentIds: [documentId] }),expect.any(String));
    requests[0].next({ id: 'application-id', state: 'SUBMITTED' });
    requests[0].complete();
    expect(component.distributorApplicationSubmitted()).toBe(true);
  });

  it('does not report success when the application API rejects the submission', () => {
    const { component, submitApplication, requests } = setup();
    component.selectedDocumentId = '00000000-0000-4000-8000-000000000001';
    component.documentUploadState.set('CLEAN');
    component.onDistributorSubmit(new Event('submit'));
    requests[0].error({ error: { code: 'ACCESS_DENIED' } });
    expect(submitApplication).toHaveBeenCalledOnce();
    expect(component.distributorSubmitted()).toBe(false);
    expect(component.submitError()).toBeTruthy();
  });

  it('requires sign-in without creating an enquiry or claiming application success', () => {
    const { component, submitEnquiry, submitApplication, session } = setup(false);
    component.onDistributorSubmit(new Event('submit'));
    expect(submitEnquiry).not.toHaveBeenCalled();
    expect(submitApplication).not.toHaveBeenCalled();
    expect(component.distributorSubmitted()).toBe(false);
    expect(component.distributorApplicationSubmitted()).toBe(false);
    expect(component.submitError()).toContain('Sign in');
    expect(session.signIn).not.toHaveBeenCalled();
  });

  it('uploads a real proof through the API and waits for clean scan confirmation', async () => {
    const { component, http, submitEnquiry, submitApplication } = setup();
    const assetId = '00000000-0000-4000-8000-000000000001';
    http.post
      .mockReturnValueOnce(of({
        assetId,
        uploadUrl: 'https://storage.example/upload',
        requiredHeaders: { 'content-type': 'application/pdf' }
      }))
      .mockReturnValueOnce(of({ id: assetId, state: 'QUARANTINED' }));
    http.get.mockReturnValueOnce(of({ url: 'https://storage.example/download' }));
    vi.stubGlobal('crypto', {
      randomUUID: vi.fn().mockReturnValue('00000000-0000-4000-8000-000000000002'),
      subtle: { digest: vi.fn().mockResolvedValue(new Uint8Array(32).buffer) }
    });
    const storageFetch = vi.fn().mockResolvedValue({ ok: true, status: 200 });
    vi.stubGlobal('fetch', storageFetch);

    const file = new File(['proof'], 'registration.pdf', { type: 'application/pdf' });
    Object.defineProperty(file, 'arrayBuffer', {
      value: vi.fn().mockResolvedValue(new TextEncoder().encode('proof').buffer)
    });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: [file] });
    await component.onDocumentSelected({ target: input } as unknown as Event);

    expect(http.post).toHaveBeenNthCalledWith(1, '/api/v1/experience/files/uploads', expect.objectContaining({
      purpose: 'DISTRIBUTOR_PROOF',
      filename: 'registration.pdf',
      mimeType: 'application/pdf',
      checksumSha256: '00'.repeat(32)
    }), expect.objectContaining({ headers: { 'Idempotency-Key': '00000000-0000-4000-8000-000000000002' } }));
    expect(storageFetch).toHaveBeenCalledWith('https://storage.example/upload', expect.objectContaining({
      method: 'PUT',
      credentials: 'omit',
      body: file
    }));
    expect(http.post).toHaveBeenNthCalledWith(2, `/api/v1/experience/files/${assetId}/completion`, {
      checksumSha256: '00'.repeat(32)
    }, expect.objectContaining({ headers: { 'If-Match': '"0"', 'Idempotency-Key': '00000000-0000-4000-8000-000000000002' } }));
    expect(component.selectedDocumentId).toBe(assetId);
    expect(component.documentUploadState()).toBe('QUARANTINED');
    component.onDistributorSubmit(new Event('submit'));
    expect(submitEnquiry).not.toHaveBeenCalled();
    expect(submitApplication).not.toHaveBeenCalled();
    expect(component.submitError()).toContain('must pass the security scan');

    await component.checkDocumentStatus();
    expect(http.get).toHaveBeenCalledWith(`/api/v1/experience/files/${assetId}/download`);
    expect(component.documentUploadState()).toBe('CLEAN');
    expect(component.documentDownloadUrl()).toBe('https://storage.example/download');
  });

  it('does not contact the API for an unsupported proof file', async () => {
    const { component, http } = setup();
    const file = new File(['proof'], 'registration.txt', { type: 'text/plain' });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: [file] });
    await component.onDocumentSelected({ target: input } as unknown as Event);

    expect(http.post).not.toHaveBeenCalled();
    expect(component.documentUploadError()).toContain('Choose a PDF, JPEG, or PNG');
  });
});

it('submits a wholesale application with verified proof and waits for acknowledgment',()=>{
 const {component,submitApplication,submitEnquiry,requests}=setup();
 Object.assign(component.f,{company:'Wholesale Example',contact:'Example Person',email:'person@example.com',phone:'+2348012345678',state:'Lagos',address:'Synthetic business address'});
 component.selectedDocumentId='00000000-0000-4000-8000-000000000001';component.documentUploadState.set('CLEAN');
 component.onWholesaleSubmit(new Event('submit'));expect(submitEnquiry).not.toHaveBeenCalled();expect(submitApplication).toHaveBeenCalledWith(expect.objectContaining({partnerType:'WHOLESALE',organizationName:'Wholesale Example',documentIds:[component.selectedDocumentId],businessDetails:expect.stringContaining('Wholesale Example')}),expect.any(String));expect(component.wholesaleSubmitted()).toBe(false);
 requests[0].next({id:'app',partnerType:'WHOLESALE',state:'SUBMITTED'});requests[0].complete();expect(component.wholesaleSubmitted()).toBe(true);
});

it('retries an unconfirmed application with the same key and blocks changed details',()=>{
 const {component,submitApplication,requests}=setup();component.selectedDocumentId='00000000-0000-4000-8000-000000000001';component.documentUploadState.set('CLEAN');component.onDistributorSubmit(new Event('submit'));const key=submitApplication.mock.calls[0][1];requests[0].error({status:0});component.onDistributorSubmit(new Event('submit'));expect(submitApplication.mock.calls[1][1]).toBe(key);requests[1].error({status:0});component.dist.organizationName='Different business';component.onDistributorSubmit(new Event('submit'));expect(submitApplication).toHaveBeenCalledTimes(2);expect(component.submitError()).toContain('unconfirmed');
});

describe('Basic authenticated affiliate application',()=>{
 it('submits a typed application without bank fields or documents and waits for acknowledgement',()=>{const {component,submitApplication,submitEnquiry,requests}=setup();Object.assign(component.aff,{name:'Affiliate Example',email:'affiliate@example.invalid',phone:'08012345678',location:'Lagos',channels:'@affiliate',strategy:'Product reviews'});component.onAffiliateSubmit(new Event('submit'));expect(submitEnquiry).not.toHaveBeenCalled();expect(submitApplication).toHaveBeenCalledWith(expect.objectContaining({partnerType:'AFFILIATE',documentIds:[]}),expect.any(String));const payload=(submitApplication.mock.calls as any)[0][0];expect(JSON.parse(payload.businessDetails)).toMatchObject({phone:'+2348012345678',channels:'@affiliate'});expect(payload.businessDetails).not.toMatch(/bankName|accountNumber|accountName/);expect(component.affiliateSubmitted()).toBe(false);vi.spyOn(component,'loadMyApplications').mockImplementation(()=>{});requests[0].next({id:'application',state:'SUBMITTED'});requests[0].complete();expect(component.affiliateSubmitted()).toBe(true);expect(component.submittingAffiliate()).toBe(false);});
 it('requires login before accepting an affiliate application',()=>{const {component,session,submitApplication}=setup(false);component.onAffiliateSubmit(new Event('submit'));expect(session.signIn).toHaveBeenCalledWith('/affiliate');expect(submitApplication).not.toHaveBeenCalled();});
});

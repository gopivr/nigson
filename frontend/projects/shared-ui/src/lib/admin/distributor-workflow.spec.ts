import { describe, expect, it } from 'vitest';
import { distributorCommand, distributorDisplayState, distributorActionBlock, leadDisplayState, partnerLeadActionAllowed } from './distributor-workflow';
describe('Distributor workflow wire contract', () => {
  it('never displays approved while Commerce acknowledgement is pending', () => {
    expect(distributorDisplayState('ACTIVATION_PENDING')).toBe('Activation Pending');
    expect(distributorDisplayState('APPROVED')).toBe('Approved');
  });
  it('requires explicit document review confirmation', () => {
    expect(() => distributorCommand('Under Review','Reviewed document',false)).toThrow();
    expect(distributorCommand('Under Review','Reviewed document',true)).toEqual({ action: 'review', body: { reason: 'Reviewed document', documentsVerified: true } });
  });
  it('uses the real decision endpoint and explicit enum instead of approval/rejection URLs', () => {
    expect(distributorCommand('Approved','Second staff decision',false,'market-id')).toEqual({ action: 'decision', body: { reason: 'Second staff decision', decision: 'APPROVE', marketId: 'market-id' } });
    expect(distributorCommand('Rejected','Invalid document',false).body.decision).toBe('REJECT');
  });
  it('requires the configured market for approval', () => {
    expect(() => distributorCommand('Approved','Decision',true)).toThrow('market');
  });
  it('does not fabricate a server transition back to submitted', () => {
    expect(() => distributorCommand('Pending','Reset',true)).toThrow();
  });
});

describe('Distributor decision prerequisites',()=>{
 const reviewed={state:'UNDER_REVIEW',reviewerPartyId:'reviewer'};
 it('blocks approval before review and by the reviewer',()=>{
  expect(distributorActionBlock({state:'SUBMITTED'},'Approved','approver','market')).toContain('review');
  expect(distributorActionBlock(reviewed,'Approved','reviewer','market')).toContain('Another');
  expect(distributorActionBlock(reviewed,'Approved','approver','market')).toBeNull();
 });
 it('fails closed when reviewer, market or clean documents are missing',()=>{
  expect(distributorActionBlock({state:'UNDER_REVIEW'},'Approved','approver','market')).toContain('Reviewer');
  expect(distributorActionBlock(reviewed,'Approved','approver')).toContain('market');
  expect(distributorActionBlock({state:'SUBMITTED',documents:[{fileId:'file',scanStatus:'PENDING'}]},'Under Review','reviewer')).toContain('CLEAN');
 });
 it('preserves pending and suspended states and blocks terminal decisions',()=>{
  for(const [wire,label] of [['ACTIVATION_PENDING','Activation Pending'],['SUSPENSION_PENDING','Suspension Pending'],['SUSPENDED','Suspended']])expect(distributorDisplayState(wire)).toBe(label);
  expect(distributorDisplayState('unexpected')).toBe('Unknown');
  for(const state of ['APPROVED','REJECTED','SUSPENDED','ACTIVATION_PENDING'])expect(distributorActionBlock({state},'Rejected','sales','market')).not.toBeNull();
 });
 it('does not call lead resolution distributor approval',()=>{
  expect(leadDisplayState('RESOLVED')).toBe('Resolved');expect(leadDisplayState('CLOSED')).toBe('Closed');
  expect(partnerLeadActionAllowed('NEW','Start work')).toBe(true);expect(partnerLeadActionAllowed('CLOSED','Resolve')).toBe(false);
 });
 it('requires a human reason for a decision',()=>{expect(()=>distributorCommand('Rejected',' ',false)).toThrow('reason');});
});

it('uses saved-command retry only for an independent pending activation',()=>{expect(distributorActionBlock({state:'ACTIVATION_PENDING',reviewerPartyId:'reviewer'},'Retry activation','approver')).toBeNull();expect(distributorActionBlock({state:'ACTIVATION_PENDING',reviewerPartyId:'reviewer'},'Retry activation','reviewer')).toBeTruthy();expect(distributorActionBlock({state:'APPROVED'},'Retry activation','approver')).toBeTruthy();expect(distributorCommand('Retry activation','',false)).toEqual({action:'decision',body:{decision:'RETRY',reason:'Retry the saved activation command'}});});

describe('Simple affiliate decisions',()=>{
 it('allows direct affiliate approval without weakening business review',()=>{expect(distributorActionBlock({partnerType:'AFFILIATE',state:'SUBMITTED'},'Approved','staff','market')).toBeNull();expect(distributorActionBlock({partnerType:'WHOLESALE',state:'SUBMITTED'},'Approved','staff','market')).toContain('review');});
 it('suspends only an approved affiliate through the existing decision command',()=>{expect(distributorActionBlock({partnerType:'AFFILIATE',state:'APPROVED'},'Suspended','staff','market')).toBeNull();expect(distributorActionBlock({partnerType:'AFFILIATE',state:'SUBMITTED'},'Suspended','staff','market')).toContain('approved');expect(distributorCommand('Suspended','Terms review',false).body.decision).toBe('SUSPEND');});
});

it('permits recovery of a pending affiliate suspension without creating a fresh decision',()=>{expect(distributorActionBlock({partnerType:'AFFILIATE',state:'SUSPENSION_PENDING'},'Retry activation','staff','market')).toBeNull();expect(distributorCommand('Retry activation','',false).body.decision).toBe('RETRY');});

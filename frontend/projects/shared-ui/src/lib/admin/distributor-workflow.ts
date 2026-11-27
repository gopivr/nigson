export type DistributorStateLabel='Pending'|'Under Review'|'Activation Pending'|'Approved'|'Rejected'|'Suspension Pending'|'Suspended'|'Unknown';
export const DISTRIBUTOR_STATES:Record<string,DistributorStateLabel>={SUBMITTED:'Pending',UNDER_REVIEW:'Under Review',ACTIVATION_PENDING:'Activation Pending',APPROVED:'Approved',REJECTED:'Rejected',SUSPENSION_PENDING:'Suspension Pending',SUSPENDED:'Suspended'};
export function distributorDisplayState(state:string):DistributorStateLabel{return DISTRIBUTOR_STATES[state]??'Unknown';}
export function leadDisplayState(state:string):string{return ({NEW:'New',ASSIGNED:'Assigned',IN_PROGRESS:'In Progress',RESOLVED:'Resolved',CLOSED:'Closed'} as Record<string,string>)[state]??'Unknown';}
export type DistributorContext={partnerType?:string;state?:string;reviewerPartyId?:string|null;documents?:Array<{fileId?:string;scanStatus?:string}>};
export function distributorActionBlock(context:DistributorContext,action:string,actorId:string|null,marketId?:string):string|null {
 const state=context.state;
 if(context.partnerType==='AFFILIATE'){if(!actorId)return 'Sign in with Sales access.';if(action==='Approved')return ['SUBMITTED','UNDER_REVIEW'].includes(state??'')?(marketId?null:'A configured approval market is required.'):'Only a pending affiliate application can be approved.';if(action==='Suspended')return state==='APPROVED'?null:'Only an approved affiliate can be suspended.';}
 if(action==='Retry activation'){if(context.partnerType==='AFFILIATE'&&state==='SUSPENSION_PENDING')return actorId?null:'Sign in with Sales access.';if(!actorId||actorId===context.reviewerPartyId)return 'The original independent approver must retry activation.';return state==='ACTIVATION_PENDING'?null:'Only a pending activation can be retried.';}
 if(!actorId)return 'Sign in with an active Sales-authorized account.';
 if(action==='Under Review'){
  if(state!=='SUBMITTED')return 'Only a submitted application can enter document review.';
  if(!context.documents?.length||context.documents.some(document=>!document.fileId||document.scanStatus!=='CLEAN'))return 'All required documents must be present and marked CLEAN before completing review.';
  return null;
 }
 if(action==='Approved'){
  if(state!=='UNDER_REVIEW')return state==='ACTIVATION_PENDING'?'Approval is being processed. Refresh to check the confirmed outcome.':'Complete document review before approval.';
  if(!context.reviewerPartyId)return 'Reviewer information is missing. Refresh the application before deciding.';
  if(actorId===context.reviewerPartyId)return 'Another Sales-authorized staff member must approve this application; you completed its review.';
  if(!marketId)return 'A configured approval market is required.';
  return null;
 }
 if(action==='Rejected')return ['SUBMITTED','UNDER_REVIEW'].includes(state??'')?null:'Only a pending or under-review application can be rejected.';
 return 'This action is not available for this application.';
}
export function distributorCommand(status:string,reason:string,documentsVerified:boolean,marketId?:string){
 if(status==='Retry activation')return {action:'decision',body:{decision:'RETRY',reason:'Retry the saved activation command'}};
 if(!reason.trim()||reason.trim().length>500)throw new Error('Enter a staff reason between 1 and 500 characters.');
 if(status==='Under Review'){
  if(!documentsVerified)throw new Error('Confirm that you have inspected the registration documents.');
  return {action:'review',body:{reason:reason.trim(),documentsVerified:true}};
 }
 if(status==='Approved'){
  if(!marketId)throw new Error('A configured approval market is required.');
  return {action:'decision',body:{reason:reason.trim(),decision:'APPROVE',marketId}};
 }
 if(status==='Suspended')return {action:'decision',body:{reason:reason.trim(),decision:'SUSPEND'}};
 if(status==='Rejected')return {action:'decision',body:{reason:reason.trim(),decision:'REJECT'}};
 throw new Error('Choose a review or decision.');
}
export function partnerLeadState(action:string):string {
 const states:Record<string,string>={'Start work':'IN_PROGRESS',Resolve:'RESOLVED',Close:'CLOSED'};
 if(!states[action])throw new Error('Choose a supported lead action.');return states[action];
}
export function partnerLeadActionAllowed(state:string,action:string):boolean {
 if(!['Start work','Resolve','Close'].includes(action))return false;
 const target=partnerLeadState(action);
 if(['NEW','ASSIGNED'].includes(state))return ['IN_PROGRESS','RESOLVED','CLOSED'].includes(target);
 if(state==='IN_PROGRESS')return ['RESOLVED','CLOSED'].includes(target);
 return state==='RESOLVED'&&target==='CLOSED';
}
export function applicationFailure(error:any):string {
 const code=error?.error?.code;
 let message=error?.error?.detail||error?.message||'The application update failed.';
 if(code==='VERSION_MISMATCH')message='This application changed. Refresh its latest record before trying again.';
 else if(code==='BUSINESS_RULE_VIOLATION')message='The backend rejected this decision. Refresh the application and check its state, documents and reviewer. Approval requires a different Sales-authorized person after review.';
 else if(code==='ACCESS_DENIED')message='This account or session is not authorized for this action. Sales access is required.';
 else if(error?.status===0||code==='PROVIDER_OUTCOME_UNKNOWN')message='The outcome could not be confirmed. Refresh the server status before attempting another action.';
 const reference=error?.error?.correlationId;
 return message+(typeof reference==='string'?' Reference: '+reference:'');
}

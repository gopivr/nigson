"""Closed ADR-014 owner cancellation protocol. No provider credentials or actor assertions."""
def schemas():
    uuid={'type':'string','format':'uuid'}
    mode={'type':'string','enum':['MOCK','SANDBOX','LIVE']}
    state={'type':'string','enum':['UNKNOWN','CANCELLED','REJECTED']}
    reason={'type':'string','enum':['NONE','PROVIDER_OUTCOME_UNKNOWN','PROVIDER_UNAVAILABLE','PROVIDER_MODE_MISMATCH','CARRIER_CANCEL_REJECTED','CARRIER_ALREADY_DELIVERED','CARRIER_APPROVAL_REQUIRED']}
    def obj(properties):
        return {'type':'object','additionalProperties':False,'required':list(properties),'properties':properties}
    command={'cancellationId':uuid,'shipmentId':uuid,'bookingRequestId':uuid,'reference':{'type':'string','pattern':'^[A-Za-z0-9]{10,40}$'},'mode':mode,'requestedAt':{'type':'string','format':'date-time'}}
    receipt={key:value for key,value in command.items() if key!='requestedAt'}
    receipt={'commandId':uuid,**receipt,'state':state,'occurredAt':{'type':'string','format':'date-time'},'reasonCode':reason}
    version={'type':'integer','format':'int64','minimum':0}
    acknowledgement=obj({'cancellationId':uuid,'shipmentId':uuid,'state':{'type':'string','enum':['QUEUED','UNKNOWN','CANCELLED','REJECTED']},'receipt':{'anyOf':[{'$ref':'#/components/schemas/CarrierCancellationReceipt'},{'type':'null'}]}})
    acknowledgement['required'].remove('receipt')
    return {'CarrierCancellationCommand':obj(command),'CarrierCancellationReceipt':obj(receipt),'CarrierCancellationAcknowledgement':acknowledgement,'CarrierCancellationDTO':obj({'id':uuid,'shipmentId':uuid,'state':{'type':'string','enum':['REQUESTED','QUEUED','UNKNOWN','CANCELLED','REJECTED']},'version':version,'shipmentVersion':version,'errorCode':{'anyOf':[reason,{'type':'null'}]}})}

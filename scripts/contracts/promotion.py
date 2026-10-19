"""COM-PROMO-001 closed commerce promotion API contract."""
from authorization import operation, parameter, object_schema, REF, UUID, VERSION

def extend(spec):
    paths=spec['paths']; schemas=spec['components']['schemas']
    strv=lambda min=1,max=120:{'type':'string','minLength':min,'maxLength':max}
    money={'type':'string','pattern':r'^(0|[1-9][0-9]{0,14})(\\.[0-9]{1,2})?$'}
    instant={'type':'string','format':'date-time'}
    def closed(props, required=None):
        value=object_schema(props)
        if required is not None:value['required']=required
        return value
    def params(props):return closed({'schemaVersion':{'type':'integer','const':1},**props})
    schemas.update({
      'PromotionKind':{'type':'string','enum':['PERCENT','FIXED','FREE_DELIVERY','SPEND_REWARD','REFERRAL','DEALER']},
      'PromotionEligibility':closed({'schemaVersion':{'type':'integer','const':1},'segment':{'type':'string','enum':['RETAIL','DISTRIBUTOR','ANY']},'productIds':{'type':'array','maxItems':100,'uniqueItems':True,'items':UUID},'categoryIds':{'type':'array','maxItems':100,'uniqueItems':True,'items':UUID},'minimumSpend':{'anyOf':[money,{'type':'null'}]},'minimumQuantity':{'anyOf':[strv(1,24),{'type':'null'}]},'firstSettledPurchaseOnly':{'type':'boolean'}}),
      'PromotionParameters':{'oneOf':[params({'percentage':{'type':'string','pattern':r'^(0(\\.[0-9]{1,8})?|1(\\.0{1,8})?)'},'maximumDiscount':{'anyOf':[money,{'type':'null'}]} }),params({'amount':money}),params({'maximumDeliveryDiscount':{'anyOf':[money,{'type':'null'}]} }),params({'threshold':money,'rewardAmount':money,'maximumRewardPerOrder':money,'holdDays':{'type':'integer','minimum':0,'maximum':365},'expiresDays':{'type':'integer','minimum':1,'maximum':3650},'refundMode':{'type':'string','enum':['PROPORTIONAL','FULL_ON_ANY_REFUND']}}),params({'minimumSettledSpend':money,'referrerReward':money,'referredReward':money,'holdDays':{'type':'integer','minimum':0,'maximum':365},'expiresDays':{'type':'integer','minimum':1,'maximum':3650},'maximumRewardsPerReferrer':{'type':'integer','minimum':1,'maximum':1000000},'refundMode':{'type':'string','enum':['PROPORTIONAL','FULL_ON_ANY_REFUND']}}),params({'minimumSettledSpend':money,'maximumIssue':money,'expiresDays':{'type':'integer','minimum':1,'maximum':3650},'requiresSecondActor':{'type':'boolean'}})]},
      'PromotionRuleInput':closed({'code':strv(1,80),'kind':REF('PromotionKind'),'marketId':UUID,'startsAt':instant,'endsAt':instant,'parameters':REF('PromotionParameters'),'eligibility':REF('PromotionEligibility'),'stackingPolicy':{'type':'string','enum':['EXCLUSIVE','STACKABLE']},'enabled':{'type':'boolean','const':False}}),
      'PromotionRuleRevisionInput':closed({'kind':REF('PromotionKind'),'marketId':UUID,'startsAt':instant,'endsAt':instant,'parameters':REF('PromotionParameters'),'eligibility':REF('PromotionEligibility'),'stackingPolicy':{'type':'string','enum':['EXCLUSIVE','STACKABLE']},'enabled':{'type':'boolean','const':False}}),
      'PromotionRuleDTO':closed({'id':UUID,'code':strv(1,80),'kind':REF('PromotionKind'),'marketId':UUID,'startsAt':instant,'endsAt':instant,'parameters':REF('PromotionParameters'),'eligibility':REF('PromotionEligibility'),'stackingPolicy':{'type':'string','enum':['EXCLUSIVE','STACKABLE']},'enabled':{'type':'boolean'},'revision':{'type':'integer','format':'int64','minimum':1},'version':VERSION}),
      'PromotionActivation':closed({'enabled':{'type':'boolean'},'approvalReference':strv(1,120),'reason':strv(10,300)}),
      'CouponCreate':closed({'ruleId':UUID,'code':strv(1,80),'partyId':{'anyOf':[UUID,{'type':'null'}]},'maximumUses':{'type':'integer','minimum':1,'maximum':1000000},'maximumUsesPerParty':{'type':'integer','minimum':1,'maximum':1000000},'expiresAt':instant},['ruleId','code','maximumUses','maximumUsesPerParty','expiresAt']),
      'CouponDTO':closed({'id':UUID,'code':strv(1,80),'active':{'type':'boolean'},'remainingUses':{'type':'integer','minimum':0},'version':VERSION}),
      'CouponToggle':closed({'active':{'type':'boolean'},'reason':strv(10,300)}),
      'WheelBenefit':{'oneOf':[params({'ruleId':UUID,'expiresHours':{'type':'integer','minimum':1,'maximum':8760},'maximumDiscount':money}),params({'extraDraws':{'type':'integer','const':1}}),params({})]},
      'WheelOutcomeInput':closed({'label':strv(1,100),'kind':{'type':'string','enum':['COUPON','FREE_DELIVERY','RETRY','NONE']},'weight':{'type':'integer','minimum':1,'maximum':1000000},'benefit':REF('WheelBenefit')}),
      'WheelCampaignCreate':closed({'code':strv(1,80),'ruleId':UUID,'marketId':UUID,'dailyLimit':{'type':'integer','minimum':1,'maximum':1000000},'rewardBudget':money,'outcomes':{'type':'array','minItems':1,'maxItems':100,'items':REF('WheelOutcomeInput')},'enabled':{'type':'boolean','const':False}}),
      'WheelCampaignDTO':closed({'id':UUID,'code':strv(1,80),'ruleId':UUID,'marketId':UUID,'dailyLimit':{'type':'integer','minimum':1},'rewardBudget':money,'outcomes':{'type':'array','items':REF('WheelOutcomeInput')},'enabled':{'type':'boolean'},'version':VERSION}),
      'WheelCampaignToggle':closed({'enabled':{'type':'boolean'},'reason':strv(10,300)}),
      'WheelDrawInput':closed({'marketId':UUID}),
      'WheelDrawDTO':closed({'drawId':UUID,'outcome':closed({'label':strv(1,100),'kind':{'type':'string','enum':['COUPON','FREE_DELIVERY','RETRY','NONE']}}),'couponCode':{'anyOf':[strv(1,80),{'type':'null'}]},'expiresAt':{'anyOf':[instant,{'type':'null'}]}}),
      'PromotionPage':closed({'items':{'type':'array','items':REF('PromotionRuleDTO')},'page':{'type':'integer','minimum':0},'size':{'type':'integer','minimum':1,'maximum':100},'totalElements':{'type':'integer','minimum':0},'totalPages':{'type':'integer','minimum':0},'sort':strv(1,40)})
    })
    def command(api,method,path,body,response,role,create=False,versioned=False):
        ps=[]
        if '{id}' in path:ps.append(parameter('id','path',UUID,True))
        if versioned:ps.append(parameter('If-Match','header',{'type':'string','pattern':r'^"(0|[1-9][0-9]{0,18})"$'},True))
        if create or versioned:ps.extend([parameter('Idempotency-Key','header',strv(1,100),True),parameter('X-CSRF-TOKEN','header',{'type':'string','minLength':43,'maxLength':43},True)])
        op={'operationId':api,'description':f'{role} only. Commerce-owned transaction, server-derived authority, audited mutation, stable idempotency and no provider HTTP. '+('Published rule revisions are immutable. New revisions are disabled. ' if api=='API-152' else '')+('Activation requires a matching approved active commerce.feature_configuration record binding promotionRuleId and promotionRevision. Emits EVT-007. ' if api=='API-153' else '')+('Campaign-row budget lock, UTC per-party daily slot and cryptographic weighted draw are committed atomically. ' if api=='API-061' else ''),'security':[{'bffSession':[]}],'parameters':ps,'responses':{'200':{'description':'Success','content':{'application/json':{'schema':REF(response)}}},'201':{'description':'Created','content':{'application/json':{'schema':REF(response)}}},'400':{'description':'Invalid input','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}},'401':{'description':'Authentication required','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}},'403':{'description':'Access denied','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}},'404':{'description':'Not found','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}},'409':{'description':'Conflict or exhausted budget','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}},'412':{'description':'Version mismatch','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}},'422':{'description':'Commercial rule violation','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}}}}
        if body:op['requestBody']={'required':True,'content':{'application/json':{'schema':REF(body)}}}
        if method in ('get','patch') or method=='post' and api=='API-061': pass
        paths.setdefault(path,{})[method]=op
    query=[parameter('page','query',{'type':'integer','minimum':0}),parameter('size','query',{'type':'integer','minimum':1,'maximum':100}),parameter('sort','query',{'type':'string','enum':['createdAt,asc','createdAt,desc','code,asc','code,desc']})]
    for path,api,dto in [('/api/v1/staff/promotion-rules','API-056','PromotionPage'),('/api/v1/staff/coupons','API-058','CouponDTO'),('/api/v1/staff/wheel-campaigns','API-060','WheelCampaignDTO')]:
        paths.setdefault(path,{})['get']={'operationId':api,'description':'SALES only. Staff configuration list with bounded page, size and stable sort.','security':[{'bffSession':[]}],'parameters':query,'responses':{'200':{'description':'Page','content':{'application/json':{'schema':REF(dto)}}}}}
    command('API-055','post','/api/v1/staff/promotion-rules','PromotionRuleInput','PromotionRuleDTO','SALES',True)
    command('API-152','post','/api/v1/staff/promotion-rules/{id}/revisions','PromotionRuleRevisionInput','PromotionRuleDTO','SALES',True,True)
    command('API-153','post','/api/v1/staff/promotion-rules/{id}/activation','PromotionActivation','PromotionRuleDTO','FINANCE',True,True)
    command('API-057','post','/api/v1/staff/coupons','CouponCreate','CouponDTO','SALES',True)
    command('API-154','patch','/api/v1/staff/coupons/{id}','CouponToggle','CouponDTO','SALES',True,True)
    command('API-059','post','/api/v1/staff/wheel-campaigns','WheelCampaignCreate','WheelCampaignDTO','SALES',True)
    command('API-155','patch','/api/v1/staff/wheel-campaigns/{id}','WheelCampaignToggle','WheelCampaignDTO','SALES',True,True)
    command('API-061','post','/api/v1/wheel-campaigns/{id}/draws','WheelDrawInput','WheelDrawDTO','CUSTOMER',True)
    return spec

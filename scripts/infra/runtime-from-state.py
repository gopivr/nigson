"""Derive non-secret runtime settings from applied infrastructure; seed only missing internal secrets."""
import base64
import hashlib
import json
import re
import secrets
import urllib.request

REVISION='runtime-v1'
CLIENT_NAMES={'commerce':['NIGSON_BFF_CLIENT_SECRET','NIGSON_COMMERCE_WORKLOAD_SECRET','NIGSON_SESSION_WRAPPED_KEY'],
              'experience':['NIGSON_EXPERIENCE_WORKLOAD_SECRET'],'integration':['NIGSON_INTEGRATION_WORKLOAD_SECRET']}


def derive(applied, infrastructure, release, aws):
    account,region=release['account_id'],release['region']
    if (applied['expected_account_id']!=account or applied['region']!=region or applied['environment']!='stage'
            or applied['zone_name']!='stage.nigson.com' or infrastructure['account_id']!=account or infrastructure['region']!=region):
        raise ValueError('Applied runtime infrastructure does not match the selected staging account/region')
    for field in ['runtime_actor_key_arn','runtime_session_key_arn','private_subnet_cidrs']:
        if not infrastructure.get(field):raise ValueError('Run updated bootstrap to record state-owned runtime security: '+field)
    actor=infrastructure['runtime_actor_key_arn'];session=infrastructure['runtime_session_key_arn']
    pattern=rf'arn:aws:kms:{region}:{account}:key/[a-f0-9-]{{36}}'
    if not re.fullmatch(pattern,actor) or not re.fullmatch(pattern,session) or actor==session:
        raise ValueError('Separate state-owned actor signing and session encryption keys are required')
    response=aws.call('kms','get-public-key',key_id=actor)
    if response.get('KeyId')!=actor or response.get('KeySpec')!='ECC_NIST_P256' or response.get('KeyUsage')!='SIGN_VERIFY':
        raise ValueError('Unexpected actor signing key metadata')
    public=response['PublicKey'];base64.b64decode(public,validate=True)
    kid=actor.rsplit('/',1)[1]
    owners=['commerce','experience','integration']+(['ai'] if release['product_phase']==5 else [])
    values=dict(applied)
    environments={}
    for owner in owners:
        defaults={'NIGSON_AWS_REGION':region,'NIGSON_OIDC_ISSUER':infrastructure['identity_issuer'],
                  'NIGSON_OIDC_JWKS':infrastructure['identity_issuer']+'/protocol/openid-connect/certs',
                  'NIGSON_PRIVATE_PEER_CIDRS':','.join(infrastructure['private_subnet_cidrs']),
                  'NIGSON_ACTOR_PUBLIC_KEYS':json.dumps({kid:public},separators=(',',':')),
                  'NIGSON_PROVIDERS_ENABLED':'false','NIGSON_PANDAGO_ENABLED':'false','NIGSON_TERMII_ENABLED':'false',
                  'NIGSON_FILES_ENABLED':'false','LAUNCH_COUNTRY':'NG','LAUNCH_MARKET':'NG','LAUNCH_CURRENCY':'NGN'}
        if owner=='commerce':defaults.update(NIGSON_ACTOR_KMS_KEY=actor,NIGSON_ACTOR_KEY_ID=kid,NIGSON_ACTOR_PUBLIC_KEY=public,
            NIGSON_SESSION_KMS_KEY=session,NIGSON_SESSION_KEY_REVISION=REVISION,
            NIGSON_BFF_CALLBACK=infrastructure['frontend_url']+'/bff/callback',NIGSON_BFF_ALLOWED_ORIGINS=infrastructure['frontend_url'],
            NIGSON_BFF_RETURN_PATHS='/,/account,/shop,/checkout,/admin,/distributor',NIGSON_REGISTRATION_ENABLED='false')
        current=applied['application_environment'].get(owner,{})
        # Preserve explicitly configured feature policies, but bind infrastructure/security identity to state.
        environments[owner]={**defaults,**current,'APP_ENV':'stage','NIGSON_SECURITY_ENABLED':'true',
            'NIGSON_DB_USER':'nigson_'+owner+'_app',
            'NIGSON_JDBC_URL':f'jdbc:postgresql://{infrastructure["database_address"]}:5432/nigson?sslmode=verify-full&sslrootcert=/tmp/nigson-rds-ca.pem&currentSchema={owner}'}
    values.update(key_administrator_principal_arn=infrastructure["key_administrator_principal_arn"],application_environment=environments,
        application_secret_names={owner:sorted(set(applied['application_secret_names'].get(owner,[]))|set(CLIENT_NAMES.get(owner,[]))) for owner in owners},
        publish_task_definitions=True,activate_identity=applied['activate_identity'],activate_applications=False,
        cpu_architecture={'arm64':'ARM64','amd64':'X86_64'}[release['architecture']],
        image_digests={name:item['image'].split('@')[1] for name,item in release['components'].items() if name!='migration'},
        migration_image_digest=release['components']['migration']['image'].split('@')[1])
    return values


def seed_internal(infrastructure, release, aws):
    account,region=release['account_id'],release['region']
    for owner,entries in infrastructure['secret_arns'].items():
        allowed={'migration_password','SPRING_DATASOURCE_PASSWORD',*CLIENT_NAMES.get(owner,[])}
        if owner=='identity':allowed={'database_password','database_ca','bootstrap_username','bootstrap_password'}
        for name,arn in entries.items():
            if name not in allowed:continue
            if not arn.startswith(f'arn:aws:secretsmanager:{region}:{account}:secret:nigson/stage/{owner}/'):
                raise ValueError('Unexpected internal secret ARN')
            versions=aws.call('secretsmanager','describe-secret',secret_id=arn).get('VersionIdsToStages',{})
            if any('AWSCURRENT' in stages for stages in versions.values()):continue
            if versions:raise ValueError('Internal secret has versions but no AWSCURRENT; reconcile before provisioning')
            if name=='NIGSON_SESSION_WRAPPED_KEY':
                value=aws.call('kms','generate-data-key-without-plaintext',key_id=infrastructure['runtime_session_key_arn'],key_spec='AES_256',
                    encryption_context={'purpose':'nigson-commerce-session','revision':REVISION})['CiphertextBlob']
            elif owner=='identity' and name=='database_ca':
                url=f'https://truststore.pki.rds.amazonaws.com/{region}/{region}-bundle.pem'
                with urllib.request.urlopen(url,timeout=20) as response:value=response.read(65537).decode('ascii')
                if len(value)>65536 or '-----BEGIN CERTIFICATE-----' not in value:raise ValueError('Invalid regional RDS CA bundle')
            elif name=='bootstrap_username':value='bootstrap-'+secrets.token_hex(8)
            else:value=secrets.token_urlsafe(48)
            token=hashlib.sha256((arn+':initial-runtime-v1').encode()).hexdigest()
            # A stable first-version token makes concurrent initializers fail instead of rotating credentials.
            aws.call('secretsmanager','put-secret-value',secret_id=arn,client_request_token=token,secret_string=value)

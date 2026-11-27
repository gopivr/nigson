# Documentation

## Everyday use

- [Local development](LOCAL_APPLICATION_GUIDE.md): setup, commands and troubleshooting.
- [AWS deployment](DEPLOYMENT.md): GitHub configuration, infrastructure and UI releases.

## Technical reference

- [Architecture](ARCHITECTURE.md) and [security](SECURITY.md).
- [API contracts](API_CONTRACT.md), [data model](DATA_MODEL.md) and [events](EVENT_CATALOG.md).
- [Dependency versions](DEPENDENCY_LOCK.md).
- [Operational runbooks](runbooks/): payments, orders, delivery, identity and recovery.
- [Architecture decisions](adr/): reasons behind important implementation choices.
- [Backend specification](../BACKEND_IMPLEMENTATION_MASTER.md): detailed input used
  by database/event generators and contract checks. Current source and later ADRs
  supersede historical examples and roadmap descriptions.

Build logs and test evidence are generated locally under `.build/` and are ignored.
There is no committed task registry or per-session reporting requirement.

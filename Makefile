.PHONY: status-check build test verify java-build dependency-check ai-sync ai-build ai-test ai-lint local-up local-down local-config-check local-status
UV ?= uv
PROFILE ?= core
ENV ?= local
export PROFILE ENV
export MAVEN_USER_HOME := $(CURDIR)/.build/maven
export UV_CACHE_DIR := $(CURDIR)/.build/uv-cache
export UV_PYTHON_INSTALL_DIR := $(CURDIR)/.build/python
export UV_PROJECT_ENVIRONMENT := $(CURDIR)/.build/ai-env
export SOURCE_DATE_EPOCH := 1791072000

status-check:
	python3 scripts/check_repository.py

java-build:
	./mvnw -B verify

dependency-check:
	./mvnw -B dependency:tree -DoutputType=json -DoutputFile=target/dependencies.json
	python3 scripts/java_dependency_lock.py --check

build test: java-build

verify: status-check java-build dependency-check command-surface-check

ifeq ($(PROFILE),ai)
build: ai-build
test: ai-test
verify: ai-build ai-test ai-lint
endif

ai-sync:
	$(UV) --directory services/ai sync --locked --python 3.12.12

ai-build: ai-sync
	$(UV) --directory services/ai build --no-build-isolation --python $(UV_PROJECT_ENVIRONMENT)/bin/python --out-dir $(CURDIR)/.build/ai-dist

ai-test: ai-sync
	$(UV_PROJECT_ENVIRONMENT)/bin/python -m pytest services/ai/tests -q

ai-lint: ai-sync
	$(UV_PROJECT_ENVIRONMENT)/bin/ruff check services/ai/src services/ai/tests
	$(UV_PROJECT_ENVIRONMENT)/bin/mypy --config-file services/ai/pyproject.toml services/ai/src

local-up:
	python3 scripts/local/dependencies.py up

local-down:
	python3 scripts/local/dependencies.py down

local-config-check:
	python3 scripts/local/dependencies.py validate

local-status:
	python3 scripts/local/dependencies.py status

.PHONY: migrate seed run-core run-ai cold-start command-surface-check
migrate:
	python3 scripts/local/dependencies.py migrate
seed:
	python3 scripts/local/dependencies.py seed
run-core:
	python3 scripts/local/dependencies.py run-core
run-ai:
	PROFILE=ai python3 scripts/local/dependencies.py run-ai
cold-start:
	python3 scripts/local/cold_start.py
command-surface-check:
	python3 -m unittest discover -s tests/contract -v

.PHONY: schema-check database-check
schema-check:
	python3 scripts/database/generate.py --check

database-check: schema-check
	terraform -chdir=infrastructure/terraform/database init -backend=false
	terraform -chdir=infrastructure/terraform/database validate
	terraform -chdir=infrastructure/terraform/database apply -auto-approve
	docker build --file services/commerce/Dockerfile --target migration --tag nigson/migration:local .
	ENV=test python3 scripts/database/verify.py

.PHONY: database-rollback-check
database-rollback-check:
	ENV=test python3 scripts/database/rollback_check.py

.PHONY: platform-check mock-build mock-reset provider-scenario provider-journal provider-contract-test
platform-check: ai-sync
	ENV=test python3 scripts/platform/verify.py
mock-build:
	python3 scripts/platform/build_mocks.py
mock-reset:
	python3 scripts/local/mock_admin.py reset
provider-scenario:
	python3 scripts/local/mock_admin.py configure
provider-journal:
	python3 scripts/local/mock_admin.py state
provider-contract-test:
	ENV=test python3 scripts/platform/provider_check.py

.PHONY: provider-live-check
provider-live-check:
	python3 scripts/platform/provider_live_check.py

.PHONY: observability-check observability-infra-check
observability-check: ai-sync
	ENV=test python3 scripts/platform/observability_check.py
observability-infra-check:
	terraform -chdir=infrastructure/terraform/modules/observability init -backend=false
	terraform -chdir=infrastructure/terraform/modules/observability validate
	terraform -chdir=infrastructure/terraform/modules/observability test

.PHONY: collector-config-check
collector-config-check:
	ENV=test python3 scripts/platform/collector_check.py

.PHONY: identity-sync identity-check identity-policy-check identity-infra-check
identity-sync:
	UV_PROJECT_ENVIRONMENT=$(CURDIR)/.build/identity-env $(UV) --directory scripts/identity sync --locked --python 3.12.12
identity-policy-check: identity-sync
	$(CURDIR)/.build/identity-env/bin/python -m unittest discover -s tests/security -v
identity-check: identity-policy-check
	ENV=test $(CURDIR)/.build/identity-env/bin/python scripts/identity/qualify.py
identity-infra-check:
	terraform -chdir=infrastructure/terraform/modules/identity init -backend=false
	terraform -chdir=infrastructure/terraform/modules/identity validate
	terraform -chdir=infrastructure/terraform/modules/identity test

.PHONY: security-check security-infra-check
security-check:
	ENV=test python3 scripts/security/verify.py
security-infra-check:
	terraform -chdir=infrastructure/terraform/modules/private-trust init -backend=false
	terraform -chdir=infrastructure/terraform/modules/private-trust validate
	terraform -chdir=infrastructure/terraform/modules/private-trust test

.PHONY: authorization-check
authorization-check:
	python3 scripts/contracts/authorization.py --check
	ENV=test python3 scripts/authorization/verify.py

.PHONY: party-check
party-check:
	ENV=test python3 scripts/party/verify.py

.PHONY: session-check
session-check:
	ENV=test python3 scripts/session/verify.py

.PHONY: configuration-check catalogue-check
configuration-check:
	ENV=test python3 scripts/configuration/verify.py
catalogue-check:
	python3 scripts/contracts/authorization.py --check
	ENV=test python3 scripts/catalogue/verify.py

.PHONY: files-check
files-check:
	python3 scripts/contracts/authorization.py --check
	ENV=test python3 scripts/files/verify.py

.PHONY: integration-jobs-check
integration-jobs-check:
	python3 scripts/contracts/authorization.py --check
	ENV=test python3 scripts/provider/verify.py

.PHONY: notification-check
notification-check:
	python3 scripts/contracts/authorization.py --check
	ENV=test python3 scripts/notification/verify.py

.PHONY: inventory-check
inventory-check:
	python3 scripts/contracts/authorization.py --check
	ENV=test python3 scripts/inventory/verify.py

.PHONY: market-check
market-check:
	$(MAKE) status-check
	ENV=test python3 scripts/market/verify.py

.PHONY: pricing-check
pricing-check:
	python3 scripts/contracts/authorization.py --check
	ENV=test python3 scripts/pricing/verify.py

.PHONY: nigeria-check
nigeria-check:
	$(MAKE) status-check
	ENV=test python3 scripts/nigeria/verify.py

.PHONY: cart-check
cart-check:
	python3 scripts/contracts/authorization.py --check
	ENV=test python3 scripts/cart/verify.py

.PHONY: fulfillment-check
fulfillment-check:
	python3 scripts/contracts/authorization.py --check
	ENV=test python3 scripts/fulfillment/verify.py

.PHONY: order-check
order-check:
	python3 scripts/contracts/authorization.py --check
	ENV=test python3 scripts/order/verify.py

.PHONY: payment-check
payment-check:
	python3 scripts/contracts/authorization.py --check
	ENV=test python3 scripts/payment/verify.py

.PHONY: paystack-check
paystack-check:
	python3 scripts/contracts/authorization.py --check
	python3 -m unittest discover -s tests/providers/paystack
	ENV=test python3 scripts/platform/provider_check.py
	ENV=test python3 scripts/paystack/verify.py

.PHONY: status-check build test verify java-build dependency-check ai-sync ai-build ai-test ai-lint local-up local-down local-config-check local-status
UV ?= uv
PROFILE ?= core
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

verify: status-check java-build dependency-check

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
	$(UV_PROJECT_ENVIRONMENT)/bin/mypy services/ai/src

local-up:
	python3 scripts/local/dependencies.py up

local-down:
	python3 scripts/local/dependencies.py down

local-config-check:
	python3 scripts/local/dependencies.py validate

local-status:
	python3 scripts/local/dependencies.py status

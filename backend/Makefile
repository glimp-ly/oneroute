SHELL := /bin/bash
GO_BIN := $(shell command -v go 2>/dev/null)
GO_CACHE_DIR ?= $(HOME)/.cache/oneroute-go

ifeq ($(GO_BIN),)
define run_go
	mkdir -p "$(GO_CACHE_DIR)/mod" "$(GO_CACHE_DIR)/build" && \
	docker run --rm --network host \
		-u "$$(id -u):$$(id -g)" \
		-e HOME=/tmp -e GOMODCACHE=/cache/mod -e GOCACHE=/cache/build \
		-v "$(CURDIR):/app" \
		-v "$(GO_CACHE_DIR)/mod:/cache/mod" \
		-v "$(GO_CACHE_DIR)/build:/cache/build" \
		-w /app golang:1.23 go $(1)
endef
else
define run_go
	$(GO_BIN) $(1)
endef
endif

.PHONY: build test test-integration tidy docker-up docker-down

build:
	$(call run_go,build ./...)

test:
	$(call run_go,test -v -race ./...)

test-integration:
	$(call run_go,test -tags=integration -v -race ./...)

tidy:
	$(call run_go,mod tidy)

docker-up:
	docker compose up -d

docker-down:
	docker compose down

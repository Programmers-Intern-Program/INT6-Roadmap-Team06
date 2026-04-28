# Repository Agent Guide

## Backend Package Structure

- Backend code lives under `backend/src/main/java/com/back/coach`.
- Do not create a generic `common` package by default.
- If code looks common, place it by meaning first:
  - authorization or ownership checks: `global/security`
  - exception and error handling: `global/exception`
  - API response wrappers: `global/response`
  - infrastructure configuration: `global/config`
  - outbound clients or external API adapters: `external/<provider>`
- Domain features should use a vertical package under `domain/<domain>`:
  - `controller` for HTTP entrypoints
  - `dto` for request, response, command result, and snapshot records
  - `service` for application/domain services
  - `entity` for JPA entities
  - `repository` for Spring Data repositories

## Current Domain Defaults

- Roadmap feature code belongs under `domain/roadmap`.
- Dashboard snapshot code belongs under `domain/dashboard`.
- Shared result-history behavior, such as version calculation, belongs under `domain/result`.
- GitHub result storage belongs under `domain/github`; GitHub API clients belong under `external/github`.

## Change Rules

- Keep public API paths stable when moving packages.
- Move tests to mirror the production package they verify.
- Avoid placeholder top-level packages such as `api` or `service`; add code under the owning domain instead.

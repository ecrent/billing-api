# Security Policy

## Scope

billing-api is a public portfolio/demo project. It runs as a **public demo API** at
https://billing-api.eanil.dev.

**The API has no authentication — by design.** This is not a misconfiguration;
the README's "What's Intentionally Excluded" section covers it. Please do not
report the lack of auth as a vulnerability.

- **In scope:** security issues in this repository's source code, build, or CI/CD
  workflows.
- **Out of scope:** hosting/infrastructure issues (nginx config, VPS hardening,
  TLS, DNS), automated scanner noise, and anything requiring privileged access
  to the demo host.

## Data

No real user data is stored. Any data in the demo database is synthetic test data.

## Reporting

Please report vulnerabilities privately via
[GitHub vulnerability reporting](https://github.com/ecrent/billing-api/security/advisories/new)
rather than opening a public issue. I'll acknowledge reports as soon as I see
them — this is a personal project, so response times may vary.

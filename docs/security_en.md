# Security & Safety

This document centralizes the LinkMind Security & Safety strategy. It replaces scattered high-level security notes in README-style overview documents and describes the controls available in the middleware layer, the configuration responsibilities of deployers, and the compliance boundary for production use.

> This document is not a compliance certification and is not legal advice. Additional security reviews, privacy assessments, and compliance audits may be required for specific organizations, industries, and jurisdictions.

## Goals

- Govern model access, RAG, agent runtimes, MCP, Skills, filters, and cascade networking through one middleware layer.
- Provide configurable control points for input handling, retrieval, model calls, tool calls, output handling, and runtime administration.
- Reduce risks from sensitive content, unauthorized access, data leakage, uncontrolled tool execution, and provider configuration drift.
- Keep configuration auditable through `lagi.yml`, including routes, filters, authentication, policy checks, and backend capability switches.

## Responsibility Boundary

LinkMind provides middleware-level security controls and governance entry points, but production security still depends on the deployer's environment configuration, permission model, secret management, network policy, data classification process, and model provider choices.

Deployers should verify:

- Whether `functions.chat.enable_auth`, `functions.chat.enable_policy`, and `filters.enable` are enabled.
- Whether `functions.chat.filter` references the required filters, such as `sensitive`, `priority`, `stopping`, and `continue`.
- Whether models, vector stores, MCP services, Skills, Workers, and external agent runtimes are exposed only to trusted sources.
- Whether logs, caches, uploaded files, RAG indexes, and temporary workspaces follow the organization's data retention and access policies.

## Safety Guardrails

LinkMind guardrails are composed through configuration rather than being tied to a single model or endpoint. Core controls include:

- Sensitive-word filtering: configure `mask`, `erase`, `block`, and related action levels through `filters.items[].groups`.
- Priority keywords: use `priority` rules to influence conversation scheduling or business priority.
- Stopping keywords: use `stopping` rules to terminate conversation paths that should not continue.
- Continuation keywords: use `continue` rules to recognize sessions that should preserve context.
- Built-in policy checks: enable runtime policy checks with `functions.chat.enable_policy`.

Filter fields, examples, and references are documented in the [Configuration Guide](config_en.md#filters).

## Data And Privacy

LinkMind can connect business systems, private knowledge bases, vector databases, model providers, and agent runtimes. Deployers should govern data by sensitivity level:

- Do not place real API keys, secrets, personal data, or customer data in public demos, test configurations, or commits.
- Apply the same access controls to RAG documents, vector indexes, caches, logs, and uploaded files as to their source data.
- Before connecting third-party models or cloud agent platforms, review data transfer, training use, log retention, and cross-border processing terms.
- Apply the same classification and redaction requirements to OCR, ASR/TTS, image, video, Text-to-SQL, and document-processing workflows.
- Establish audit and deletion processes for inputs or outputs containing personal data, credentials, trade secrets, or regulated data.

## Access Control And Secrets

LinkMind supports API keys, key pools, authentication switches, and billing-related interfaces. Production deployments should follow least privilege:

- Use dedicated service accounts and least-privilege keys instead of personal development keys.
- Store secrets in secure configuration management or a secret manager, not in the repository.
- Use distinguishable keys for different environments, tenants, and providers, with rotation and revocation procedures.
- Protect management interfaces such as `/user/*`, `/apiKey/*`, and `/credit/*` with outer access controls.
- Use network isolation or trusted network policies for reverse proxies, consoles, management APIs, and internal cascade nodes.

## RAG, Documents, And Retrieval

RAG and document pipelines transform source files into retrievable content, so derived data must be protected as well:

- Vector stores, full-text indexes, graph-enhanced data, and caches should inherit the sensitivity level of source documents.
- When source documents are deleted or updated, derived indexes, caches, and trace records should be handled accordingly.
- Multi-tenant deployments should isolate knowledge bases, index namespaces, file storage paths, and access credentials.
- For Text-to-SQL, restrict database account permissions and apply extra validation to the execution scope of generated SQL.

## Agents, MCP, And Skills

Agents, MCP, Skills, Workers, and Pnps expand system capabilities and the execution surface. Review them before production use:

- Enable only MCP services, Skills, Workers, and agent runtimes from trusted sources.
- Configure least-privilege credentials for external tools and limit filesystem, network, and command execution scope.
- Use approval, audit, or sandboxing policies for tool calls that may produce side effects.
- Review runtime sync settings regularly to avoid accidental local agent configuration overwrites or drift.

## Cascade Networking

Cascade networking combines multiple LinkMind nodes into a larger runtime network. Deployments should ensure:

- Each node independently manages local data, concurrency, permissions, and backend credentials.
- Cascade links connect only trusted nodes and are protected through network controls, authentication, or reverse proxy policy.
- Data from different business domains, tenants, or security levels is physically or logically isolated.
- Nodes pass only the minimum context required to complete a task.

## Production Checklist

- Enable authentication, policy checks, and required filters.
- Replace all sample keys, default secrets, and test endpoints.
- Use HTTPS, trusted reverse proxies, and controlled administration entry points.
- Restrict access sources for consoles, management APIs, MCP services, and cascade nodes.
- Define access controls, retention windows, and cleanup processes for logs, caches, uploads, vector stores, and temporary directories.
- Review data-processing terms for model providers, cloud agent platforms, and third-party services.
- Establish configuration-change auditing, key rotation, vulnerability response, and abnormal-call alerting.

## Vulnerabilities And Security Issues

If you discover a security issue, do not disclose exploitable details in a public issue. Use the private channel designated by the maintainers and include reproduction steps, impact scope, version information, and suggested mitigations. After confirmation, maintainers should prioritize fixes, upgrade notes, or temporary mitigation guidance.

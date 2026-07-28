# SDD — Sessão #24: Observabilidade + Hardening contra exaustão

## Motivação

Hoje só `actuator/health`/`info` estão expostos e só login/registro têm rate limiting
(`LoginRateLimiter`, por IP+email). O resto da API (transações, export, import) não tem
limite nenhum, e a instância Lightsail (1GB RAM) é um alvo fácil de exaustão assim que
ficar exposta de verdade. Também prepara terreno pro job de sincronização periódica do
Open Finance (#22), que falha silenciosamente sem observabilidade.

## Escopo desta sessão (o que é código vs. o que é infra manual)

Parte do plano original (#24 no `PLANO-SDD.md`) depende de contas/serviços que só o
usuário pode provisionar (conta AWS CloudWatch, conta Cloudflare, acesso root à instância
Lightsail) — mesmo padrão da sessão #21 (deploy AWS), onde a criação de conta/DNS/secrets
ficou documentada como passo manual, não automatizada por mim.

**Implementado nesta sessão (código, testado):**
1. Rate limiting geral na API (não só auth), com limite mais agressivo em endpoints caros.
2. Logging estruturado (JSON) com foco em eventos de segurança.
3. Tuning de connection pool (HikariCP) e threads (Tomcat) pro perfil de produção.

**Documentado como runbook manual** (`infra/README.md`), não implementado em código:
4. Export de logs pro CloudWatch + alarme de tentativas de login falhas.
5. Cloudflare gratuito na frente da instância (DDoS/WAF na borda).
6. Endurecimento de SSH (fail2ban) sem quebrar o deploy via GitHub Actions.

## Tasks

1. **`ApiRateLimiter`** (`common/security/`): generaliza a janela fixa in-memory já usada
   pelo `LoginRateLimiter`, mas por **nome de regra + chave** (permite várias regras
   nomeadas simultâneas: `default` e `expensive`), keyed por IP do cliente.
2. **`ApiRateLimitFilter`** (`OncePerRequestFilter`): aplica a regra `default` (mais
   permissiva) em toda a API, e a regra `expensive` (mais restritiva) em paths de
   export/import (`/v1/transactions/export`, `/v1/import/**`). Pula `/actuator/health`.
   Registrado bem no início da cadeia de filtros (antes até do `JwtAuthFilter`), pra
   proteger também os endpoints públicos (login/register/refresh). Resposta 429 com
   `Retry-After`, corpo `ApiError` (mesmo formato do resto da API — escrito manualmente,
   já que filtros de segurança rodam antes do `@RestControllerAdvice`).
3. **Logging estruturado**: dependência `logstash-logback-encoder` + `logback-spring.xml`
   com encoder JSON no profile `prod` (console — o `docker logs`/CloudWatch Agent captura
   stdout do container); texto legível continua em dev. Logger dedicado
   `com.poupito.api.security` usado por `LoginAttemptLimiter`, `LoginRateLimiter` e
   `ApiRateLimitFilter` pra eventos de segurança (login falho, lockout, rate limit
   estourado) — MDC com `event`, `ip`, `path` pra facilitar filtro/alerta no CloudWatch
   Insights depois.
4. **Tuning de produção** (`application-prod.yml`): `HikariCP` (`maximum-pool-size`
   pequeno — a instância tem 1GB e o Postgres roda no mesmo host) e `server.tomcat.threads`
   (limite de threads compatível com a RAM disponível).
5. Testes: `ApiRateLimiterTest` (unit) + teste de integração validando 429 no endpoint
   geral e no `expensive` após estourar o limite configurado (baixo, só pro teste).
6. Runbook manual em `infra/README.md`: CloudWatch (export + alarm), Cloudflare (proxy +
   SSL mode Full strict), fail2ban (jail SSH, sem restringir por IP — os runners do
   GitHub Actions usam IPs dinâmicos).
7. Atualizar `CLAUDE.md` e `PLANO-SDD.md` (marcar #24 concluída, deixar claro o que é
   automatizado vs. manual).

## Critério de sucesso

- Rajada de requisições no endpoint geral e no `expensive` retorna 429 com `Retry-After`
  depois do limite configurado, sem derrubar a API.
- Logs em prod saem em JSON (verificável rodando com `SPRING_PROFILES_ACTIVE=prod`
  localmente); eventos de segurança carregam `event`/`ip`/`path` no MDC.
- JaCoCo ≥90% mantido.

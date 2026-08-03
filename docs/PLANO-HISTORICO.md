# Poupito — Histórico de sessões concluídas

> Este arquivo existe pra manter o `docs/PLANO-SDD.md` enxuto no dia a dia (ele é
> lido/editado toda vez que o roadmap é discutido, e cresce a cada sessão nova —
> sem separar o que já foi feito, o custo de token por leitura só sobe). Aqui vai
> o **texto completo** de cada sessão já concluída, exatamente como estava antes
> de ser resumida a uma linha no plano ativo. Cada `docs/session-NN-*/SDD.md`
> continua sendo a fonte primária de detalhe técnico de implementação — este
> arquivo é o meio-termo entre aquele detalhe e a linha única do plano ativo.
>
> Consultar aqui quando precisar do racional/decisões completas de uma sessão
> antiga; para o estado atual do roadmap, use `docs/PLANO-SDD.md`.

---

## Fase 0 — Fundação

**#1 — Estrutura Inicial + Backend Base** ✅ CONCLUÍDA (2026-07-07 — SDD: `docs/session-01-estrutura-inicial/SDD.md`)
Tasks: (1) monorepo `api/`+`web/`+`infra/`+`docs/`; (2) Docker Compose com Postgres 16; (3) Spring Boot 3.5 + Flyway V1 (`users`) + springdoc + healthcheck; (4) verificação (`docker compose up`, `/actuator/health`, Swagger UI).
Pré-req: nenhum.

**#2 — Auth JWT** ✅ CONCLUÍDA (2026-07-08 — SDD: `docs/session-02-auth-jwt/SDD.md`; cobertura 97,3%, JaCoCo 90% enforçado desde aqui)
Tasks: (1) register/login com password hash (BCrypt) e emissão de JWT; (2) Spring Security filter chain + contexto do usuário; (3) testes (Testcontainers); (4) verificação end-to-end via curl.
Pré-req: #1.

**#3 — Setup Frontend Angular** ✅ CONCLUÍDA (2026-07-09 — SDD: `docs/session-03-setup-frontend/SDD.md`; cobertura 100%/90%/100%/100%)
Tasks: (1) Angular 20 + Tailwind + tema dark do protótipo (variáveis CSS `--bg`, `--card`, `--accent`...); (2) layout shell — sidebar (Dashboard, Transações, Investimentos, Metas, Fixos, Configurações), topbar com month-picker; (3) telas login/registro + interceptor JWT + guards; (4) verificação (login funcional contra a API).
Pré-req: #2.

**#4 — CI/CD** ✅ CONCLUÍDA (2026-07-09 — SDD: `docs/session-04-cicd/SDD.md`; 5 workflows verdes no PR #4; pendência manual: branch protection em main/develop). **Fase 0 completa.**
Tasks: (1) GitHub Actions backend (build, test); (2) frontend (build, test); (3) CodeQL + Trivy + Dependency Review; (4) verificação (pipelines verdes).
Pré-req: #1–#3 (pode rodar em paralelo com #5+).

## Fase 1 — MVP (substitui a planilha)

**#5 — Contas & Categorias** ✅ CONCLUÍDA (2026-07-09 — SDD: `docs/session-05-contas-categorias/SDD.md`)
Tasks: (1) migration V2 + CRUD `accounts` (tipos, closing/due day); (2) CRUD `categories` (icon, cor, kind); (3) tela Configurações no Angular; (4) verificação.
Pré-req: #3.

**#6 — Transações (backend)** ✅ CONCLUÍDA (2026-07-09 — SDD: `docs/session-06-transacoes-backend/SDD.md`)
Tasks: (1) migration V3 + CRUD com validações (conta obrigatória, BigDecimal); (2) regra de vínculo à fatura pelo closing_day; (3) filtros por mês/conta/categoria + paginação; (4) testes + verificação.
Pré-req: #5.

**#7 — Transações (frontend)** ✅ CONCLUÍDA (2026-07-09 — SDD: `docs/session-07-transacoes-frontend/SDD.md`)
Tasks: (1) tabela mensal com tags coloridas de categoria (como no protótipo); (2) modal de lançamento rápido (data = hoje, última conta usada); (3) edição/exclusão + filtros; (4) verificação.
Pré-req: #6.

**#S — Segurança (STRIDE + LGPD)** ✅ CONCLUÍDA (2026-07-11 — inserida a pedido do usuário; SDD: `docs/session-S-seguranca/SDD.md`; docs: `docs/security/`). Migration **V4** (`refresh_tokens`). Auth migrada para cookies httpOnly + refresh rotacionado/revogável; rate limiting; fail-fast de segredos em prod; security headers; endpoints LGPD de exportação/exclusão. 101 testes API + 77 web.

**#8 — Fixos Recorrentes** ✅ CONCLUÍDA (2026-07-12 — SDD: `docs/session-08-fixos-recorrentes/SDD.md`)
Tasks: (1) migration **V5** (V4 usada na sessão #S) + CRUD; (2) job mensal de materialização + flag "pago?"; (3) tela Fixos com checkbox; (4) testes do job + verificação.
Pré-req: #6.

**#9 — Fechamento de Fatura** ✅ CONCLUÍDA (2026-07-13 — SDD: `docs/session-09-fechamento-fatura/SDD.md`; sem migration)
Tasks: (1) ciclo de vida da fatura (OPEN → CLOSED → PAID) + total lançado vs. declarado; (2) lançamento automático de ajuste (INVOICE_ADJUSTMENT) e redução ao detalhar; (3) UI de fatura por cartão; (4) testes + verificação.
Pré-req: #6.

**#10 — Orçamentos (orçado vs. realizado)** ✅ CONCLUÍDA (2026-07-13 — SDD: `docs/session-10-orcamentos/SDD.md`)
Tasks: (1) migration **V6** (V5 reservada pela sessão #8) + CRUD budgets; (2) endpoint orçado × realizado por categoria/mês; (3) tabela com barras de progresso (vermelho ao estourar, como no protótipo); (4) verificação.
Pré-req: #6. 126 testes API (JaCoCo ≥90%), testes web com cobertura ≥90/80/90/90.

**#11 — Dashboard Mensal + Panorama Anual** ✅ CONCLUÍDA (2026-07-13 — SDD: `docs/session-11-dashboard/SDD.md`)
Tasks: (1) endpoints agregados (entradas, gastos, saldo do mês, saldo acumulado, gasto por categoria, série anual); (2) cards + donut de categorias + tabela orçado/realizado; (3) gráfico de barras anual (entradas × gastos); (4) verificação visual contra o protótipo. Chart.js puro (sem `ng2-charts`, que exigiria `@angular/cdk`). 168 testes API (JaCoCo ≥90%), 126 testes web (cobertura ≥90/80/90/90).
Pré-req: #7, #10.

**#12 — Import da Planilha xlsx** ✅ CONCLUÍDA (2026-07-14 — SDD: `docs/session-12-import-planilha/SDD.md`). **Fase 1 (MVP) completa.**
Tasks: (1) parser Apache POI da Planilha_Gastos_2026 (abas mensais, fixos, entradas); (2) endpoint de upload + mapeamento categorias/contas + idempotência; (3) UI de importação com preview; (4) verificação com a planilha real. Verificado com o arquivo real do usuário: 440 linhas, 439 transações criadas, idempotência confirmada (reimport pula tudo como duplicata). 183 testes API (JaCoCo ≥90%), 134 testes web (cobertura ≥90/80/90/90).
Pré-req: #11. **Critério de sucesso da Fase 1: abandonar a planilha no mês seguinte.**

## Fase 2 — Investimentos

**#13 — Investimentos (backend)** ✅ CONCLUÍDA (2026-07-14 — SDD: `docs/session-13-investimentos-backend/SDD.md`)
Tasks: (1) migration V7 + CRUD investments/entries (aporte, resgate, atualização de saldo); (2) cálculo de rentabilidade TWR por período e por classe; (3) testes + verificação. 207 testes API (JaCoCo ≥90%).
Pré-req: #2 (independente do MVP).

**#14 — Integração CDI (Bacen SGS)** ✅ CONCLUÍDA (2026-07-15 — SDD: `docs/session-14-integracao-cdi/SDD.md`)
Tasks: (1) client HTTP da série 12 com cache local; (2) endpoint carteira × CDI acumulado; (3) testes com mock + verificação. 216 testes API (JaCoCo ≥90%); verificado com a API real do Bacen.
Pré-req: #13.

**#15 — Investimentos (frontend)** ✅ CONCLUÍDA (2026-07-16 — SDD: `docs/session-15-investimentos-frontend/SDD.md`)
Tasks: (1) cards por classe (patrimônio total, reserva, RF, RV); (2) gráfico de linha patrimônio × CDI; (3) lançamentos de aporte/resgate/atualização; (4) verificação contra o protótipo. 171 testes web (cobertura ≥90/80/90/90).
Pré-req: #14.

**#16 — Metas Financeiras** ✅ CONCLUÍDA (2026-07-16 — SDD: `docs/session-16-metas-financeiras/SDD.md`). **Fase 2 (Investimentos) completa.**
Tasks: (1) migration **V9** (não V6, já usada pela sessão #10) + CRUD goals/contributions + cálculo de aporte necessário; (2) tela Metas com barras de progresso e "R$ X/mês até data"; (3) verificação. 239 testes API (JaCoCo ≥90%), 152 testes web (cobertura ≥90/80/90/90).
Pré-req: #13.

## Fase 3 — Qualidade de vida

**#17 — Alertas de Orçamento + Busca e Tags** ✅ CONCLUÍDA (2026-07-16 — SDD: `docs/session-17-alertas-busca-tags/SDD.md`). **Primeira sessão da Fase 3.**
Tasks: (1) alerta ao estourar orçamento (badge/notificação no app); (2) busca full-text e filtros avançados; (3) tags livres em transações; (4) verificação. Migration **V10** (`transaction_tags`). 197 testes web (cobertura ≥90/80/90/90); verificado end-to-end no browser.
Pré-req: #11.

**#18 — Parcelamentos** ✅ CONCLUÍDA (2026-07-17 — SDD: `docs/session-18-parcelamentos/SDD.md`)
Tasks: (1) compra em N× gera N transações futuras vinculadas; (2) UI de parcelamento no lançamento; (3) verificação. Migration **V11**. 252 testes API + 206 testes web (cobertura ≥90/80/90/90); verificado end-to-end no browser.
Pré-req: #9.

**#19 — Export CSV/xlsx** ✅ CONCLUÍDA (2026-07-17 — SDD: `docs/session-19-export-csv-xlsx/SDD.md`)
Tasks: (1) endpoint de export (POI); (2) botão de export com filtros aplicados; (3) verificação. 259 testes API + 209 testes web (cobertura ≥90/80/90/90); verificado com arquivos reais (CSV e xlsx).
Pré-req: #7.

**#20 — PWA** ✅ CONCLUÍDA (2026-07-17 — SDD: `docs/session-20-pwa/SDD.md`)
Tasks: (1) `@angular/pwa` (manifest + service worker); (2) ajustes responsive mobile (sidebar vira drawer off-canvas com botão hambúrguer abaixo de 700px); (3) verificação. 213 testes web (cobertura ≥90/80/90/90); verificado end-to-end com service worker registrado, manifest servido e drawer mobile funcionando (login real + viewport mobile no browser).
Pré-req: #11.

**#21 — Deploy AWS** ✅ CONCLUÍDA (2026-07-18 — SDD: `docs/session-21-deploy-aws/SDD.md`; **provisionamento real confirmado** — produção rodando em `poupito.com` na Lightsail, deploys manuais via `workflow_dispatch` funcionando)
Tasks: (1) `web/Dockerfile` + `Caddyfile` (Caddy serve o estático do Angular e faz proxy `/api/*`; `api/Dockerfile` já existia da sessão #4) + `infra/docker-compose.prod.yml`; (2) **Lightsail US$5/mês** (decisão do usuário — logo, imagens `linux/amd64`, não ARM); (3) `infra/scripts/backup.sh` (pg_dump → S3, lifecycle 30 dias) + `setup-host.sh` (swap 2GB); (4) `.github/workflows/deploy.yml` (SSH manual via `workflow_dispatch`) + job `docker` novo em `ci-web.yml`; (5) smoke test local do compose completo (proxy, fallback SPA, healthcheck).
Pré-req: #4 + MVP estável (recomendado após #12).

## Fase 4 — Hardening & Open Finance

**#23 — Identidade visual (logo + marca + paleta oficial)** ✅ CONCLUÍDA (SDDs: rename `docs/session-23-rebrand-guaranin/SDD.md` (2026-07-19), rename `docs/session-23-rebrand-poupito/SDD.md` (2026-07-22), visual `docs/session-23-identidade-visual/SDD.md` (2026-07-22))
**Nome:** "DinDin" → "Guaranin" (2026-07-19) → "**Poupito**" (2026-07-22, final — "Guaranin" soava a guaraná/guarani; "Poupito", de "poupar", comunica economizar). Domínio `poupito.com` a registrar pelo usuário.
**Identidade visual ("Crescimento Seguro"):** logo "P" azul-marinho + broto verde (fornecido pelo usuário); paleta navy `#0F172A`/`#1E293B` + verde esmeralda `#059669`/`#10B981` + neutras branco/cinza-claro; slogan "Descomplique, poupe, Poupito.". Implementado: **tema claro como padrão + toggle dark/light** (`ThemeService`, variáveis CSS `:root`/`[data-theme="dark"]`, script inline anti-flash); sidebar navy como "chrome" da marca nos dois temas; ícones PWA + favicon reais gerados do logo (substituem o placeholder do Angular da sessão #20); gráficos Chart.js e tints semânticos passam a respeitar o tema. 217 testes web; verificado nos dois temas no browser.
**Melhorias futuras (não bloqueiam):** self-hostar fonte Inter (feito na #33, trocada por Manrope); recolorir gráficos ao alternar tema sem navegar; favicon multi-resolução.
Pré-req: nenhuma.

**#24 — Observabilidade + Hardening contra exaustão** ✅ CONCLUÍDA (2026-07-27 — SDD: `docs/session-24-observabilidade-hardening/SDD.md`)
Motivação: só `actuator/health`/`info` estavam expostos e só login/registro tinham rate limiting (`LoginRateLimiter`, por IP+email) — o resto da API (transações, export, import) não tinha limite nenhum, e a instância Lightsail de 1GB é um alvo fácil de exaustão assim que ficar pública. Motivador direto: o job de sincronização periódica do Open Finance (#22) é exatamente o tipo de coisa que falha silenciosamente sem observabilidade.
Entregue em **código** (ver `CLAUDE.md`, seção "Observabilidade + Hardening"): `ApiRateLimiter`/`ApiRateLimitFilter` — rate limit geral por IP, regra `default` (120/min, toda a API, roda antes até do `JwtAuthFilter` pra cobrir os endpoints públicos) e `expensive` (10/min, export/import); logging estruturado JSON em produção (`logback-spring.xml` + `logstash-logback-encoder`), logger `com.poupito.api.security` com MDC (`event`/`ip`/`path`) usado por `LoginAttemptLimiter`/`LoginRateLimiter`/`ApiRateLimitFilter`; tuning de `HikariCP`/Tomcat em `application-prod.yml` pra instância de 1GB; driver `awslogs` já configurado no `docker-compose.prod.yml` pro serviço `api`. Bug encontrado e corrigido durante a implementação: o filtro usava `getRequestURI()` (inclui o context-path `/api`, nunca batia com os padrões configurados) — trocado por `getServletPath()`.
Ficou como **runbook manual** em `D:\Docs\Poupito\runbook-sessao-24-observabilidade-hardening.md` (requer conta/acesso que só o usuário tem, mesmo padrão da #21): export de logs pro CloudWatch + alarme de lockout, Cloudflare gratuito (WAF/DDoS na borda, SSL mode Full strict) e fail2ban no SSH (sem restringir por IP — os runners do GitHub Actions usam IPs dinâmicos). Correção registrada no runbook: o Lightsail **não suporta anexar IAM role a uma instância** (diferente da EC2) — o caminho real é IAM user + access key configurada via `aws configure` no host (não no `.env`, já que o driver `awslogs` do Docker roda no nível do daemon, não do container).
Testes: `ApiRateLimiterTest` (unit) + `ApiRateLimitFilterIntegrationTest` (429 com `Retry-After` no endpoint geral e no `expensive`, `/actuator/health` nunca limitado). Gotcha de suíte: o rate limit geral roda por trás do mesmo contexto Spring reaproveitado por quase todas as `*FlowIntegrationTest` (mesmo IP `localhost`) — o `maven-surefire-plugin` sobrescreve os limites pra um valor bem alto via `systemPropertyVariables` só durante o build, e o teste dedicado do filtro sobrescreve de novo pra baixo via `@TestPropertySource` (contexto isolado).
Pré-req: #21 (precisava da instância real em produção pra fazer sentido — a lógica de código não depende disso, mas o runbook manual sim).

**#25 — Remodelagem Contas & Cartões (método de pagamento)** ✅ CONCLUÍDA (2026-07-22 — SDD: `docs/session-25-remodelagem-contas-cartoes/SDD.md`) — **prioridade sobre #24/#22 (decisão do usuário: o rearranjo de domínio vem antes do resto)**
Motivação: cartão de crédito como tipo de conta mistura "onde o dinheiro vive" com "instrumento de pagamento"; e o importador pulava a regra de fatura (bug: aba Faturas zerada após import) — corrigido dentro desta sessão.
Decisões (usuário): entidade **Card** separada sempre vinculada a uma conta; compra no crédito só debita a conta **quando a fatura é paga** (`INVOICE_PAYMENT`, excluído das agregações de gasto pra não contar duas vezes); **dados transacionais recomeçados** na migration V12 (app pré-produção). Método de pagamento (crédito/débito/dinheiro) é **derivado**, não digitado; parcelamento passa a exigir cartão; fixos seguem em conta (cartão em fixos = melhoria futura, resolvido na #32).
Tasks: (1) migration V12 + CRUD `/v1/cards`; (2) transações xor conta/cartão + fatura por cartão + endpoint pagar fatura; (3) importer roteando cartão→fatura; (4) testes backend; (5) frontend (painel Cartões, "Pagar com", badge de método, pagar fatura, import); (6) testes web + verificação e2e; (7) docs + PR.
Pré-req: nenhum técnico (roda já).

**#26 — Bugfix: estado de contas dessincronizado na UI (pós-#25)** ✅ CONCLUÍDA (2026-07-23 — SDD: `docs/session-26-fix-estado-contas/SDD.md`; bugs reportados pelo usuário 2026-07-22)
Solução: stores reativos com signals (`core/state/{account,card,category}.store.ts`) como fonte única — toda mutação dá `refresh()` e propaga pros consumidores. Mensagem específica no 404 (conta/cartão apagado noutra tela) + refresh; nota de UX no painel de Cartões ("débito não é cartão"). 255 testes web (97/85/93/96); verificado e2e no browser (apagar conta some de todos os selects sem reload, nas duas direções e cross-rota).

**Sintomas relatados:**
1. Ao criar cartão vinculado a uma conta (ex.: cartão "Nubank" → conta "Débito"), a UI retorna **"Erro ao salvar o cartão"**.
2. Os selects de conta **listam contas que o usuário já apagou** (ex.: "Débito" aparece no dropdown do form de cartão mesmo tendo sido excluída).

**Causa-raiz (confirmada por análise de código + inspeção do banco):** os dois sintomas têm **uma única origem**. Cada componente (`accounts-panel`, `cards-panel`, `transactions`, `invoices`, `importer`, `recurring`, `dashboard`, `investments`, `budgets`, `goals`) carrega a lista de contas **independentemente em `ngOnInit`** e nunca ressincroniza. Quando o usuário apaga uma conta no `accounts-panel` (que se recarrega sozinho), os dropdowns dos **outros** componentes continuam com a lista antiga em cache — mostrando contas que já não existem (sintoma 2). Ao escolher uma dessas contas fantasma e salvar o cartão, o backend responde **404 "Conta não encontrada"** (`CardService.ownedAccount`), que o front exibe como o genérico "Erro ao salvar o cartão" (sintoma 1). Confirmado no banco: a conta "Débito" realmente não existe mais (só restaram Itau e Nubank para o usuário) — o delete funcionou no backend; foi a UI que ficou dessincronizada.

**Tasks realizadas:**
1. **Estado de contas compartilhado e reativo:** store baseado em signals (`core/state/AccountStore`, e generalizado pra `CardStore`/`CategoryStore`) que todos os consumidores leem, atualizado após qualquer mutação (create/update/delete).
2. **Mensagem de erro específica no 404:** quando a conta/cartão referenciado sumiu, mensagem clara + recarrega o select, em vez do genérico "Erro ao salvar".
3. **UX "débito não é cartão":** texto/orientação deixando claro que **Cartões = só crédito**; débito e dinheiro = contas.
4. **Mensagem do delete bloqueado por FK** explicando o motivo (cartão/transação vinculada).
5. Regressão de testes web (≥90/80/90/90) + verificação e2e: apagar conta e confirmar que some de **todos** os selects sem precisar recarregar a página.

**Nota (2026-08-02):** este mesmo padrão de "store singleton sem reset" foi identificado como a causa-raiz de um bug NOVO e distinto — vazamento de dados entre contas de usuários diferentes no mesmo navegador após logout/login (`ensureLoaded()`/`loaded` nunca resetados no logout) — corrigido na sessão **#42**.

**#29 — Senhas: recuperação por email + endurecimento (regras fortes · mostrar senha · lockout)** ✅ CONCLUÍDA (2026-07-25 — SDD: `docs/session-29-recuperacao-senha/SDD.md`)
Entregue: migration **V13** (`password_reset_tokens`, token opaco 256 bits guardado como hash SHA-256, single-use, expiry PT30M); `POST /auth/forgot-password` (**sempre 204**, anti-enumeração) e `POST /auth/reset-password` (valida/consome token, aplica senha forte, **revoga todos os refresh tokens**). Validador `@StrongPassword` compartilhado (≥12 chars maiúscula+minúscula+número+símbolo) em registro e reset — **login segue só `@NotBlank`** (senhas antigas continuam logando; sem migração de senha). Envio de email **pluggável** (`EmailSender`: `LoggingEmailSender` default loga o link em dev; `SmtpEmailSender`/AWS SES quando `app.mail.enabled=true`). Lockout **por conta** (`LoginAttemptLimiter`, 5 falhas → 5 min, reseta no sucesso) com `Retry-After` no 429. Front: telas `/esqueci-senha` e `/redefinir-senha`, toggle "mostrar senha" (olho) em login/cadastro/redefinir, **hint de política só em cadastro/reset (removido do login)**, feedback de lockout com minutos restantes. 293 testes API (JaCoCo ≥90%) + 280 testes web (97,2/86,6/93,8/97,1); verificado e2e (fluxo de reset via `EmailSender` de teste; telas no browser). **SES ainda a configurar pelo usuário** (`D:/Docs/Poupito/setup-ses-email.md`) — não bloqueia (email logado em dev).
Fluxo:
- `POST /auth/forgot-password {email}` → **sempre 204** (anti-enumeração — não revela se o email existe). Se existir, gera token de reset **opaco (256 bits), guardado como hash SHA-256** (single-use, expiry ~30 min) e envia email com link `https://poupito.com/redefinir-senha?token=...`.
- `POST /auth/reset-password {token, newPassword}` → valida (não expirado, não usado), aplica a senha forte, marca o token como usado e **revoga todos os refresh tokens** do usuário.
- **Rate limiting** no forgot-password (reusa `LoginRateLimiter` por IP+email) contra email-bombing/enumeração.
Endurecimento de senha & login: regras fortes (≥12 chars maiúscula+minúscula+número+símbolo), toggle "mostrar senha", lockout de 5 tentativas/5 min por conta, dica de política de senha só em cadastro/reset (nunca no login).
Setup do SES documentado em `D:/Docs/Poupito/setup-ses-email.md` (fora do repo): verificação de domínio + DKIM na Cloudflare, SPF/DMARC, credenciais SMTP e saída do sandbox.

**#32 — Bugfix: gastos fixos no cartão de crédito** ✅ CONCLUÍDA (2026-07-25 — SDD: `docs/session-32-fixos-no-cartao/SDD.md`; bug reportado pelo usuário 2026-07-25)
Causa-raiz: limitação **estrutural** herdada da migration V5 (anterior à #25) — `recurring_transactions.account_id` era `NOT NULL` e não existia `card_id`; a materialização usava sempre `Transaction.forAccount`. Assinatura cobrada no cartão (Netflix/Spotify) não podia ser cadastrada como fixo. Fecha a pendência que a própria #25 deixou no código ("fixo em cartão = melhoria futura").
Entregue: migration **V14** (`account_id` nullable + `card_id` + CHECK `chk_recurring_account_xor_card` + índice; aditiva, **sem backfill**); `RecurringRequest` com conta **XOR** cartão (400 "Informe conta OU cartão"; entrada em cartão → 400); materialização de fixo no cartão **vinculando à fatura do período** pelo `closing_day` via `Transaction.materializedOnCard`, nascendo `paid=true` (a quitação é o `INVOICE_PAYMENT`, não checkbox por mês); fixo em conta **inalterado** (`paid=false` + checkbox); `method`/`cardName` nas respostas. Front: seletor **"Pagar com"** (contas + cartões, cartões escondidos em Entrada), `CardStore`, badge de método, "na fatura" no lugar do checkbox. 316 testes API (JaCoCo ≥90%) + 286 testes web (97,2/86,8/93,9/97,2); verificado e2e com API real e no browser.
Pré-req: #25. Roda independente do resto da fila.

**#33 — Refino visual: tipografia, tokens e ícones ("menos cara de IA")** ✅ CONCLUÍDA (2026-08-01 — SDD: `docs/session-33-refino-visual/SDD.md`)
Motivação: a marca tem personalidade (navy + esmeralda, "Crescimento Seguro", logo próprio — #23), mas a **execução visual** era genérica. Auditoria do CSS encontrou: escala tipográfica achatada (quase tudo entre 11-15px, sem hierarquia); emoji como ícone de UI (☰, ☀️/🌙 — não acompanha tema, quebra alinhamento); `border-radius` sem token (4/6/8/9/10/12px espalhados); sem token de espaçamento; fonte Inter (default genérica de app gerado por IA); bugfix de coluna de ações desalinhada em 8 telas (`.row-actions` com `display: flex` direto no `<td>` quebrava `vertical-align`); tabelas sem tratamento responsivo no mobile (mesmas 8 telas); botões sem animação/feedback de clique.
Entregue (ver `CLAUDE.md`, seção "Refino visual: tokens, mobile e botões"): tokens de tipografia/raio/espaçamento/transição em `styles.css` (`--text-*`, `--radius-*`, `--space-*`, `--transition-fast`); emoji → SVG inline no menu e no toggle de tema; bugfix da coluna de ações (`.row-actions` não usa mais `display: flex` no `<td>`); **mobile: tabelas viram cards empilhados** abaixo de 640px via `data-label` nos `<td>` + `@media` global, aplicado nas 8 telas (`goals` não precisou, já era card); botões com `transition`, `:active` scale, sombra no hover, `:focus-visible` desenhado, classe `.btn.loading` pronta, tudo respeitando `prefers-reduced-motion`.
Complementos entregues na mesma sessão (pedido do usuário depois de ver o resultado dos tokens): elevação no hover de `.card`/`.panel`; modais com entrada suave (fade + scale, 4 telas); barra de progresso anima largura; `.nav-item` do menu lateral ganha transição no hover; emoji 🎯 das Metas → SVG; **troca de fonte Inter → Manrope** — self-hosted (`web/public/fonts/manrope-variable.woff2`, variável, cobre 400-800, subset latin cobre acentuação pt-BR), escolhida pelo usuário entre 3 opções. `.amount-col`/`.card .value` ganham `tabular-nums`.
Gotcha do build: `url()` relativo em CSS não resolve arquivo de `public/` (esbuild tenta bundlar como módulo) — precisa ser path absoluto (`/fonts/...`).
Testes: 295/295 Karma, cobertura 97,18/87,27/93,96/97,12.
Pré-req: nenhum.

**#34 — Bugfix: nome de conta e de cartão único por usuário** ✅ CONCLUÍDA (2026-07-25 — SDD: `docs/session-34-nome-unico-conta-cartao/SDD.md`; reportado pelo usuário 2026-07-25)
Causa-raiz: `categories` já tinha unicidade (V2), mas **`accounts` e `cards` não tinham nenhuma**. Como conta/cartão são **rótulos de escolha** em toda a UI (seletor "Pagar com", filtros, mapeamento do importador que casa por nome), dois "Nubank" viram duas opções idênticas no dropdown.
Entregue: migration **V15** com índices únicos em `(user_id, lower(name))` para as duas tabelas — **case-insensitive** — e **deduplicação antes de criar o índice** (renomeia repetidos para "Nome (2)" mantendo o mais antigo). Services validam no create e no update (409 `DuplicateResourceException`, só se o nome mudou no update). Front mostra a mensagem real do 409.
⚠️ Ordem de merge histórica: usou **V15** porque a V14 estava no PR #62 (#32), ainda aberto na época.

**#40 — Landing pública + FAQ para novos usuários** ✅ CONCLUÍDA (2026-08-01 — SDD: `docs/session-40-landing-faq/SDD.md`; pedido do usuário: "colocar algumas coisas agora que tá produtivo")
Motivação: antes desta sessão **não existia nenhuma página pública** — `app.routes.ts` tinha `path: ''` casando com o `Shell` (autenticado), que redirecionava qualquer visitante não-logado direto pro `/login`.
Entregue (ver `CLAUDE.md`, seção "Landing pública + FAQ"): **Landing** (`features/landing/`, rota `/`) explicando o Poupito e suas features, com CTA "Criar conta grátis"; **Faq** (`features/faq/`, rota `/faq`) com perguntas comuns pré-cadastro via `<details>`/`<summary>` nativo. **Reestruturação de rotas sem quebrar nenhum path existente**: a Landing entra como primeira rota com `path: '', pathMatch: 'full'` (essencial — sem isso faria *prefix match* e "roubaria" `/dashboard` etc. do `Shell`); `redirectIfAuthenticatedGuard` novo manda usuário já logado direto pro `/dashboard` ao visitar `/`.
Testes: 9 novos specs, 304/304 Karma, cobertura 97,21/87,36/93,97/97,14. Verificado ao vivo: `/` mostra Landing, `/faq` acessível sem sessão, `/dashboard` sem sessão continua redirecionando pro login.
Fora de escopo (virou a #39): SEO propriamente dito.
Pré-req: nenhum.

**Complemento pós-#40 (2026-08-02):** botão "Criar conta grátis" (Landing/FAQ) passou a levar direto pro modo cadastro via `?mode=register`; tela de login/cadastro ganhou diferenciação visual entre os dois modos (accent roxo `--purple` no modo cadastro vs. verde `--accent` no login, reaproveitando token já existente na paleta); logo do Poupito na tela de login virou link de volta pra landing (`routerLink="/"`); "Voltar para o início" na FAQ virou botão (`.btn-ghost`) em vez de link de texto puro. Landing também recebeu ajustes de adequação a WebView mobile: `viewport-fit=cover`, `env(safe-area-inset-*)` no header/footer, `-webkit-tap-highlight-color: transparent`, `<html>` com background do tema (evita flash no overscroll elástico), grid de features blindado contra overflow em telas <240px, área de toque do link "Dúvidas" ampliada pra ~44px.

---

## Histórico de reordenações da fila (registro cronológico)

> Snapshots das notas de "ordem de execução" conforme foram escritas ao longo do
> projeto — preservados aqui como registro; a ordem **atual** vive só no
> `PLANO-SDD.md`, não precisa reconciliar com o que está abaixo.

**2026-07-25:** Ordem decidida: ~~#29 (recuperação de senha)~~ ✅ → ~~#32 (fixos no cartão)~~ ✅ → #24 (hardening) → #27/#28/#30/#31 → #22 (Open Finance) por último. A #22 fica pro fim a pedido do usuário (rodar todas as outras antes). Flexíveis: #30 (template de import — pequena, encaixa cedo), #27 (arquivar contas/cartões), #28 (saldo por conta) e #31 (empréstimos "a receber" — melhor após #28).

**2026-07-31:** Ordem atualizada: #26 ✅ → #29 ✅ → #32 ✅ → #24 ✅ → #33 → (#27/#28/#30/#31/#35/#36/#37/#38 quando o usuário quiser) → #22 por último. A próxima era a #33 (refino visual) — decisão do usuário 2026-07-27: usuários já sentiam os problemas visuais na prática, então furou a fila na frente das sessões flexíveis.

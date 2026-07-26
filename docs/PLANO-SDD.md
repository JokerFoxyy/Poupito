# Poupito — Plano SDD de Implementação

> **Fonte:** `DinDin/spec-app-financeiro.md` + `DinDin/prototipo-dashboard.html` (pasta local fora do repo git, nome não relacionado à marca)
> **Criado em:** 2026-07-07
> **Decisão do usuário:** Frontend em **Angular** (a spec original sugeria React) e APIs em **Java**.
> Este arquivo é o documento-mestre. Cada sessão ganhará seu próprio SDD detalhado em
> `docs/session-NN-nome/SDD.md` no momento em que for iniciada (mesmo padrão do ContratoIA).

---

## 1. Stack definida

| Camada | Tecnologia | Observação |
|---|---|---|
| Backend | Java 21 + Spring Boot 3.5.x | Virtual threads; pacotes por feature (`transaction/`, `budget/`, `investment/`, `goal/`...) |
| API | REST + OpenAPI (springdoc) | Contrato documentado para futuro app mobile |
| Banco | PostgreSQL 16 (Docker Compose) | Dinheiro em `NUMERIC(14,2)` / `BigDecimal`; datas em `LocalDate` |
| Migrations | Flyway | Desde a V1 |
| Auth | Spring Security + JWT próprio | Mais leve que Keycloak para 1 usuário; multiusuário desde o início |
| Frontend | **Angular 20 + TypeScript** (standalone components, signals) | Adaptação da spec (React → Angular) |
| UI | Tailwind CSS + componentes próprios | Reproduzir o tema dark do protótipo (`prototipo-dashboard.html`) |
| Gráficos | Chart.js via ng2-charts | O protótipo já usa Chart.js (donut, barras, linha) |
| Import xlsx | Apache POI (backend) | Parser da Planilha_Gastos_2026 |
| Testes | JUnit 5 + Testcontainers (Postgres real); Karma/Jasmine no front | Meta de cobertura como no ContratoIA |
| CI | GitHub Actions (build + test + CodeQL/Trivy) | Mesmo padrão do ContratoIA |
| Deploy | Docker Compose em AWS Lightsail US$5 (ou EC2 t4g.micro) + Caddy | Conforme seção 6 da spec |

**Regras de ouro (da spec):** nunca float/double para dinheiro; API separada do front; saldo mensal é **cálculo**, não célula (`saldo(mês) = saldo(mês-1) + entradas − gastos`).

## 2. Estrutura do monorepo

```
Poupito/                  (repo git)
├── api/                    Spring Boot (Java 21, Maven)
│   └── src/main/java/com/poupito/api/
│       ├── auth/  account/  category/  transaction/  invoice/
│       ├── recurring/  budget/  goal/  investment/  dashboard/  importer/
│       └── common/         (config, security, erros, money)
├── web/                    Angular 20
│   └── src/app/
│       ├── core/           (auth, interceptors, api client, layout shell)
│       ├── features/       (dashboard, transactions, investments, goals, recurring, settings)
│       └── shared/         (cards, tabelas, month-picker, barras de progresso)
├── infra/                  docker-compose.yml (postgres, api, web/caddy), scripts
└── docs/                   PLANO-SDD.md (este arquivo) + session-NN-*/SDD.md
```

## 3. Modelo de dados

O schema completo está na seção 2 da spec (`spec-app-financeiro.md`) e vira as migrations Flyway:

- **V1:** `users`
- **V2:** `accounts` (CHECKING | CREDIT_CARD | CASH, closing_day/due_day), `categories` (EXPENSE | INCOME, icon, color)
- **V3:** `transactions` (EXPENSE | INCOME | INVOICE_ADJUSTMENT, account obrigatória, invoice_id), `card_invoices` (OPEN | CLOSED | PAID, declared_total)
- **V4:** `refresh_tokens` (sessão #S, segurança — não estava no plano original, entrou na frente por pedido do usuário)
- **V5:** `recurring_transactions` (fixos com day_of_month, active) — sessão #8
- **V6:** `budgets` (categoria × mês × valor esperado) — sessão #10
- **V7:** `goals` + `goal_contributions`
- **V8:** `investments` (RESERVA | RENDA_FIXA | RENDA_VARIAVEL) + `investment_entries` (APORTE | RESGATE | ATUALIZACAO_SALDO)

Cada migration entra na sessão que implementa a feature correspondente. Numeração vai sendo ajustada conforme a ordem real de merge (não a ordem do roadmap), já que sessões podem ficar com PR aberto aguardando aprovação do usuário antes de outra ser iniciada.

## 4. Regras de negócio críticas

1. **Vínculo de fatura:** lançamento em cartão de crédito é atribuído à `card_invoice` do período conforme `date` × `closing_day` do cartão.
2. **Fechamento de fatura:** usuário informa `declared_total`; se difere do somatório dos lançamentos, o app cria `INVOICE_ADJUSTMENT` com a diferença. Ao detalhar gastos esquecidos depois, o ajuste é reduzido automaticamente.
3. **Materialização de fixos:** job `@Scheduled` mensal cria transactions a partir de `recurring_transactions`, com flag "pago?".
4. **Rentabilidade (fase 2):** TWR simplificado por período: `(saldo_atual − saldo_anterior − aportes) / saldo_anterior`. Comparação com CDI via API SGS do Bacen (série 12, sem chave).
5. **Metas:** aporte mensal necessário = `(target_amount − acumulado) / meses_restantes`.

## 5. Sessões planejadas

### Fase 0 — Fundação

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

### Fase 1 — MVP (substitui a planilha)

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

### Fase 2 — Investimentos

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

### Fase 3 — Qualidade de vida

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

**#21 — Deploy AWS** ✅ CÓDIGO CONCLUÍDO (2026-07-18 — SDD: `docs/session-21-deploy-aws/SDD.md`) — **provisionamento real pendente do usuário**
Tasks: (1) `web/Dockerfile` + `Caddyfile` (Caddy serve o estático do Angular e faz proxy `/api/*`; `api/Dockerfile` já existia da sessão #4) + `infra/docker-compose.prod.yml`; (2) **Lightsail US$5/mês** (decisão do usuário — logo, imagens `linux/amd64`, não ARM); (3) `infra/scripts/backup.sh` (pg_dump → S3, lifecycle 30 dias) + `setup-host.sh` (swap 2GB); (4) `.github/workflows/deploy.yml` (SSH manual via `workflow_dispatch`) + job `docker` novo em `ci-web.yml`; (5) smoke test local do compose completo (proxy, fallback SPA, healthcheck) — verificação end-to-end **em produção real** ainda pendente: requer o usuário comprar domínio, criar a instância Lightsail e configurar os secrets do GitHub (passo a passo em `infra/README.md`).
Pré-req: #4 + MVP estável (recomendado após #12).

### Fase 4 — Hardening & Open Finance

> **Ordem de execução (atualizada 2026-07-25): #26 ✅ → #29 ✅ → #32 ✅ → #24 → (#27/#28/#30/#31 quando o usuário quiser) → #22 por último.** Já foram: #26 (contas dessincronizadas), #29 (recuperação de senha + endurecimento) e #32 (fixos no cartão de crédito). A **próxima é a #24 (observabilidade/hardening)**. A **#22 (Open Finance) foi deliberadamente jogada pro fim** (decisão do usuário 2026-07-25): rodar todas as outras antes. A #24 continua vindo **antes** da #22 de propósito (a observabilidade suporta o job de sync do Open Finance). As #27/#28/#30/#31 encaixam quando o usuário quiser, mas **todas antes da #22**.

**#24 — Observabilidade + Hardening contra exaustão** 📋 PLANEJADA (roda **antes** da #22 — decisão do usuário; a refinar quando a Fase 3 terminar, mesma lógica da sessão #S)
Ideia inicial: hoje só `actuator/health`/`info` estão expostos e só login/registro tem rate limiting (`LoginRateLimiter`, por IP+email) — o resto da API (transações, export, import) não tem limite nenhum, e a instância Lightsail de 1GB é um alvo fácil de exaustão assim que ficar pública. Motivador direto: o job de sincronização periódica do Open Finance (#22) é exatamente o tipo de coisa que falha silenciosamente sem observabilidade — melhor ter isso pronto antes.
Tasks a refinar: (1) logs estruturados (JSON) por nível, com foco em eventos de segurança (falha de login, hits no rate limiter, 401/429, 5xx); (2) exportar esses logs pra **CloudWatch** (fora da instância — sobrevive a um comprometimento onde o atacante apaga logs locais) com retenção curta (30-90 dias) pra controlar custo, mais um **CloudWatch Alarm** simples (ex.: pico de tentativas de login falhas) notificando por email; (3) rate limiting geral na API (não só auth) — provavelmente via módulo de rate limit do Caddy ou filtro genérico no Spring, com limite mais agressivo nos endpoints caros (export xlsx via Apache POI, import de planilha); (4) **Cloudflare gratuito** na frente da instância — proteção DDoS básica + rate limiting na borda antes mesmo de chegar no Lightsail, mais barato/efetivo que construir tudo na aplicação; (5) revisar limites de connection pool (HikariCP) e threads (Tomcat) pra não estourar a RAM da instância sob carga; (6) **endurecer o SSH sem quebrar o CD**: manter `PasswordAuthentication no` (key-only, padrão do Lightsail) + **fail2ban** (jail de SSH) — **não** restringir a 22 ao IP do usuário, senão o deploy do GitHub Actions (que faz SSH de IPs dinâmicos dos runners) para de funcionar; (7) verificação: simular rajada de requisições e confirmar que a aplicação se protege sem cair.
Camadas de firewall (esclarecimento): o **firewall do Lightsail** (aba Networking, portas 22/80/443) já cobre **rede (L3/L4)** — não precisa subir appliance nenhuma. O que falta é **aplicação (L7): DDoS/WAF/rate limiting** = Cloudflare (task 4) + rate limiting (task 3). Ao ligar o **proxy da Cloudflare (nuvem laranja)** — hoje em *DNS only* pra o ACME — colocar o SSL/TLS mode em **Full (strict)** antes, senão quebra (loop de redirect com o Caddy).
Pré-req: #21 (precisa da instância real rodando em produção pra fazer sentido testar isso de verdade).

**#22 — Conexão bancária via Open Finance** 📋 PLANEJADA — **última sessão da fila** (decisão do usuário 2026-07-25: rodar todas as outras antes; a refinar quando chegar a vez)
Ideia inicial: conectar contas de banco via agregador certificado em Open Finance (Pluggy ou Belvo) para puxar extratos/saldos automaticamente, reduzindo lançamento manual.
Tasks a refinar: (1) avaliar Pluggy vs. Belvo (custo por conta conectada, cobertura de bancos, free tier); (2) fluxo de consentimento OAuth do usuário com o banco (conexão, expiração/reautenticação de token); (3) endpoint/job de sincronização periódica de extratos → mapeamento para `transactions` (evitar duplicidade com lançamentos manuais); (4) UI de gerenciamento de conexões bancárias; (5) verificação end-to-end com conta de banco real (sandbox do agregador).
Pré-req: Fase 3 completa (MVP estável + deploy) **+ #24**, e o usuário optou por deixá-la **por último** (depois de #27/#28/#30/#31). Risco/trade-off a decidir: custo recorrente por conta conectada escala com base de usuários — mais vantajoso enquanto uso é pessoal (poucas contas) do que se o produto virar SaaS multiusuário sem repasse desse custo.

**#23 — Identidade visual (logo + marca + paleta oficial)** ✅ CONCLUÍDA (SDDs: rename `docs/session-23-rebrand-guaranin/SDD.md` (2026-07-19), rename `docs/session-23-rebrand-poupito/SDD.md` (2026-07-22), visual `docs/session-23-identidade-visual/SDD.md` (2026-07-22))
**Nome:** "DinDin" → "Guaranin" (2026-07-19) → "**Poupito**" (2026-07-22, final — "Guaranin" soava a guaraná/guarani; "Poupito", de "poupar", comunica economizar). Domínio `poupito.com` a registrar pelo usuário.
**Identidade visual ("Crescimento Seguro"):** logo "P" azul-marinho + broto verde (fornecido pelo usuário); paleta navy `#0F172A`/`#1E293B` + verde esmeralda `#059669`/`#10B981` + neutras branco/cinza-claro; slogan "Descomplique, poupe, Poupito.". Implementado: **tema claro como padrão + toggle dark/light** (`ThemeService`, variáveis CSS `:root`/`[data-theme="dark"]`, script inline anti-flash); sidebar navy como "chrome" da marca nos dois temas; ícones PWA + favicon reais gerados do logo (substituem o placeholder do Angular da sessão #20); gráficos Chart.js e tints semânticos passam a respeitar o tema. 217 testes web; verificado nos dois temas no browser.
**Melhorias futuras (não bloqueiam):** self-hostar fonte Inter; recolorir gráficos ao alternar tema sem navegar; favicon multi-resolução.
Pré-req: nenhuma.

**#25 — Remodelagem Contas & Cartões (método de pagamento)** ✅ CONCLUÍDA (2026-07-22 — SDD: `docs/session-25-remodelagem-contas-cartoes/SDD.md`) — **prioridade sobre #24/#22 (decisão do usuário: o rearranjo de domínio vem antes do resto)**
Motivação: cartão de crédito como tipo de conta mistura "onde o dinheiro vive" com "instrumento de pagamento"; e o importador pulava a regra de fatura (bug: aba Faturas zerada após import) — corrigido dentro desta sessão.
Decisões (usuário): entidade **Card** separada sempre vinculada a uma conta; compra no crédito só debita a conta **quando a fatura é paga** (`INVOICE_PAYMENT`, excluído das agregações de gasto pra não contar duas vezes); **dados transacionais recomeçados** na migration V12 (app pré-produção). Método de pagamento (crédito/débito/dinheiro) é **derivado**, não digitado; parcelamento passa a exigir cartão; fixos seguem em conta (cartão em fixos = melhoria futura).
Tasks: (1) migration V12 + CRUD `/v1/cards`; (2) transações xor conta/cartão + fatura por cartão + endpoint pagar fatura; (3) importer roteando cartão→fatura; (4) testes backend; (5) frontend (painel Cartões, "Pagar com", badge de método, pagar fatura, import); (6) testes web + verificação e2e; (7) docs + PR.
Pré-req: nenhum técnico (roda já).

**#26 — Bugfix: estado de contas dessincronizado na UI (pós-#25)** ✅ CONCLUÍDA (2026-07-23 — SDD: `docs/session-26-fix-estado-contas/SDD.md`; bugs reportados pelo usuário 2026-07-22)
Solução: stores reativos com signals (`core/state/{account,card,category}.store.ts`) como fonte única — toda mutação dá `refresh()` e propaga pros consumidores. Mensagem específica no 404 (conta/cartão apagado noutra tela) + refresh; nota de UX no painel de Cartões ("débito não é cartão"). 255 testes web (97/85/93/96); verificado e2e no browser (apagar conta some de todos os selects sem reload, nas duas direções e cross-rota).

**Sintomas relatados:**
1. Ao criar cartão vinculado a uma conta (ex.: cartão "Nubank" → conta "Débito"), a UI retorna **"Erro ao salvar o cartão"**.
2. Os selects de conta **listam contas que o usuário já apagou** (ex.: "Débito" aparece no dropdown do form de cartão mesmo tendo sido excluída).

**Causa-raiz (confirmada por análise de código + inspeção do banco):** os dois sintomas têm **uma única origem**. Cada componente (`accounts-panel`, `cards-panel`, `transactions`, `invoices`, `importer`, `recurring`, `dashboard`, `investments`, `budgets`, `goals`) carrega a lista de contas **independentemente em `ngOnInit`** e nunca ressincroniza. Quando o usuário apaga uma conta no `accounts-panel` (que se recarrega sozinho), os dropdowns dos **outros** componentes continuam com a lista antiga em cache — mostrando contas que já não existem (sintoma 2). Ao escolher uma dessas contas fantasma e salvar o cartão, o backend responde **404 "Conta não encontrada"** (`CardService.ownedAccount`), que o front exibe como o genérico "Erro ao salvar o cartão" (sintoma 1). Confirmado no banco: a conta "Débito" realmente não existe mais (só restaram Itau e Nubank para o usuário) — o delete funcionou no backend; foi a UI que ficou dessincronizada.

**Tasks a refinar:**
1. **Estado de contas compartilhado e reativo:** criar um store baseado em signals (ex.: `core/state/AccountStore`) que todos os consumidores leem, atualizado após qualquer mutação (create/update/delete). Alternativa mais barata: re-buscar contas ao abrir cada form/modal (`openCreate`/`openEdit`) nos componentes afetados. Avaliar generalizar para **cartões e categorias** (mesmo padrão de bug latente).
2. **Mensagem de erro específica no 404:** quando a conta/cartão referenciado sumiu, dizer "A conta selecionada não existe mais — atualize a lista" e recarregar o select, em vez do genérico "Erro ao salvar".
3. **UX "débito não é cartão":** o usuário tentou registrar "Nubank débito" no painel de **Cartões** (que exige dia de fechamento/vencimento). No modelo da #25, débito não é entidade — basta a **conta** (`CHECKING`/`CASH`) e escolher no "Pagar com" (método deriva pra `DEBITO`/`DINHEIRO`). Melhorar o texto/orientação: deixar claro que **Cartões = só crédito**; débito e dinheiro = contas.
4. **Confirmar a mensagem do delete bloqueado por FK:** apagar conta com cartão vinculado → 409 (`cards.account_id NOT NULL`); hoje o front mostra "Erro ao excluir a conta". Garantir que a mensagem explique o motivo (cartão/transação vinculada).
5. Regressão de testes web (≥90/80/90/90) + verificação e2e: apagar conta e confirmar que some de **todos** os selects sem precisar recarregar a página.

Pré-req: nenhum (roda já; independe do deploy).

**#27 — Arquivar contas & cartões (soft) + excluir com cascata explícito** 📋 PLANEJADA (refinamento pós-uso, decidido com o usuário 2026-07-23)
Motivação: apagar cartão/conta com transações/faturas vinculadas hoje só bloqueia (409 por FK). Num app financeiro o histórico é o produto — cancelar um cartão não pode significar perder o histórico de gastos dele.
Decisão (usuário): **arquivar/inativar é a ação principal** (some dos seletores de novo lançamento, mas mantém transações/faturas antigas visíveis no histórico e nos filtros); **excluir de vez em cascata** vira opção secundária, com confirmação forte mostrando a contagem exata (cartão + N transações + M faturas). Vale **igual para contas**.
Tasks a refinar: (1) flag `active` em `Card` e `Account` (migration) + filtrar seletores de novo lançamento para só ativos (listagens/histórico/filtros mostram todos); (2) ação de arquivar/desarquivar (endpoint + UI); (3) excluir-com-cascata explícito (serviço transacional no backend + confirmação no front com a contagem); (4) testes (≥90% API, ≥90/80/90/90 web); (5) verificação e2e.
Pré-req: #25 (modelo conta/cartão).

**#28 — Saldo por conta (competência + regime de caixa)** 📋 PLANEJADA (refinamento pós-uso, decidido com o usuário 2026-07-23)
Motivação: hoje só existe um saldo total agregado, em regime de **competência** (a compra no crédito já vira gasto na data da compra; `INVOICE_PAYMENT` não conta de novo). Falta ver o saldo **por conta** e, principalmente, o **dinheiro real disponível** considerando quando as faturas são efetivamente pagas.
Decisão (usuário): **manter o saldo por competência como está** (padrão) e **adicionar** uma visão de **regime de caixa por conta** como recurso extra — o "dinheiro real" da conta agora e depois de pagar a fatura. No caixa: entradas e gastos em conta (débito/dinheiro) entram na data; a compra no crédito **não** baixa o caixa da conta até a fatura ser paga (aí o `INVOICE_PAYMENT` debita). Competência = "quanto gastei"; caixa = "quanto tenho de verdade na conta".
Tasks a refinar: (1) cálculo de saldo de caixa por conta (entradas + gastos débito/dinheiro + `INVOICE_PAYMENT`, na data de cada lançamento); (2) endpoint de saldo por conta (as duas visões); (3) UI — saldo por conta (Configurações e/ou Dashboard) + a visão de caixa "agora vs. depois de pagar a fatura em aberto"; (4) testes; (5) verificação e2e.
Pré-req: #25; roda melhor **depois da #27** (pra o saldo não somar contas/cartões arquivados de forma indevida — a confirmar no SDD).

**#29 — Senhas: recuperação por email + endurecimento (regras fortes · mostrar senha · lockout)** ✅ CONCLUÍDA (2026-07-25 — SDD: `docs/session-29-recuperacao-senha/SDD.md`)
Entregue: migration **V13** (`password_reset_tokens`, token opaco 256 bits guardado como hash SHA-256, single-use, expiry PT30M); `POST /auth/forgot-password` (**sempre 204**, anti-enumeração) e `POST /auth/reset-password` (valida/consome token, aplica senha forte, **revoga todos os refresh tokens**). Validador `@StrongPassword` compartilhado (≥12 chars maiúscula+minúscula+número+símbolo) em registro e reset — **login segue só `@NotBlank`** (senhas antigas continuam logando; sem migração de senha). Envio de email **pluggável** (`EmailSender`: `LoggingEmailSender` default loga o link em dev; `SmtpEmailSender`/AWS SES quando `app.mail.enabled=true`). Lockout **por conta** (`LoginAttemptLimiter`, 5 falhas → 5 min, reseta no sucesso) com `Retry-After` no 429. Front: telas `/esqueci-senha` e `/redefinir-senha`, toggle "mostrar senha" (olho) em login/cadastro/redefinir, **hint de política só em cadastro/reset (removido do login)**, feedback de lockout com minutos restantes. 293 testes API (JaCoCo ≥90%) + 280 testes web (97,2/86,6/93,8/97,1); verificado e2e (fluxo de reset via `EmailSender` de teste; telas no browser). **SES ainda a configurar pelo usuário** (`D:/Docs/Poupito/setup-ses-email.md`) — não bloqueia (email logado em dev).
Fluxo:
- `POST /auth/forgot-password {email}` → **sempre 200** (anti-enumeração — não revela se o email existe). Se existir, gera token de reset **opaco (256 bits), guardado como hash SHA-256** numa nova tabela `password_reset_tokens` (single-use, expiry curto ~30–60 min) e envia email com link `https://poupito.com/redefinir-senha?token=...`.
- `POST /auth/reset-password {token, newPassword}` → valida (não expirado, não usado), aplica a senha (BCrypt, mesma política ≥10 chars com letra+número), marca o token como usado e **revoga todos os refresh tokens** do usuário (força re-login em todo lugar — caso a conta estivesse comprometida).
- **Rate limiting** no forgot-password (reusar `LoginRateLimiter` por IP+email) contra email-bombing/enumeração.

Endurecimento de senha & login (pedido do usuário 2026-07-24):
- **Regras de senha mais fortes:** elevar a política atual (hoje ≥10 chars com letra+número) — proposta: **≥12 caracteres com maiúscula + minúscula + número + símbolo**, e opcionalmente bloquear senhas óbvias/comuns; aplicar no registro, na troca e no reset (validador compartilhado). Limites exatos a fechar no SDD.
- **Mostrar senha:** botão de "olho" (toggle mostrar/ocultar) nos campos de senha (login, registro, redefinir) pra o usuário conferir o que digitou.
- **Lockout de login:** **5 tentativas erradas por conta → bloqueio de 5 minutos.** Ajustar o `LoginRateLimiter` (hoje IP+email) pra contar falhas **por conta** e, após 5, responder 429 até a janela de 5 min expirar; no front, mostrar o aviso com o tempo restante.
- **Dica de política de senha só onde se DEFINE senha:** a mensagem "A senha deve ter pelo menos X caracteres, incluindo letra e número" aparece só nas telas de **cadastro e redefinição** — **nunca na tela de login** (lá o usuário só digita a senha existente; mostrar a regra é ruído/confuso). Hoje ela vaza no login; remover de lá.
Tasks a refinar: (1) migration `password_reset_tokens` + entidade/repository; (2) endpoints forgot/reset no `AuthController` + serviço (geração/validação/single-use/expiry/revogação); (3) **envio de email via AWS SES** (encaixa na infra AWS, barato ~US$0,10/1.000 emails) — requer verificar o domínio `poupito.com` (SPF/DKIM/DMARC) e **sair do sandbox do SES** (por padrão só manda pra emails verificados; pedir acesso de produção); template de email branded Poupito; (4) frontend — telas "Esqueci minha senha" (pede email) e "Redefinir senha" (token na URL + nova senha); (5) política de senha reforçada (validador compartilhado em registro/troca/reset); (6) toggle "mostrar senha" nos campos; (7) lockout de 5 tentativas/5 min no `LoginRateLimiter` + feedback no front; (8) remover a dica de política de senha da tela de login (manter só no cadastro/reset); (9) testes (API ≥90%, web ≥90/80/90/90) com envio de email **mockado**; (10) verificação e2e.
Pré-req: nenhum técnico pro core — dá pra desenvolver com o envio de email **mockado/logado** e plugar o SES depois (o SES é a única parte que depende de infra/DNS). Alinha com o modelo de auth da sessão #S (tokens opacos + hash SHA-256 + rate limiting).
Setup do SES documentado em `D:/Docs/Poupito/setup-ses-email.md` (fora do repo): verificação de domínio + DKIM na Cloudflare, SPF/DMARC, credenciais SMTP e saída do sandbox.

**#30 — Template de import da planilha (download do modelo compatível)** 📋 PLANEJADA (sessão **pequena e separada**, decidida com o usuário 2026-07-25) — **encaixa cedo** (independe do deploy e de #24/#22)
Motivação: o importador (#12) lê a Planilha_Gastos_2026 em **posições fixas de linha/coluna** nas 12 abas mensais (seções Fixos/Cartão/Gastos do Mês/Entradas — ver `docs/session-12-import-planilha/SDD.md`). Quem não tiver exatamente esse layout (ou for começar do zero) não consegue importar de forma fiel. Falta um **modelo oficial para baixar** que já venha no formato exato que o parser espera.
Decisão (usuário): oferecer um **template .xlsx para download** que espelhe 1:1 o que o import consome — mesmas abas, mesmos cabeçalhos, mesmas posições de seção — com uma **aba de instruções/legenda** (o que preencher em cada bloco, uma linha de exemplo realista) para o preenchimento sair alinhado ao parser. Escopo enxuto: é gerar/servir o arquivo + link na tela de Import, **não** mexe na regra de parsing.
Tasks a refinar: (1) gerar o template com Apache POI (mesmas abas/posições do `SpreadsheetParser`; aba "Como preencher" com legenda + linha de exemplo) — idealmente derivado das **mesmas constantes de posição** do parser pra não divergir com o tempo; (2) endpoint `GET /v1/import/template` (download .xlsx) + botão "Baixar modelo" na tela de Import; (3) testes (o template gerado passa pelo próprio `preview` sem linhas "não mapeadas" inesperadas — teste de ida e volta) + verificação e2e (baixar, preencher o exemplo, reimportar). Meta de cobertura padrão (API ≥90%, web ≥90/80/90/90).
Pré-req: #12 (parser) e #25 (mapeamento de cartão no import). Pode rodar assim que o usuário quiser — não depende da infra.

**#31 — Empréstimos a pessoas / "a receber" (dívidas de terceiros com você)** 📋 PLANEJADA (formaliza a antiga ideia "contas mãe / a receber", decidida com o usuário 2026-07-25) — sessão **maior** (entidade + fluxo novos)
Motivação: hoje não dá pra registrar dinheiro que **você emprestou** a alguém. Emprestar sai do seu bolso (afeta seu saldo real), mas não é "gasto" — é um **ativo a receber**; e falta um lugar pra consultar, por pessoa, **quanto te devem** e acompanhar o recebimento (inclusive parcelado) mês a mês.
Decisões a fechar com o usuário no SDD (proposta inicial):
- **Entidade `Person`/contato** (nome; escopada por usuário) pra agrupar os empréstimos e ver o total por pessoa.
- **Empréstimo (`loan`)**: valor, data, pessoa, conta de origem (de onde o dinheiro saiu), e opção de **parcelamento do recebimento** (N parcelas mensais esperadas, como se fosse o inverso de uma compra parcelada — cronograma do que a pessoa deve te pagar).
- **Efeito no saldo (a decidir — recomendação):** emprestar **reduz o caixa da conta de origem** (dinheiro saiu de verdade) mas **não** conta como *gasto* nas agregações de despesa/orçamento (não é consumo, é um ativo). Ao **receber de volta** (total ou parcela), entra na conta e **abate o saldo devedor** da pessoa — sem virar "receita/entrada" de renda (é devolução de capital, não ganho). Alinha com a lógica de "regime de caixa vs. competência" da #28 e com o tratamento de `INVOICE_PAYMENT` (movimenta caixa sem duplicar como gasto).
- **Visão de consulta:** tela/painel "A receber" com **total emprestado por pessoa**, quanto já foi devolvido, **saldo em aberto** e o **cronograma mensal** (o que cada pessoa deve te pagar em cada mês) — o "quanto a pessoa te deve todo mês" que o usuário pediu.
Tasks a refinar: (1) migration (`persons` + `loans` + `loan_repayments`, ou reuso de `transactions` com tipos novos `LOAN_OUT`/`LOAN_REPAYMENT` — decidir modelagem no SDD, pesando integração com o saldo de caixa da #28); (2) CRUD de pessoas e empréstimos + registrar recebimento (parcela/total); (3) cálculo de saldo devedor por pessoa + cronograma mensal + integração com saldo de caixa (afetar conta sem contar como gasto); (4) UI "A receber" (por pessoa, aberto vs. recebido, agenda do mês) + lançamento de empréstimo/recebimento; (5) testes (API ≥90%, web ≥90/80/90/90) + verificação e2e.
Pré-req: #25 (modelo conta/cartão); casa melhor **depois da #28** (reusa o conceito de saldo de caixa por conta pra o empréstimo debitar a conta sem virar gasto). A confirmar no SDD se antecipa ou espera a #28.

**#32 — Bugfix: gastos fixos no cartão de crédito** ✅ CONCLUÍDA (2026-07-25 — SDD: `docs/session-32-fixos-no-cartao/SDD.md`; bug reportado pelo usuário 2026-07-25)
Causa-raiz: limitação **estrutural** herdada da migration V5 (anterior à #25) — `recurring_transactions.account_id` era `NOT NULL` e não existia `card_id`; a materialização usava sempre `Transaction.forAccount`. Assinatura cobrada no cartão (Netflix/Spotify) não podia ser cadastrada como fixo. Fecha a pendência que a própria #25 deixou no código ("fixo em cartão = melhoria futura").
Entregue: migration **V14** (`account_id` nullable + `card_id` + CHECK `chk_recurring_account_xor_card` + índice; aditiva, **sem backfill** — fixos existentes já satisfazem o XOR); `RecurringRequest` com conta **XOR** cartão (400 "Informe conta OU cartão"; entrada em cartão → 400); materialização de fixo no cartão **vinculando à fatura do período** pelo `closing_day` (decisão do usuário) via `Transaction.materializedOnCard`, nascendo `paid=true` (a quitação é o `INVOICE_PAYMENT`, não checkbox por mês); fixo em conta **inalterado** (`paid=false` + checkbox); `method`/`cardName` nas respostas (overload `PaymentMethod.of(cardId, accountType)` reusado). Front: seletor **"Pagar com"** (contas + cartões, cartões escondidos em Entrada), `CardStore`, badge de método, "na fatura" no lugar do checkbox; `.method-badge` promovido pro `styles.css` global. 316 testes API (JaCoCo ≥90%) + 286 testes web (97,2/86,8/93,9/97,2); verificado e2e com API real (V14 aplicada, fixo no cartão → fatura de agosto com R$ 55,90) e no browser.
Pré-req: #25. Roda independente do resto da fila.

> Ordem atual (2026-07-25): ~~#29 (recuperação de senha)~~ ✅ → ~~#32 (fixos no cartão)~~ ✅ → **#24 (hardening)** → #27/#28/#30/#31 → #22 (Open Finance) por último. A #29 foi concluída (era um gap de acesso real em produção); a **próxima na fila é a #24**. A **#22 fica pro fim a pedido do usuário** (rodar todas as outras antes). As flexíveis: **#30** (template de import — pequena, encaixa cedo), **#27** (arquivar contas/cartões), **#28** (saldo por conta) e **#31** (empréstimos "a receber" — melhor após #28).

### Fase 5 — Futuro (sem sessão planejada ainda)

Ideias soltas: Multi-tenancy real, plano free/pago, cotações via brapi.dev, app mobile consumindo a mesma API. *(A feature "a receber/emprestado — contas mãe" virou a sessão #31, formalizada acima.)*

#### Escala / virar SaaS — tenancy, migrações e infra (nota de arquitetura, 2026-07-24)

**Tenancy:** o app já nasce no modelo **pool** (shared DB + shared schema, tudo escopado por `user_id`) — o certo pra B2C. Virar SaaS **não exige reprojetar o domínio**; as migrations continuam sendo **uma execução de Flyway por deploy**. Alternativas (schema-per-tenant / db-per-tenant) só compensam pra isolamento enterprise/regulado, com custo operacional bem maior.

**Migrações em escala (o que muda é o *processo*, não os arquivos):**
- **Expand → migrate → contract:** migration aditiva compatível com a versão *antiga* do app durante o rolling deploy; limpeza (`DROP`/`RENAME`) só num deploy posterior. Uma migration nunca pode quebrar o app que ainda está no ar.
- **Tirar o Flyway do boot da API** e rodar como **passo separado do pipeline** (job/init antes de subir as réplicas) — evita corrida entre N instâncias.
- **Evitar locks em tabela grande:** `CREATE INDEX CONCURRENTLY`, coluna nullable + backfill em lotes (nunca `UPDATE` gigante que trava a tabela).
- **Postgres gerenciado** (RDS/Aurora) com PITR/snapshots/réplicas — rede de segurança pra migrar em produção.
- **(dado financeiro) Postgres RLS** como defesa-em-profundidade: políticas no banco garantem isolamento por tenant mesmo se um bug na app esquecer o `where user_id`.

**Migração dos dados existentes (Lightsail → RDS) — NÃO confundir com migration do Flyway:** "migration do Flyway" versiona o *schema* (V1..V12); "migração de dados" move as *linhas* dos usuários de um Postgres pro outro. O RDS sobe vazio e você copia o banco pra dentro dele.
- **Método pro tamanho atual (`pg_dump` + `pg_restore`, janela de manutenção de poucos minutos):**
  1. RDS vazio, mesma versão major (16); liberar rede (ver pegadinha abaixo).
  2. Congelar escritas: `docker compose stop api`.
  3. Dump: `docker exec poupito-postgres-prod pg_dump -U poupito -d poupito -Fc > poupito.dump`
  4. Restore: `pg_restore -h <endpoint-rds> -U <master> -d poupito --no-owner --no-privileges poupito.dump`
  5. Apontar a API pro RDS (`DB_HOST`/`DB_URL`/`DB_USER`/`DB_PASSWORD` no `.env`), tirar o container `postgres` do compose, `docker compose up -d api`.
  6. Validar (login + dados); só então desativar o Postgres local. **Rollback:** apontar a API de volta enquanto o dado antigo existir.
- O dump leva a `flyway_schema_history` junto → o RDS já nasce no V12 e o **Flyway não re-roda nada** (schema + dados chegam prontos).
- **Zero-downtime (quando houver tráfego que não pode cair):** trocar o dump por replicação contínua — **AWS DMS** (carga inicial + CDC das mudanças, cutover instantâneo) ou logical replication nativa do Postgres.
- **Pegadinha de rede:** o Lightsail fica numa VPC separada da VPC padrão do RDS — por padrão não se enxergam. Habilitar **VPC peering** (Console → Account → Advanced) ou já rodar a compute (EC2/ECS) na mesma VPC do RDS.

**Infra nova necessária (além do deploy atual de 1 VM Lightsail):**
- **Postgres gerenciado** (sai da VM) — RDS/Aurora.
- **Compute dedicada** pra API (sai do burstable) — ECS Fargate ou EC2 não-burst; 2+ réplicas pra HA.
- **Load balancer (ALB)** na frente das réplicas + TLS.
- **Frontend estático** → S3 + CloudFront (em vez do Caddy servir o build).
- **Observabilidade** (CloudWatch — já é a #24) + **Secrets Manager**.
- CI/CD já publica imagens (GHCR); adicionar o passo de migração no pipeline.

**Custos aproximados (referência us-east-1, variam bastante):**

| Item | Hoje (uso pessoal) | SaaS mínimo com HA |
|---|---|---|
| Compute (API) | Lightsail US$5–10/mês (tudo junto) | Fargate/EC2 ~US$35–80/mês (1–2 réplicas) |
| Postgres | na mesma VM (grátis) | RDS `t4g.small` ~US$25–30 (single-AZ) / ~US$50–60 (Multi-AZ HA) + storage ~US$0,12/GB |
| Load balancer | Caddy (grátis) | ALB ~US$16–20/mês + tráfego |
| Frontend | Caddy serve o estático | S3+CloudFront ~US$1–5/mês |
| Observabilidade/Secrets | — | CloudWatch + Secrets Manager ~US$5–15/mês |
| **Total aprox.** | **~US$10/mês** | **~US$90–180/mês** (piso; escala com tenants/tráfego) |

O salto de "app pessoal" pra "SaaS com HA" é de ~US$10 pra ~US$100+/mês só de infra base — faz sentido **só quando houver tração/receita**. Enquanto é pessoal, a VM única no Lightsail (2GB) está perfeita. *Aurora Serverless v2* é uma alternativa elástica (paga pelo uso), mas tem piso ~US$40+/mês se ficar sempre ligado. Região `sa-east-1` (São Paulo) costuma ser ~15–30% mais cara que `us-east-1`.

## 6. Grafo de dependências (resumo)

```
#1 → #2 → #3 → #4
      #2 ─────────→ #13 → #14 → #15
      #3 → #5 → #6 → #7 ──┐        #13 → #16
                 #6 → #8   ├→ #11 → #12
                 #6 → #9   │   #11 → #17, #20
                 #6 → #10 ─┘   #9 → #18   #7 → #19
                               #4 + #12 → #21
```

Sessões #13–#16 (investimentos) podem rodar em paralelo com a Fase 1 a partir da sessão #2.

## 7. Decisões em aberto (herdadas da spec)

- Categoria "Itaú" da planilha mistura conta com categoria → no import (#12), mapear cartão como conta e pedir categoria real.
- ~~"Contas mãe" (empréstimos a familiares) → avaliar feature "a receber" na Fase 3/4.~~ **Resolvido:** virou a sessão **#31 — Empréstimos a pessoas / "a receber"** (a refinar no SDD; decisões de modelagem e efeito no saldo já esboçadas lá).
- Keycloak em vez de JWT próprio se SSO for necessário no futuro (migração possível sem quebrar a API).

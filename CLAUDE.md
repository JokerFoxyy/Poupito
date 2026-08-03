# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## O que é este projeto

Poupito: app de gestão financeira pessoal que substitui a Planilha_Gastos_2026 — transações, contas/cartões com fatura, fixos recorrentes, orçamentos, dashboard, investimentos e metas. Uso pessoal com potencial de virar SaaS.

Antes de implementar qualquer coisa, leia:

- `docs/PLANO-SDD.md` — documento-mestre: stack, sessões planejadas (#1–#21), grafo de dependências e regras de negócio. **É a fonte de verdade do roadmap.**
- `../spec-app-financeiro.md` (fora do repo, na pasta pai) — spec completa com modelo de dados e fases.
- `../prototipo-dashboard.html` — protótipo visual de referência (tema dark, layout, gráficos).

## Fluxo de trabalho (SDD)

O desenvolvimento é organizado em sessões numeradas (mesmo padrão do projeto ContratoIA). Ao iniciar a sessão #NN:

1. Criar `docs/session-NN-nome/SDD.md` detalhando as tasks antes de codar.
2. Implementar task a task, com verificação end-to-end como última task.
3. Atualizar o status da sessão em `docs/PLANO-SDD.md` ao concluir.

## Arquitetura (planejada)

Monorepo:

```
api/    Java 21 + Spring Boot 3.5 + Maven — pacotes POR FEATURE (auth/, account/,
        category/, transaction/, invoice/, recurring/, budget/, goal/, investment/,
        dashboard/, importer/, common/), cada um com controller, service, repository e DTOs
web/    Angular 20 (standalone components, signals) + Tailwind + ng2-charts (Chart.js)
infra/  docker-compose.yml (Postgres 16, api, caddy) e scripts
docs/   PLANO-SDD.md + SDDs por sessão
```

- Banco: PostgreSQL com Flyway (migrations V1–V7 mapeadas no plano).
- Auth: Spring Security + JWT próprio (não Keycloak).
- Frontend consome só a API REST (OpenAPI/springdoc) — mobile futuro reusa o contrato.

## Regras não negociáveis

- **Dinheiro:** `BigDecimal` no Java, `NUMERIC(14,2)` no Postgres. Nunca float/double.
- **Datas:** `LocalDate` (sem timezone para datas de transação).
- **Saldos são calculados, nunca armazenados:** `saldo(mês) = saldo(mês−1) + entradas − gastos`.
- **Toda transação tem conta obrigatória**; lançamento em cartão de crédito vincula-se à fatura (`card_invoice`) do período conforme o `closing_day` do cartão.
- **Fechamento de fatura:** diferença entre total lançado e valor declarado vira transação `INVOICE_ADJUSTMENT` automática, reduzida conforme o usuário detalha os gastos reais.
- UI segue a identidade "Poupito" (sessão #23): tema **claro por padrão + toggle dark/light** via variáveis CSS em `styles.css` (`:root` claro, `:root[data-theme="dark"]` escuro). Paleta: navy `#0F172A`/`#1E293B` (fundo dark + sidebar), verde esmeralda `--accent #059669`/`#10B981` (ação/CTA/positivo), neutras branco/cinza-claro (cards no light). Sidebar é navy nos dois temas (variáveis `--brand-navy*`/`--sidebar-*`, fixas). Cores novas sempre via variável — nunca hex fixo do tema antigo.
- Textos de UI em pt-BR.

## Comandos

```bash
# Banco (Postgres 16 na porta 5433; Docker Desktop precisa estar rodando)
docker compose -f infra/docker-compose.yml up -d

# API — build + testes + cobertura (o que o CI roda)
cd api && ./mvnw verify

# Um teste só
./mvnw test -Dtest=AuthServiceTest
./mvnw test -Dtest=AuthServiceTest#shouldThrowEmailAlreadyUsed_whenEmailExists

# Rodar a API
./mvnw spring-boot:run

# Web — dev server em http://localhost:4200 (proxy /api → localhost:8080)
cd web && npm start

# Web — testes com cobertura (o que o CI roda; thresholds quebram o build)
npm run test:ci

# Um spec só
npm test -- --include='**/auth.service.spec.ts' --watch=false --browsers=ChromeHeadless

# Web — build de produção
npm run build:prod
```

**Gotcha de JDK:** o `java` default da máquina é 19; o projeto exige 21. Antes de qualquer comando Maven: `$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"` (PowerShell). Os testes de integração usam Testcontainers — Docker Desktop precisa estar de pé, senão o contexto falha com "find a Docker environment failed".

**URLs (context-path `/api`):** health em `http://localhost:8080/api/actuator/health`, Swagger em `http://localhost:8080/api/swagger-ui.html`. Endpoints: `/api/v1/...` (controllers usam `@RequestMapping("/v1/...")`; o prefixo vem de `server.servlet.context-path`).

## Qualidade — regras obrigatórias

- **Cobertura mínima de 90% de linha em testes unitários/integração — enforçada pelo JaCoCo na fase `verify`** (regra no `api/pom.xml`); build quebra abaixo disso. No frontend (a partir da sessão #3), thresholds no Karma: 90% statements/functions/lines, 80% branches. Toda feature nova entra com testes na mesma sessão — nunca como follow-up.
- Convenção de nomes de teste: `should<ComportamentoEsperado>_when<Condicao>` (ex.: `shouldReturn401_whenPasswordIsWrong`).
- Unit tests com Mockito para services; integração com `@SpringBootTest` + Testcontainers (Postgres real, nunca H2).
- Segurança: deny-by-default no `SecurityConfig` — todo endpoint novo é autenticado a menos que explicitamente liberado; `@Valid` em todo request DTO; segredos só via env var.
- CLAUDE.md e README devem ser atualizados no mesmo commit que muda comandos, endpoints ou arquitetura.

## Frontend (sessão #3)

Angular 20 standalone + signals + `inject()`; Tailwind v4 via `@tailwindcss/postcss` (`.postcssrc.json`); tema em `src/styles.css` (variáveis CSS `:root`/`[data-theme="dark"]` + classes `.panel`, `.card`, `.btn`, `.tag`...). `ThemeService` (`core/theme/`) controla claro/escuro (signal + localStorage `poupito.theme`; script inline no `index.html` aplica antes do boot pra evitar flash); gráficos Chart.js leem as cores do tema via `core/theme/chart-theme.ts`. Estrutura: `core/auth` (AuthService com signals, interceptor funcional, `authGuard`), `core/layout/shell` (sidebar), `core/state/` (stores reativos de dados de referência — ver abaixo), `features/<nome>` (uma pasta por página, lazy via `loadComponent`), `shared/`. **Dados de referência compartilhados entre telas (contas, cartões, categorias) vivem em stores signal-based (`core/state/{account,card,category}.store.ts`, sessão #26), nunca carregados isoladamente no `ngOnInit` de cada componente:** cada store expõe um signal readonly como fonte única, `ensureLoaded()` (idempotente) no `ngOnInit` dos consumidores, e `create/update/delete` que delegam ao service e dão `refresh()` no sucesso — assim uma mutação em Configurações propaga na hora pros dropdowns de Transações/Faturas/Import/Fixos/Orçamentos sem reload (antes ficavam com listas fantasma e davam 404 ao salvar). Componente que ainda precise dos services diretos deve preferir o store. API sempre por caminho relativo `/api/...` — em dev o `proxy.conf.json` encaminha para `localhost:8080` (não usar URL absoluta nem CORS). Token JWT em `localStorage` (`poupito.token`).

**Gotcha do Karma:** o `karma.conf.js` referenciado pelo builder `@angular/build:karma` **substitui** a config default em vez de mesclar — se recriar, use `ng generate config karma` e edite (senão os testes quebram com "describe is not defined"). Os thresholds de cobertura (90/80/90/90) vivem no `coverageReporter.check` desse arquivo.

## Landing pública + FAQ (sessão #40)

- **Roteamento** (`app.routes.ts`): `Landing` (`features/landing/`, rota `/`) entra como a
  **primeira** rota do array, com `path: '', pathMatch: 'full'` — essencial, porque path
  vazio **sem** ele faz *prefix match* (é assim que o `Shell` sempre funcionou, delegando
  pros filhos `dashboard`/`transacoes`/etc.); com `full`, Landing só intercepta a barra `/`
  exata. Isso permite Landing e `Shell` coexistirem (ambos usam `path: ''`) **sem mudar
  nenhum path autenticado existente**.
- **`redirectIfAuthenticatedGuard`** (`core/auth/`, espelha o `authGuard`): na rota da
  Landing, se já autenticado manda pro `/dashboard` direto.
- **`Faq`** (`features/faq/`, rota `/faq`, pública, sem guard): `<details>`/`<summary>`
  nativos (acordeão sem JS de estado).
- Botão "Criar conta grátis" (Landing/FAQ) leva direto pro modo cadastro via
  `routerLink="/login" [queryParams]="{ mode: 'register' }"`; `Login` lê o query param no
  construtor e chama `setMode('register')` antes do primeiro render.
- **Diferenciação visual login vs. cadastro** (`login.css`): modo cadastro usa `--purple`
  (token já existente na paleta, não é cor nova) como accent local via custom property
  `--mode-accent`/`--mode-accent-strong` escopada em `.login-page.register-mode` — borda
  superior do card, logo, botão de submit e foco dos campos ficam roxos em vez de verdes,
  sinalizando "tela diferente" sem duplicar CSS. Logo do Poupito na tela de login virou
  link (`routerLink="/"`) de volta pra landing.
- **Ajustes WebView mobile** (`index.html`, `styles.css`, `landing.css`): viewport ganhou
  `viewport-fit=cover`; `<html>` também herda `--bg` do tema (evita flash no overscroll
  elástico); `-webkit-tap-highlight-color: transparent` global (feedback de toque já vem
  de `:active`/`:hover`/`:focus-visible`); header/footer da landing usam
  `env(safe-area-inset-*)`; grid de features blindado contra overflow em telas <240px
  (`minmax(min(240px, 100%), 1fr)`).
- Testes: 9 novos specs (Landing, Faq, guard), 304/304 Karma. Racional completo da decisão
  de roteamento em `docs/PLANO-HISTORICO.md` (sessão #40).

## Refino visual: tokens, mobile e botões (sessão #33)

- **Tokens de design** em `styles.css` (`:root`): `--text-xs`...`--text-2xl` (escala tipográfica), `--radius-sm`/`--radius-md`/`--radius-pill`, `--space-1`...`--space-6` (ritmo de 4px) e `--transition-fast` (150ms). **Todo valor novo usa token, nunca número solto.**
- **Ícones do shell**: `☰`/`☀️`/`🌙` viraram SVG inline (`currentColor`, mesmo padrão do olho de senha da #29) em `core/layout/shell.html`.
- **`.row-actions`** (`styles.css`) não usa `display: flex` direto no `<td>` (sobrescreveria o `display: table-cell` nativo e quebraria `vertical-align`) — o flex fica numa `<div>` interna.
- **Mobile: tabelas viram cards empilhados** abaixo de 640px (`@media` em `styles.css`, decisão do usuário sobre scroll horizontal + sticky). **Todo `<td>` novo em tabela precisa de `data-label="Nome da coluna"`** (vira rótulo via `::before`/`content: attr(data-label)`); `<tr>` de cabeçalho (`tr:has(th)`) some no modo card. Aplicado em `transactions`, `recurring`, `budgets`, `investments` (2 tabelas), `accounts-panel`, `cards-panel`, `categories-panel`. `goals` não precisou (já é card, não `<table>`).
- **Botões/links**: `transition` em background/transform/box-shadow/border-color (150ms), `:active` com `scale(.97)`, sombra no hover do `.btn` primário, `:focus-visible` desenhado. Classe `.btn.loading` (spinner via `::after`) pronta pra usar — **wiring `[class.loading]="submitting()"` por componente ainda não feito**. Tudo respeita `prefers-reduced-motion: reduce`.
- **Animações adicionais**: elevação no hover de `.card`/`.panel`; modais com fade+scale (`@keyframes` global); barra de progresso anima largura; `.nav-item` do menu com transição no hover. Emoji 🎯 das Metas → SVG.
- **Fonte Manrope** (troca de Inter, self-hosted, não Google Fonts CDN): arquivo **variável** (`web/public/fonts/manrope-variable.woff2`, cobre pesos 400–800, subset `latin` cobre acentuação pt-BR) via `@font-face` em `styles.css`. **Gotcha do path**: esbuild resolve `url()` relativo em CSS como módulo a bundlar — arquivo em `public/` só resolve com caminho **absoluto** (`/fonts/manrope-variable.woff2`); relativo dá erro "Could not resolve" no build. `.card .value`/`.amount-col` ganham `font-variant-numeric: tabular-nums`.
- Racional completo (auditoria de CSS, bugs de alinhamento/mobile, decisões do usuário) em `docs/PLANO-HISTORICO.md`, sessão #33.

## Domínio (sessões #5–#6) — parcialmente superado pela #25

> ⚠️ A modelagem abaixo é a original. A **sessão #25** separou conta e cartão: contas passaram a ser só `CHECKING`/`CASH` (sem `closingDay`/`dueDay`), cartão virou entidade própria, e transação tem conta **XOR** cartão. Leia primeiro a seção **"Remodelagem Contas & Cartões (sessão #25)"** abaixo — ela é a fonte de verdade atual do domínio de contas/cartões/faturas/transações.


- `/api/v1/accounts` e `/api/v1/categories`: CRUDs escopados por usuário (recurso alheio → 404); cartão exige `closingDay`/`dueDay`; categoria única por (user, nome, kind) → 409.
- **Nome único por usuário (sessão #34):** `accounts` e `cards` também rejeitam nome repetido → **409** (`DuplicateResourceException`), **case-insensitive** — índices `uq_accounts_user_lower_name`/`uq_cards_user_lower_name` em `(user_id, lower(name))` (migration **V15**) + checagem `existsByUserIdAndNameIgnoreCase` no service (no `update`, só se o nome mudou, senão colidiria consigo mesmo). Motivo: conta/cartão são rótulos de escolha nos seletores ("Pagar com", filtros) e o importador casa **por nome** — dois "Nubank" tornam tudo ambíguo. A V15 **deduplica o que já existe antes** de criar os índices (renomeia para "Nome (2)", mantendo o mais antigo), senão a migration falharia em bases com duplicatas.
- `/api/v1/transactions`: `GET ?month=YYYY-MM` (obrigatório) `&accountId&categoryId&type&page&size` → `PageResponse`; POST/PUT/DELETE. `amount` sempre positivo (sinal vem do `type`); categoria deve ter kind coerente com o type; `INVOICE_ADJUSTMENT` é reservado (400 na API).
- **Vínculo de fatura:** compra em cartão no dia do fechamento ou depois → fatura do mês seguinte; `CardInvoiceService.getOrCreateInvoiceFor` cria a fatura OPEN do período na primeira compra (única por conta+mês; dias clampados ao fim do mês; vencimento ≤ fechamento cai no mês seguinte). Update de transação re-vincula; sair do cartão zera `invoice_id`.
- Delete de conta/categoria com transações → 409 (`DataIntegrityViolationException` no handler global).
- Filtros dinâmicos usam **JPA Specification** — não usar `(:param is null or ...)` em JPQL com UUID (quebra no Postgres/Hibernate 6).

## Orçamentos (sessão #10)

- `/api/v1/budgets`: `GET ?month=YYYY-MM` retorna o relatório orçado × realizado (só categorias com orçamento definido no mês); `POST` cria (categoria + mês + valor); `PUT /{id}` edita só o valor (categoria/mês são imutáveis — para trocar, excluir e recriar); `DELETE /{id}`.
- Só categoria `kind=EXPENSE` pode ter orçamento (400 caso contrário); único orçamento por (user, categoria, mês) → 409.
- Realizado = soma de `transactions` tipo `EXPENSE` da categoria no mês (`TransactionRepository.sumExpensesByCategory`); `percentage`/`over` calculados no `BudgetReportResponse.from`. Tela usa `.bar-bg`/`.bar`/`.bar.over` (já existentes em `styles.css`) para a barra de progresso.
- Migration **V6** (V5 já estava reservada pela sessão #8 — fixos recorrentes).

## Dashboard (sessão #11)

- `/api/v1/dashboard/summary?month=YYYY-MM`: entradas/gastos do mês, saldo do mês (`entradas − gastos`), saldo acumulado (soma de todas as transações desde 1970-01-01 até o fim do mês — saldo nunca é armazenado, sempre calculado), gasto por categoria (`TransactionRepository.sumExpensesByCategoryForMonth`, sem filtro de categorias — diferente da query usada por Orçamentos) e o mesmo `budgetReport` da sessão #10 (reaproveita `BudgetService.report` diretamente).
- `/api/v1/dashboard/annual?month=YYYY-MM`: série de janeiro até o mês selecionado (não 12 meses fixos), `{month, income, expense}` por mês.
- Frontend usa **Chart.js puro** (`chart.js/auto`, sem `ng2-charts` — a versão atual exige `@angular/cdk` como peer dependency, não usado no projeto).
- **Gotcha de timing**: o `<canvas>` do gráfico só existe no DOM depois que o Angular processa o `@if` que envolve os dados carregados via HTTP — usar `afterNextRender(callback, {injector})` para criar o `Chart` (não `setTimeout(0)`: o `ViewChild` ainda é `undefined` nesse ponto).
- **Gotcha de animação**: sempre passar `animation: false` nas opções do Chart.js — a animação inicial depende de `requestAnimationFrame`, que não progride de forma confiável em navegadores automatizados (o gráfico ficava com o canvas em branco/0 pixels desenhados até essa opção ser adicionada). Usar também `maintainAspectRatio: false` (senão o Chart.js força proporção quadrada/2:1 e estoura a altura fixa do `.chart-box`).

## Import da planilha (sessão #12) — Fase 1 (MVP) completa

- `/api/v1/import/preview?year=` (multipart `file`) e `/api/v1/import/commit?year=` (multipart `file` + parte JSON `mapping`) — fluxo em duas chamadas, sem estado no servidor (reenvia o arquivo nas duas).
- `SpreadsheetParser` lê as 12 abas mensais (Janeiro..Dezembro) da Planilha_Gastos_2026 em **posições fixas de linha/coluna** (confirmado com o arquivo real do usuário: mesmo template nas 12 abas — não precisa de busca dinâmica de header). Ver `docs/session-12-import-planilha/SDD.md` para o mapeamento completo de cada seção (Fixos/Cartão/Gastos do Mês/Entradas).
- Linhas "Diferença de totais" (Gastos do Mês) são puladas — esse conceito já é automatizado pelo `INVOICE_ADJUSTMENT` (sessão #9); importar duplicaria o ajuste. **Gotcha**: comparar com `"totai"`, não `"total"` — "totais" não contém "total" como substring (falta o "l").
- Data da transação = mês/ano da própria aba (não a coluna "Data" da planilha, que é inconsistente — vem em branco em Fixos e às vezes carrega a data de compra original de anos anteriores em parcelas de Cartão).
- Conta/categoria sem correspondência exata (case-insensitive) por nome viram "não mapeadas" no preview; usuário escolhe usar uma existente ou criar nova no commit. Entradas não tem coluna de conta na planilha — usa um nome-placeholder (`SpreadsheetParser.ENTRADAS_ACCOUNT_PLACEHOLDER`) que passa pelo mesmo fluxo de mapeamento.
- Idempotência: antes de inserir, checa `(userId, accountId, description, amount, date, type)` idêntico já existente — permite rodar o import de novo sem duplicar (também deduplica linhas idênticas dentro do próprio arquivo).
- Upload de arquivo real não é automatizável no browser de verificação (abre diálogo nativo do SO) — a verificação end-to-end com a planilha real do usuário foi feita via chamada HTTP direta (`curl` multipart) contra a API local.

## Investimentos (sessão #13) — backend

- `/v1/investments` (CRUD: name/institution/class na criação; update só edita `name`/`institution` — `class` é imutável, trocar quebraria a série histórica de rentabilidade por classe); `DELETE` apaga em cascata as `investment_entries` (`ON DELETE CASCADE` no banco — diferente de conta/categoria, nada mais referencia `investments`).
- `/v1/investments/{id}/entries`: `GET` (lista ordenada por data), `POST` (`type`: `APORTE`/`RESGATE`/`ATUALIZACAO_SALDO`; `balanceAfter` obrigatório só em `ATUALIZACAO_SALDO` — 400 se faltar), `DELETE /{entryId}`.
- `/v1/investments/report`: saldo atual + rentabilidade do último período por investimento e agregado por classe (`InvestmentReturnCalculator`).
- **Cálculo (TWR simplificado)**: percorre as entries em ordem de data; `APORTE`/`RESGATE` somam/subtraem do saldo corrente; `ATUALIZACAO_SALDO` **substitui** o saldo pelo `balanceAfter` informado. Entre duas atualizações consecutivas: `fluxoLíquido = Σaportes − Σresgates` no período; `rendimento = saldoAtual − saldoAnterior − fluxoLíquido`; `percentual = rendimento / saldoAnterior × 100` (nulo com menos de duas atualizações). Agregação por classe roda o mesmo cálculo tratando todas as entries dos investimentos da classe como uma única linha do tempo.
- Migration **V7**. LGPD (`UserDataService`): investimentos entram na exportação e na exclusão de conta (antes do `refreshTokenRepository`).

## Investimentos (sessão #15) — frontend

- `web/src/app/features/investments/`: cards de patrimônio (total + 3 classes fixas, mesmo sem investimento cadastrado), lista de investimentos com rentabilidade do último período, gráfico "Evolução do patrimônio × CDI", tabela de últimos lançamentos e CRUD (dois modais: investimento e lançamento — mesmo padrão dos orçamentos, sessão #10).
- **Gráfico calculado no cliente**: a API não expõe uma série histórica pronta do patrimônio total (só saldo atual + rentabilidade do último período). `investments.utils.ts` reimplementa a máquina de estados do `InvestmentReturnCalculator` (`buildBalanceTimeline`) a partir de `GET /v1/investments/{id}/entries` de cada investimento, soma as linhas do tempo (`sumTimelines`, forward-fill) e alinha com o CDI acumulado (`alignSeries`, também forward-fill) num eixo `category` só de strings (sem adapter de tempo do Chart.js).
- Dois eixos Y no Chart.js: patrimônio em R$ (esquerda) × CDI acumulado em % (direita) — grandezas diferentes, não dá pra normalizar num único eixo sem base arbitrária.
- Metas de patrimônio (painel do protótipo `prototipo-dashboard.html`) ficam fora de escopo — é a sessão #16.

## Integração CDI (sessão #14) — backend

- `GET /v1/investments/cdi?from=YYYY-MM-DD&to=YYYY-MM-DD`: série do CDI acumulado (composto) dia a dia, para o frontend (sessão #15) sobrepor à curva de patrimônio. `from`/`to` obrigatórios, sem default.
- `BacenCdiClient` (`RestClient`) busca a série 12 do SGS/Bacen (`api.bcb.gov.br/dados/serie/bcdata.sgs.12/dados`, sem chave); só retorna dias úteis. Falha (timeout/5xx/parse) vira `ExternalServiceException` → **502**.
- **Cache local** (`cdi_rates`, migration **V8**): se já existe uma linha com `date = to` (data final, truncada para no máximo ontem — Bacen não tem o dia corrente), assume o intervalo inteiro em cache e não rechama o Bacen; senão busca tudo de novo e grava via `saveAll` (upsert natural pela PK `date`, sem SQL upsert manual).
- Cálculo do acumulado é **composto**: `Π(1 + taxa_i/100) − 1`, não soma simples — cada ponto da série já traz o percentual acumulado até aquele dia.
- Testes mockam a chamada HTTP: `MockRestServiceServer` no client, `@MockitoBean` de `BacenCdiClient` no teste de integração (sem chamada de rede real no CI).

## Metas Financeiras (sessão #16) — Fase 2 completa

- `/v1/goals` (CRUD: `name`/`targetAmount`/`targetDate` todos editáveis — diferente de Investimento, aqui não há série histórica que uma edição possa corromper); `DELETE` cascata (`ON DELETE CASCADE` em `goal_contributions.goal_id`).
- `/v1/goals/{id}/contributions`: `GET`/`POST` (`month` + `amount`, várias contribuições no mesmo mês são permitidas e somadas — sem unique constraint, diferente de orçamento)/`DELETE`.
- `GET /v1/goals` já retorna o relatório embutido (`accumulated`, `progressPercentage`, `requiredMonthlyContribution`) — não há endpoint de relatório separado, já que não existe filtro por mês como em Orçamentos.
- **Cálculo do aporte necessário** (`RequiredContributionCalculator`, regra 5 do plano): `restante = max(alvo − acumulado, 0)`; se `restante = 0` → `0`; se o mês atual já alcançou/passou o mês alvo → `restante` inteiro (tudo devido agora); senão `restante / mesesRestantes`, **arredondado para cima** (evita subestimar o aporte).
- Migration **V9** (o `PLANO-SDD.md` original citava V6, já usada pela sessão #10 — Orçamentos).
- Frontend (`web/src/app/features/goals/`): barra de progresso por meta + "Aporte necessário: R$ X/mês até mmm/aaaa" (protótipo), modais de meta e de aporte (mesmo padrão dos orçamentos/investimentos). Ícone fixo 🎯 por meta (a planilha/protótipo usa emoji livre por meta, não persistido no schema).
- LGPD (`UserDataService`): metas entram na exportação e na exclusão de conta (depois de investimentos, antes do `refreshTokenRepository`).

## Alertas de Orçamento + Busca e Tags (sessão #17) — Fase 3

- **Tags livres**: `Transaction.tags` é um `@ElementCollection<Set<String>>` (tabela `transaction_tags`, migration **V10**) — não uma coluna `text[]` nativa — porque `CriteriaBuilder.isMember` só compõe com a `Specification` dinâmica existente (`TransactionSpecifications`) quando é uma collection JPA de verdade. Tags são normalizadas (trim + lowercase) e deduplicadas (`Set`) no `TransactionService` antes de salvar.
- `GET /v1/transactions` ganhou `q` (busca `LIKE` case-insensitive na descrição) e `tag` (uma tag por vez, via `cb.isMember`) — combináveis com os filtros já existentes (mês/conta/categoria/tipo).
- `GET /v1/budgets/alerts?month=` (default mês atual): subconjunto de `BudgetService.report()` filtrado a `over=true` — reaproveita o cálculo da sessão #10 em vez de duplicar regra.
- Frontend: campo de busca + filtro de tag na tela de Transações (debounce de 300ms via `onSearchInput()`, evita 1 request por tecla); campo de tags (string separada por vírgula, convertida em array no submit) no formulário de lançamento; chips `#tag` na listagem. `Shell` busca `/v1/budgets/alerts` uma vez no load e mostra um badge numérico vermelho no item "Orçamentos" do menu — não é reativo a mudanças feitas em outras páginas na mesma sessão de navegação (só atualiza em um novo load do shell).
- Removidos nesta sessão (código morto após as sessões #15/#16 substituírem os últimos placeholders): `web/src/app/features/pages.spec.ts` e `web/src/app/shared/page-placeholder.ts`.

## Parcelamentos (sessão #18)

- `Transaction` ganha `installmentGroupId`/`installmentNumber`/`installmentCount` (migration **V11**, colunas `INTEGER` — atenção: `SMALLINT` quebra a validação de schema do Hibernate contra um campo `Integer`, já apanhamos com isso nesta sessão). Fábrica `Transaction.installment(...)`.
- **`amount` no parcelamento é o valor de cada parcela, não o total da compra** — mesma convenção da planilha original (sessão #12: "Valor já é a parcela do mês"). `POST /v1/transactions` com `installments > 1` (só `type=EXPENSE`, 400 caso contrário) cria N transações em `date`, `date+1 mês`, ... `date+(N-1) meses` (`LocalDate.plusMonths` já clampa dia inexistente sozinho); cada uma passa pela regra normal de vínculo de fatura (sessão #9) — sem lógica especial de parcelamento na fatura. A resposta do POST é sempre a 1ª parcela.
- `DELETE /v1/transactions/{id}?scope=group` apaga essa parcela e todas as **futuras** do mesmo grupo (`date >=` a da parcela clicada) — preserva as já lançadas/passadas. Sem o parâmetro, comportamento inalterado (exclui só a transação).
- Edição de uma parcela (`PUT`) edita só aquela transação — não recalcula nem resincroniza as demais do grupo.
- Frontend: campo "Parcelas" no formulário (só aparece criando + tipo Gasto), preview "Nx de R$ X"; badge "n/N" na listagem; ação extra "Excluir esta e as próximas" quando a transação pertence a um grupo.

## Export CSV/xlsx (sessão #19)

- `GET /v1/transactions/export?month=&accountId=&categoryId=&type=&q=&tag=&format=csv|xlsx` (default `csv`) — reaproveita os mesmos parâmetros de filtro de `GET /v1/transactions` (sessões #6/#17), sem paginação (exporta tudo que casa com o filtro no mês). `TransactionExportService` monta as linhas; CSV é escrito manualmente (RFC 4180, aspas só quando necessário); xlsx via Apache POI (`XSSFWorkbook`, já dependência do projeto desde a sessão #12).
- Colunas: Data, Descrição, Conta, Categoria, Tipo, Valor, Tags, Parcela, Fatura — espelham o que já aparece na tela de Transações. No xlsx, "Valor" é célula `NUMERIC` com format `#,##0.00` (nunca texto formatado), pra dar pra somar/filtrar no Excel depois.
- Nome do arquivo: `transacoes-{mês}.csv`/`.xlsx`. Resposta é `ResponseEntity<byte[]>` com `Content-Disposition: attachment`.
- Frontend: botões "Exportar CSV"/"Exportar xlsx" na tela de Transações usam os filtros/mês atuais da tela (`TransactionService.export()`, `responseType: 'blob'`); download disparado via `URL.createObjectURL` + `<a download>` temporário.
- Colunas de export após a #25: "Conta/Cartão" + "Método" no lugar da coluna única "Conta" (ver seção da #25).

## Remodelagem Contas & Cartões (sessão #25) — fonte de verdade do domínio de pagamento

Separa "onde o dinheiro vive" (conta) de "instrumento de pagamento" (cartão). Migration **V12** recomeça os dados transacionais (app pré-produção).

- **Contas** (`/v1/accounts`): tipo só `CHECKING` ou `CASH` (removido `CREDIT_CARD`); sem mais `closingDay`/`dueDay`. Payload = `{name, type}`.
- **Cartões** (`/v1/cards`, entidade `Card` própria): `{name, accountId, closingDay, dueDay}` — **sempre vinculado a uma conta** (é dela que a fatura é paga); `closingDay`/`dueDay` obrigatórios (400 se faltarem). CRUD escopado por usuário; `CardResponse` traz `accountName`.
- **Transação = conta XOR cartão**: `account_id` nullable + `card_id`, com CHECK `chk_transactions_account_xor_card` (exatamente um preenchido). `TransactionRequest` = `{description, amount, date, type, accountId?, cardId?, categoryId, tags, installments}` — informar conta **ou** cartão (400 "Informe conta OU cartão"). Entrada (`INCOME`) em cartão é rejeitada; parcelamento (`installments>1`) **exige cartão** ("Parcelamento só é permitido no cartão de crédito").
- **Método de pagamento é derivado, nunca digitado** (`PaymentMethod.of(tx, accountType)`): `card_id != null` → `CREDITO`; conta `CASH` → `DINHEIRO`; senão `DEBITO`. `TransactionResponse` expõe `cardId`, `cardName`, `method`.
- **Fatura por cartão**: `card_invoices` agora referencia `card_id` (não mais `account_id`), única por (cartão, mês). `CardInvoiceService.getOrCreateInvoiceFor(Card, data)`. Compra no crédito no fechamento ou depois → fatura do mês seguinte (regra da #9 preservada).
- **Pagar fatura** (`POST /v1/invoices/{id}/pay`, body `{accountId}`): cria uma transação `INVOICE_PAYMENT` debitando a **conta** informada (a compra no crédito já foi contada na competência — por isso `INVOICE_PAYMENT` é **excluído das agregações de gasto**, pra não contar duas vezes). `INVOICE_ADJUSTMENT` e `INVOICE_PAYMENT` são tipos reservados (400 se vierem no request). `InvoiceSummaryResponse` traz `cardId`/`cardName`.
- **Importer**: um nome de "conta" da planilha pode resolver para conta existente, cartão existente, criar conta nova (`createType`) ou **criar cartão** (`createCard{accountId, closingDay, dueDay}`) — ver `AccountMappingChoice`. Isso corrige o bug da aba **Faturas zerada** (o import antigo pulava o vínculo de fatura; agora `ImportService.saveCardRow` liga a compra à fatura do cartão).
- **Fixos** podem ser em conta **ou** em cartão desde a **sessão #32** (ver seção própria abaixo). LGPD/export (`UserDataService`) inclui `cards`; delete de conta segue ordem FK-safe transações → **recurring** → faturas (`deleteByCardIdIn`) → cards → accounts.
- **Frontend**: painel **Cartões** em Configurações (CRUD vinculado a conta); seletor **"Pagar com"** no form de transação agrupa contas + cartões (`account:<id>`/`card:<id>`), campo "Parcelas" só aparece com cartão selecionado; **badge de método** (Crédito/Débito/Dinheiro) na listagem; tela **Faturas** por cartão com ação "Pagar fatura" que pede a conta; import com mapeamento de cartão. `Transaction.accountId`/`cardId` nullable, `method` sempre presente.

## Fixos no cartão de crédito (sessão #32)

Fecha a pendência da #25 ("fixo em cartão = melhoria futura"): antes, `recurring_transactions.account_id` era `NOT NULL` e não havia `card_id`, então assinatura cobrada no cartão (Netflix, Spotify) não podia ser cadastrada como fixo.

- **Migration V14**: `account_id` vira nullable, entra `card_id` + CHECK `chk_recurring_account_xor_card` (mesma modelagem de `transactions` na V12) + índice em `card_id`. Aditiva/relaxante — os fixos existentes (todos em conta) já satisfazem o XOR, **sem backfill**.
- `RecurringRequest` = `{description, amount, type, accountId?, cardId?, categoryId, dayOfMonth, active, endDate}`: informe **conta OU cartão** (400 "Informe conta OU cartão" se vierem zero ou os dois — checagem no service, não em anotação, porque depende dos dois campos). **Cartão só em gasto**: `type=INCOME` + `cardId` → 400 (igual às transações).
- **Materialização** (`RecurringMaterializationService`): fixo **em conta** → `Transaction.materialized(...)`, `paid=false` (checkbox "pago?" por mês, comportamento inalterado); fixo **em cartão** → resolve o `Card`, chama `CardInvoiceService.getOrCreateInvoiceFor(card, data)` e cria via `Transaction.materializedOnCard(...)` **vinculado à fatura do período** (regra do `closing_day` da #9), com **`paid=true`** — no crédito a quitação é o pagamento da fatura (`INVOICE_PAYMENT`), não um checkbox por ocorrência.
- `RecurringResponse`/`OccurrenceResponse` ganham `cardId`/`cardName` + **`method`** derivado. `PaymentMethod.of(UUID cardId, AccountType)` é o overload novo que serve fixos e transações sem duplicar a regra.
- **Frontend** (`features/recurring/`): seletor **"Pagar com"** com `<optgroup>` Contas + Cartões (valores `account:<id>`/`card:<id>`, padrão da #25), cartões escondidos quando o tipo é Entrada (e o target volta pra conta ao trocar); consome o `CardStore`; **badge de método** na listagem; a coluna "Pago no mês" mostra **"na fatura"** (sem checkbox) para fixo em cartão.
- `.method-badge`/`.method-credito|debito|dinheiro` foram promovidos de `transactions.css` para o **`styles.css` global** (agora compartilhados com Fixos).
- Excluir cartão com fixo vinculado → **409** pelo FK (mesmo comportamento de transações vinculadas; a #27 vai melhorar isso com "arquivar").

## PWA (sessão #20)

- `ng add @angular/pwa` gerou `manifest.webmanifest` (nome "Poupito", `theme_color #0F172A`/`background_color #F3F4F6`; ícones reais do logo desde a sessão #23, não mais o placeholder do Angular), `ngsw-config.json` e o registro do service worker via `provideServiceWorker('ngsw-worker.js', { enabled: !isDevMode() })` no `app.config.ts` — **desabilitado em `ng serve` normal**, só ativa em build de produção (`ng build --configuration production` ou `ng serve --configuration production`).
- Ícones do manifest são o placeholder padrão do schematic (logo do Angular) — ainda não existe um asset de marca próprio do Poupito; pendência conhecida.
- `Shell`: sidebar vira **drawer off-canvas** abaixo de 700px (`transform: translateX(-100%)`, botão hambúrguer fixo, backdrop semi-transparente) — fecha sozinha ao navegar para outra rota ou ao clicar no backdrop. Entre 701–900px mantém o comportamento anterior (barra horizontal rolável, sessão anterior a esta). Estado (`sidebarOpen` signal) não persiste — sempre começa fechada.

## Deploy AWS (sessão #21)

- Stack de produção: **Lightsail** (Ubuntu, US$5/mês, x86_64 — não ARM, apesar do plano original ter cogitado EC2 Graviton) + `infra/docker-compose.prod.yml` (postgres + api + web/Caddy) + Caddy fazendo TLS automático (Let's Encrypt) e reverse proxy de `/api/*` pro container da API.
- As imagens **não são buildadas na instância** (1GB de RAM não aguenta) — `ci-api.yml` e `ci-web.yml` publicam `ghcr.io/jokerfoxyy/dindin-api`/`-web` a cada merge em `main`; o deploy só dá `pull` + `up -d`.
- `.github/workflows/deploy.yml` é **manual** (`workflow_dispatch`, não a cada merge) — conecta via SSH (secrets `DEPLOY_HOST`/`DEPLOY_USER`/`DEPLOY_SSH_KEY`, configurados pelo próprio usuário via `gh secret set`, nunca coladas numa sessão de IA).
- Backup: `infra/scripts/backup.sh` (pg_dump → gzip → S3, cron no host) com lifecycle de 30 dias no bucket (`configure-s3-lifecycle.sh`, roda uma vez). Swap de 2GB e clone do repo: `infra/scripts/setup-host.sh` (roda uma vez na instância nova).
- Passo a passo completo (domínio, DNS, criação da instância, secrets) em `infra/README.md` — são passos manuais que só o usuário pode executar (conta AWS, pagamento, DNS).

## Observabilidade + Hardening (sessão #24)

- **Rate limiting geral da API**: `ApiRateLimiter` (janela fixa in-memory, por regra nomeada
  + IP — generaliza o `LoginRateLimiter` que já existia só pra auth) + `ApiRateLimitFilter`
  (`OncePerRequestFilter`), registrado **antes** do `JwtAuthFilter` na cadeia de segurança —
  protege também os endpoints públicos (login/register), não só os autenticados. Duas regras:
  `default` (toda a API, `app.security.api-rate-limit.default.*`, 120 req/min) e `expensive`
  (`/v1/transactions/export`, `/v1/import/**` — mais caros de CPU/IO — 10 req/min). 429 com
  `Retry-After`, corpo no mesmo formato `ApiError` do resto da API (escrito manualmente no
  filtro, já que roda antes do `@RestControllerAdvice`). Usa `getServletPath()` (não
  `getRequestURI()`) pra casar path sem o context-path `/api` — `getRequestURI()` incluiria o
  prefixo e nunca bateria com os padrões configurados.
- **Logging estruturado**: `logback-spring.xml` com `logstash-logback-encoder` — profile
  `prod` emite JSON no stdout (o `docker logs`/CloudWatch Agent captura), dev/test continua em
  texto legível. Logger dedicado `com.poupito.api.security` (usado por `LoginAttemptLimiter`,
  `LoginRateLimiter` e `ApiRateLimitFilter`) carrega `event`/`ip`/`path` no MDC pra eventos de
  segurança (login falho, lockout, rate limit excedido) — filtráveis depois no CloudWatch
  Insights.
- **Tuning de produção** (`application-prod.yml`): `spring.datasource.hikari.maximum-pool-size`
  (5) e `server.tomcat.threads.max` (50) — enxutos de propósito pra instância Lightsail de
  1GB (Postgres roda no mesmo host).
- **Gotcha de teste**: o rate limit geral roda por trás do mesmo contexto Spring reaproveitado
  entre quase todas as `*FlowIntegrationTest` (mesmo IP `localhost`) — o `maven-surefire-plugin`
  (`api/pom.xml`) sobrescreve os limites pra um valor bem alto via `systemPropertyVariables`
  só durante a suíte; o teste dedicado do filtro (`ApiRateLimitFilterIntegrationTest`)
  sobrescreve de novo pra baixo via `@TestPropertySource` (contexto Spring isolado).
- **O que ficou como runbook manual** (não automatizado, requer conta/acesso que só o usuário
  tem — ver `infra/README.md`): export de logs pro CloudWatch + alarme de lockout, Cloudflare
  gratuito (WAF/DDoS na borda) e fail2ban no SSH (sem restringir por IP, por causa dos
  runners do GitHub Actions).

## Auth & Segurança (sessões #2, #S e #29)

**Modelo de sessão (reescrito na #S):** cookies httpOnly, não JWT no localStorage.
- `POST /api/v1/auth/register` (201) e `/login` (200) setam **dois cookies httpOnly + SameSite=Strict** (`poupito_at` = access JWT 15min; `poupito_rt` = refresh opaco 30d) e retornam só `{id,email}` — **nunca o token no corpo**.
- `POST /auth/refresh` rotaciona o refresh token (o antigo é revogado); `POST /auth/logout` revoga no banco e limpa cookies; `GET /auth/me` retorna `{id,email}`.
- `JwtAuthFilter` lê o access token do cookie `poupito_at` (fallback `Authorization: Bearer` para clientes de API/testes) e popula `AuthenticatedUser(id,email)` (via `@AuthenticationPrincipal`).
- Refresh token: opaco (256 bits), guardado **como hash SHA-256** em `refresh_tokens`, rotacionado a cada uso, revogável. HS256 do access token assinado com `JWT_SECRET`.
- **Frontend:** sem token em JS. `AuthService` guarda só a flag booleana `poupito.authed` (roteamento); interceptor manda `withCredentials` e, em 401, tenta `/refresh` uma vez e repete a requisição, senão desloga.

**Hardening:** rate limiting em login/register (429; `LoginRateLimiter` in-memory por IP+email); **lockout por conta** (`LoginAttemptLimiter`, 5 falhas de login → 5 min, reseta no sucesso, 429 com `Retry-After`; sessão #29); BCrypt strength 12; `SecretsValidator` **falha o startup em profile `prod`** se `JWT_SECRET`/`DB_PASSWORD` forem os defaults de dev; security headers (frame deny, referrer, HSTS); Swagger/api-docs desligados em prod (`application-prod.yml`, `cookie-secure: true`).

**Política de senha (sessão #29):** validador `@StrongPassword` (`common/validation/`) — **≥12 chars com maiúscula + minúscula + número + símbolo** — aplicado em `RegisterRequest` e `ResetPasswordRequest`. **`LoginRequest` continua só `@NotBlank`**: senhas antigas (regime ≥10) seguem logando; login valida contra o hash BCrypt, nunca contra a política (sem migração/rehash). No front, o validador `strongPassword` (`core/auth/password.validator.ts`) espelha a regra; a **dica de política só aparece onde se DEFINE senha (cadastro/redefinir), nunca no login**; toggle "mostrar senha" (olho) nos três formulários.

**Recuperação de senha (sessão #29):** `POST /auth/forgot-password {email}` → **sempre 204** (anti-enumeração — não revela se o email existe); se existir, emite token de reset **opaco (256 bits) guardado como hash SHA-256** em `password_reset_tokens` (migration **V13**, single-use, expiry `app.password-reset.ttl` PT30M, invalida anteriores) e envia email com link `${app.frontend.reset-url-base}?token=...`. `POST /auth/reset-password {token,newPassword}` → consome o token, aplica a senha forte e **revoga todos os refresh tokens** (`revokeAllForUser` — re-login em todo lugar); token inválido/expirado/usado → **400**. Ambos públicos no `SecurityConfig` + rate limit no forgot. Front: telas `/esqueci-senha` e `/redefinir-senha` (token na query).

**Email (`email/` package, sessão #29):** interface `EmailSender` selecionada por `app.mail.enabled` — `LoggingEmailSender` (default: loga o link, dev/test/prod-sem-SES) ou `SmtpEmailSender` (AWS SES via `spring.mail.*`, só quando `enabled=true`). `PasswordResetMailer` monta o email branded. Testes mockam `EmailSender`. Setup do SES (fora do repo): `D:/Docs/Poupito/setup-ses-email.md`. Dependência `spring-boot-starter-mail`.

**CSRF:** desabilitado de propósito — a defesa é o cookie `SameSite=Strict` (não vai em requisição cross-site) numa API JSON same-origin. Modelo de ameaças STRIDE completo mantido fora do repo público (não expor mapa de ataque em app financeiro com usuários reais), em `D:\Docs\Poupito\threat-model-stride.md`.

**LGPD:** `GET /api/v1/account/export` (portabilidade, baixa JSON) e `DELETE /api/v1/account` (elimina usuário + todos os dados vinculados, em ordem FK-safe). No front, "zona de privacidade" em Configurações. Ver `docs/security/lgpd.md`.

Erros padronizados pelo `GlobalExceptionHandler`: 400 (`fieldErrors`/business), 401 (credenciais/refresh), 409 (duplicata/FK), 429 (rate limit), 500. Públicos: register/login/refresh/logout, health, swagger.

## Git workflow

Remote: `https://github.com/JokerFoxyy/Poupito.git`. Mesmo fluxo do ContratoIA:

1. **Ao iniciar qualquer feature/sessão, criar uma branch a partir de `develop`**: `git checkout develop && git checkout -b feature/<descricao-kebab>` (ex.: `feature/auth-jwt`, `feature/setup-frontend`). Nunca trabalhar direto na `main` ou `develop`.
2. Commitar na feature branch (commits pequenos e coerentes) e **sempre dar push para o GitHub**: `git push -u origin feature/<descricao-kebab>`. Trabalho não termina sem push — commit local só não conta.
3. Merge em `develop` via PR — o workflow `feature-pr.yml` cria o PR automaticamente no push da feature branch.
4. `develop → main` só via PR de release (criado automaticamente pelo `auto-pr.yml` no push da develop).

CI (`.github/workflows/`): `ci-api.yml` (mvnw verify com Testcontainers + JaCoCo 90%; imagem Docker → GHCR na main) e `ci-web.yml` (lint, build:prod, test:ci com thresholds) usam **filtros de path** — mudança só em `api/` não roda CI do front e vice-versa; ao editar um workflow, o próprio arquivo está nos paths. `security.yml`: CodeQL (Java e TS), Trivy fs (+ imagem na main), Dependency Review em PRs, cron semanal.

**Ruleset de `develop`/`main`**: 1 aprovação obrigatória + status checks (`build-and-test`, `CodeQL Analysis`). `dismiss_stale_reviews_on_push: false` (desativado 2026-07-16) — aprovação não é descartada por commits novos no PR, já que é sempre o mesmo revisor (o usuário).

`feature-pr.yml`/`auto-pr.yml` (bots que criam os PRs) usam **`secrets.RELEASE_PAT`** (PAT fine-grained do usuário, escopo Pull requests + Contents no repo) em vez do `GITHUB_TOKEN` padrão nos passos `gh pr create`/`gh pr comment`. Motivo: eventos disparados pelo `GITHUB_TOKEN` não acionam outros workflows (regra anti-recursão do GitHub) — o `pull_request: opened` criado por esses bots nunca rodava `ci-api.yml`/`ci-web.yml`/`security.yml` sozinho, precisava de `gh run rerun` manual (aparecia como "action_required"). Com o PAT, o evento é atribuído a um usuário real e o CI dispara normalmente.

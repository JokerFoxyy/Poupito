# Poupito — Plano SDD de Implementação

> **Fonte:** `DinDin/spec-app-financeiro.md` + `DinDin/prototipo-dashboard.html` (pasta local fora do repo git, nome não relacionado à marca)
> **Criado em:** 2026-07-07
> **Decisão do usuário:** Frontend em **Angular** (a spec original sugeria React) e APIs em **Java**.
> Este arquivo é o documento-mestre — mas só do que está **planejado ou em andamento**.
> Sessões já concluídas viraram uma linha de uma frase aqui, com o texto completo
> (motivação, decisões, testes) preservado em `docs/PLANO-HISTORICO.md` (2026-08-02,
> split feito pra manter este arquivo enxuto — ele é relido inteiro toda vez que o
> roadmap é discutido). Cada sessão também tem seu próprio `docs/session-NN-nome/SDD.md`
> com o detalhe técnico de implementação, escrito no momento em que a sessão é iniciada.

---

## 1. Stack definida

| Camada | Tecnologia | Observação |
|---|---|---|
| Backend | Java 21 + Spring Boot 3.5.x | Virtual threads; pacotes por feature (`transaction/`, `budget/`, `investment/`, `goal/`...) |
| API | REST + OpenAPI (springdoc) | Contrato documentado para futuro app mobile |
| Banco | PostgreSQL 16 (Docker Compose) | Dinheiro em `NUMERIC(14,2)` / `BigDecimal`; datas em `LocalDate` |
| Migrations | Flyway | Desde a V1, hoje na V16 |
| Auth | Spring Security + JWT próprio | Cookies httpOnly (ver `CLAUDE.md`) |
| Frontend | **Angular 20 + TypeScript** (standalone components, signals) | Adaptação da spec (React → Angular) |
| UI | Tailwind CSS + componentes próprios | Tema Poupito (claro padrão + dark), sessão #23 |
| Gráficos | Chart.js puro (sem ng2-charts) | Ver seção Dashboard/Investimentos no `CLAUDE.md` |
| Import xlsx | Apache POI (backend) | Parser da Planilha_Gastos_2026 |
| Testes | JUnit 5 + Testcontainers (Postgres real); Karma/Jasmine no front | JaCoCo ≥90% API, Karma ≥90/80/90/90 web |
| CI | GitHub Actions (build + test + CodeQL/Trivy) | |
| Deploy | Docker Compose em AWS Lightsail (produção real em `poupito.com`) + Caddy | |

**Regras de ouro:** nunca float/double para dinheiro; API separada do front; saldo mensal é **cálculo**, não célula (`saldo(mês) = saldo(mês-1) + entradas − gastos`).

## 2. Estrutura do monorepo

```
Poupito/                  (repo git)
├── api/                    Spring Boot (Java 21, Maven)
│   └── src/main/java/com/poupito/api/
│       ├── auth/  account/  category/  card/  transaction/  invoice/
│       ├── recurring/  budget/  goal/  investment/  dashboard/  importer/
│       └── common/         (config, security, erros, money)
├── web/                    Angular 20
│   └── src/app/
│       ├── core/           (auth, state stores, layout shell, theme)
│       ├── features/       (dashboard, transactions, investments, goals, recurring,
│       │                    settings, landing, faq, ...)
│       └── shared/
├── infra/                  docker-compose.yml/.prod.yml, scripts, Caddyfile
└── docs/                   PLANO-SDD.md (este arquivo) + PLANO-HISTORICO.md
                            + session-NN-*/SDD.md
```

## 3. Modelo de dados (migrations Flyway, V1→V16 hoje)

Histórico completo de cada migration em `docs/PLANO-HISTORICO.md` e nas seções
correspondentes do `CLAUDE.md` (Domínio, Remodelagem Contas & Cartões, Auth &
Segurança). Resumo do que cada migration adicionou está preservado lá — este
arquivo foca no que ainda está por vir.

## 4. Regras de negócio críticas

1. **Vínculo de fatura:** lançamento em cartão de crédito é atribuído à `card_invoice` do período conforme `date` × `closing_day` do cartão.
2. **Fechamento de fatura:** usuário informa `declared_total`; se difere do somatório dos lançamentos, o app cria `INVOICE_ADJUSTMENT` com a diferença.
3. **Materialização de fixos:** job `@Scheduled` mensal cria transactions a partir de `recurring_transactions`.
4. **Rentabilidade:** TWR simplificado por período. Comparação com CDI via API SGS do Bacen.
5. **Metas:** aporte mensal necessário = `(target_amount − acumulado) / meses_restantes`.

Detalhe completo de cada regra (fatura, método de pagamento derivado, XOR conta/cartão etc.) está em `CLAUDE.md`, que é a fonte de verdade viva do domínio.

## 5. Sessões concluídas (resumo — texto completo em `docs/PLANO-HISTORICO.md`)

**Fase 0 — Fundação:** #1 Estrutura Inicial ✅ · #2 Auth JWT ✅ · #3 Setup Frontend Angular ✅ · #4 CI/CD ✅

**Fase 1 — MVP:** #5 Contas & Categorias ✅ · #6 Transações backend ✅ · #7 Transações frontend ✅ · #S Segurança STRIDE+LGPD ✅ · #8 Fixos Recorrentes ✅ · #9 Fechamento de Fatura ✅ · #10 Orçamentos ✅ · #11 Dashboard ✅ · #12 Import da Planilha ✅ (Fase 1/MVP completa)

**Fase 2 — Investimentos:** #13 Investimentos backend ✅ · #14 Integração CDI ✅ · #15 Investimentos frontend ✅ · #16 Metas Financeiras ✅ (Fase 2 completa)

**Fase 3 — Qualidade de vida:** #17 Alertas + Busca + Tags ✅ · #18 Parcelamentos ✅ · #19 Export CSV/xlsx ✅ · #20 PWA ✅ · #21 Deploy AWS ✅ (produção real rodando em `poupito.com`)

**Fase 4 — Hardening & Open Finance (concluídas):** #23 Identidade visual ✅ · #24 Observabilidade + Hardening ✅ · #25 Remodelagem Contas & Cartões ✅ · #26 Bugfix estado de contas dessincronizado ✅ · #29 Recuperação de senha + endurecimento ✅ · #32 Fixos no cartão de crédito ✅ · #33 Refino visual (tokens/mobile/botões/fonte Manrope) ✅ · #34 Bugfix nome único conta/cartão ✅ · #40 Landing pública + FAQ ✅ (inclui complemento 2026-08-02: botão de cadastro, diferenciação visual login/cadastro, ajustes WebView mobile) · #42 Correção de bugs (arquivar cartão/fixo + vazamento entre usuários) ✅

## 6. Sessões planejadas / em andamento

> **Ordem de execução atual (atualizada 2026-08-10, #42 concluída): #27 → #30 → #39 → (#28/#37/#22 quando o usuário quiser).** Sessões que ainda precisam de mais conversa (#31/#35/#36/#38/#41/#43/#44) entram na fila quando refinadas com o usuário. Histórico completo de como a ordem foi mudando ao longo do projeto está em `docs/PLANO-HISTORICO.md`.

**#27 — Arquivar contas (soft) + excluir com cascata explícito** 📋 PLANEJADA (refinamento pós-uso, decidido com o usuário 2026-07-23; **escopo reduzido em 2026-08-02** — arquivamento de Cartão e Fixo foi absorvido pela **#42** ✅) — **próxima sessão a rodar**

Motivação: apagar conta com transações/faturas vinculadas hoje só bloqueia (409 por FK).
Decisão (usuário): **arquivar/inativar é a ação principal** (some dos seletores de novo lançamento, mantém histórico visível); **excluir de vez em cascata** vira opção secundária, com confirmação forte mostrando a contagem exata.
⚠️ **Escopo restante após a #42**: só **Account** — Card e RecurringTransaction já ganham arquivamento na #42. Reaproveitar o mesmo padrão (`archived` boolean, filtro nos seletores, endpoint arquivar/desarquivar) já implementado lá.
Tasks a refinar: (1) flag `archived` em `Account` (mesmo padrão da #42); (2) ação de arquivar/desarquivar (endpoint + UI); (3) excluir-com-cascata explícito (serviço transacional + confirmação com contagem) — vale para conta, cartão e fixo, já que nenhum dos três ganhou isso na #42; (4) testes (≥90% API, ≥90/80/90/90 web); (5) verificação e2e.
Pré-req: #25 (modelo conta/cartão) + #42 (padrão de arquivamento já estabelecido).

**#30 — Template de import da planilha (download do modelo compatível)** 📋 PLANEJADA (sessão pequena e separada, decidida com o usuário 2026-07-25) — priorizada 2026-08-02, roda antes da #39

Motivação: o importador (#12) lê a Planilha_Gastos_2026 em **posições fixas de linha/coluna** nas 12 abas mensais. Quem não tiver exatamente esse layout não consegue importar de forma fiel. Falta um **modelo oficial para baixar** no formato exato que o parser espera.
Decisão (usuário): template **.xlsx para download** que espelha 1:1 o que o import consome, com aba de instruções/legenda. Escopo enxuto: gerar/servir o arquivo + link na tela de Import, **não** mexe na regra de parsing.
Tasks a refinar: (1) gerar o template com Apache POI (idealmente derivado das mesmas constantes de posição do `SpreadsheetParser`); (2) endpoint `GET /v1/import/template` + botão "Baixar modelo"; (3) testes (o template gerado passa pelo próprio `preview` sem linhas "não mapeadas" — teste de ida e volta) + verificação e2e. Cobertura padrão (API ≥90%, web ≥90/80/90/90).
Pré-req: #12 (parser) e #25 (mapeamento de cartão no import).

**#39 — SEO** 📋 PLANEJADA — ⚠️ precisa de organização/pesquisa antes de virar SDD (pedido do usuário 2026-08-01)

Motivação: até a #40 (landing pública), não existia nenhuma página pública indexável. SEO de verdade só faz sentido com a landing/FAQ existindo — o dashboard autenticado nunca deveria ser indexado.
Pontos a organizar antes do SDD: (1) meta tags por rota pública (`Title`/`Meta` do `@angular/platform-browser`, landing e FAQ); (2) `robots.txt` + `sitemap.xml` em `web/public/` (liberar só rotas públicas, bloquear autenticadas); (3) JSON-LD (`Organization`/`SoftwareApplication`) na landing; (4) Lighthouse/Core Web Vitals na landing; (5) domínio canônico (`www` vs bare `poupito.com`) + `<link rel="canonical">`; (6) Google Search Console (passo manual do usuário).
Pré-req: **#40** ✅ (já concluída). Ordem: roda depois de #27 e #30.

**#22 — Conexão bancária via Open Finance** 📋 PLANEJADA — última sessão da fila (decisão do usuário 2026-07-25: rodar todas as outras antes)

Ideia inicial: conectar contas de banco via agregador certificado (Pluggy ou Belvo) para puxar extratos/saldos automaticamente.
Tasks a refinar: (1) avaliar Pluggy vs. Belvo (custo, cobertura, free tier); (2) fluxo de consentimento OAuth com o banco; (3) endpoint/job de sincronização periódica → mapeamento pra `transactions` (evitar duplicidade); (4) UI de gerenciamento de conexões; (5) verificação e2e (sandbox do agregador).
Pré-req: Fase 3 completa + #24 (ambos ✅). Trade-off a decidir: custo recorrente por conta conectada escala com base de usuários.

**#28 — Saldo por conta (competência + regime de caixa)** 📋 PLANEJADA (decidido com o usuário 2026-07-23)

Motivação: hoje só existe saldo total agregado em regime de competência. Falta ver o saldo **por conta** e o **dinheiro real disponível** considerando quando faturas são pagas.
Decisão: manter competência como padrão + **adicionar** visão de caixa por conta ("dinheiro real" agora vs. depois de pagar fatura em aberto).
Tasks a refinar: (1) cálculo de saldo de caixa por conta; (2) endpoint (as duas visões); (3) UI (Configurações e/ou Dashboard); (4) testes; (5) verificação e2e.
Pré-req: #25; roda melhor depois da #27 (pra não somar contas/cartões arquivados indevidamente).

**#37 — Loja de temas personalizados ("Supernova", "Pulsar", "Buraco Negro"...)** 📋 PLANEJADA (pedido do usuário 2026-07-31)

Motivação: hoje só dois temas fixos (claro/escuro, #23) via `ThemeService`. Ideia: loja de temas nomeados (espacial), tipo skins de apps de consumo.
Base técnica já pronta pra estender: `ThemeService` já troca `data-theme` + persiste em localStorage; `styles.css` já é 100% variável CSS (nenhuma cor hardcoded); gráficos já leem cor do tema via `chart-theme.ts`.
Pontos a refinar: temas hardcoded no frontend vs. configuráveis no backend; UI de seleção (galeria com preview); acessibilidade (contraste WCAG AA em cada tema novo); persistência (localStorage vs. backend).
Pré-req: nenhum tecnicamente; recomendado rodar depois da #33 (já concluída).

**#45 — Refino visual da Landing (identidade forte)** 📋 PLANEJADA (pedido do usuário 2026-08-14, a partir de review com a skill `frontend-design`)

Motivação: review visual do projeto (skill `frontend-design`) concluiu que o app em geral já foge do "AI slop" óbvio (fonte Manrope em vez de Inter, sem gradiente roxo, sistema de tokens/motion já existente desde a #33) — mas a **Landing** (#40) ainda segue o padrão mais genérico de landing gerada por IA: hero centralizado + grid simétrico de 3 colunas, ícones em emoji, fundo 100% chapado sem atmosfera, título e corpo na mesma fonte só variando peso.
Decisão de escopo: só a **Landing pública** (`features/landing/`) — o app autenticado (dashboard, transações etc.) deve continuar limpo/utilitário de propósito (ferramenta de uso diário pede restraint, não é o caso de aplicar a mesma expressividade).
Tasks a refinar: (1) trocar os ícones emoji dos feature cards por SVG inline (mesmo padrão do shell/olho de senha, sessão #29/#33), usando `--accent`; (2) quebrar a simetria do hero — layout assimétrico (texto + preview estático do dashboard/gráfico, com leve overlap), em vez de tudo centralizado; (3) dar atmosfera ao fundo só da Landing (gradiente mesh sutil ou textura de grão nas cores da marca, sem introduzir cor nova fora da paleta); (4) considerar um par tipográfico (display + corpo) pro hero, se não pesar no `--font-display` custo de carregamento; (5) animação de entrada orquestrada (stagger reveal com `animation-delay` crescente no header/hero/cards), respeitando `prefers-reduced-motion` (padrão já estabelecido em `styles.css`); (6) testes web (Karma ≥90/80/90/90) + verificação visual nos 2 temas e mobile.
Pré-req: #40 (Landing existente) ✅ e #33 (tokens/motion base) ✅ — ambas concluídas.

### Sessões que precisam de mais conversa antes de virar SDD

**#31 — Empréstimos a pessoas / "a receber" (dívidas de terceiros com você)** 📋 PLANEJADA — ⚠️ precisa de mais refinamento (usuário, 2026-07-26)

Motivação: hoje não dá pra registrar dinheiro que **você emprestou** a alguém (ativo a receber). Falta consultar, por pessoa, quanto te devem e acompanhar o recebimento (inclusive parcelado).
Proposta inicial: entidade `Person` + `loan` (valor, data, pessoa, conta de origem, parcelamento do recebimento). Efeito no saldo a decidir: emprestar reduz o caixa da conta de origem mas não conta como gasto; receber de volta abate o saldo devedor sem virar receita.
Nota 2026-08-02: distinta e complementar da nova **#43** (dívida que você **toma** de um banco — direção oposta). Decidir junto se as duas viram uma aba única "Dívidas" ou continuam separadas.
Pré-req: #25; casa melhor depois da #28 (reusa conceito de saldo de caixa).

**#35 — Rollback de deploy em produção** 📋 PLANEJADA — ⚠️ precisa de mais conversa (pedido do usuário 2026-07-26)

Motivação: `deploy.yml` sempre sobe `latest`, sem forma de voltar a uma versão anterior sem editar o workflow na mão.
Já existe a favor: imagens já publicadas com tag por SHA no GHCR; migrations Flyway numeradas e sequenciais.
Escopo: parametrizar `deploy.yml` (`image_tag` opcional); coordenar api+web na mesma tag; documentar (não automatizar) o caso de migration não-reversível (plano B = restore do backup S3); reforçar disciplina de migrations aditivas.
Pontos a fechar: até onde automatizar (alerta em deploy revertido?) vs. manter simples.
Pré-req: nenhum tecnicamente; recomendado depois da #24 (já concluída).

**#36 — Repensar a tela de Investimentos (evolução de patrimônio e comparativos de rendimento)** 📋 PLANEJADA — ⚠️ precisa de mais conversa (pedido do usuário 2026-07-26)

Motivação: o gráfico atual "Evolução do patrimônio × CDI" compara R$ absoluto com % (dois eixos Y) — usuário acha que comparar **rentabilidade % da carteira vs. % do CDI** faria mais sentido (estilo XP/StatusInvest/Kinvo).
Pontos a fechar: qual comparação faz mais sentido; manter curva de patrimônio em R$ separada; outras visualizações de referência (rentabilidade por classe, drawdown, ranking). Cálculo já existe (`InvestmentReturnCalculator`, TWR); mudança é sobretudo de qual métrica plotar.
Pré-req: nenhum tecnicamente; decidir direção antes de misturar com refino visual (já concluído na #33).

**#38 — LLM local para análise de gestão financeira (estudo de viabilidade)** 📋 PLANEJADA — ⚠️ precisa de estudo de viabilidade (pedido do usuário 2026-07-31)

Motivação: LLM pequena (Hugging Face como ponto de partida) pra ajudar a interpretar a gestão financeira — resumir padrões, sinalizar anomalias, responder perguntas em linguagem natural.
Pontos a decidir antes do SDD: onde roda e com que custo de infra (Lightsail 1GB não aguenta modelo local — considerar Inference API HF ou provedor terceiro); privacidade dos dados financeiros (mudaria modelo de ameaças STRIDE se sair da própria infra); qual tarefa concreta e testável; viabilidade de custo recorrente.
Pré-req: nenhum tecnicamente pra começar a discussão.

**#41 — Integração WhatsApp (lançar gastos em linguagem natural)** 📋 PLANEJADA — ⚠️ precisa de mais conversa (pedido do usuário 2026-08-01)

Motivação: permitir mandar mensagem de WhatsApp em linguagem natural (ex. "gasto com padaria de 100 reais") e o Poupito criar a transação sozinho.
Pontos a fechar: canal (WhatsApp Business Cloud API oficial vs. Twilio vs. libs não-oficiais — as não-oficiais não são recomendadas pra produção); extração NL→estruturado (LLM terceiro vs. regras locais — mesma preocupação de privacidade da #38); categoria/conta default; vínculo WhatsApp↔usuário com verificação; confirmação/correção de erro; segurança do webhook; custo recorrente por mensagem.
Pré-req: nenhum técnico pra começar a discussão; recomendado com a Fase 4 mais avançada (#24 já concluída).

**#43 — Empréstimos/dívidas tomadas de banco (a pagar)** 📋 PLANEJADA — ⚠️ precisa de mais conversa (pedido do usuário 2026-08-02)

Motivação: diferente da #31 (inverso — dinheiro que você emprestou, ativo a receber), esta é sobre **dívida que o usuário tomou** — empréstimo bancário/financiamento, um **passivo a pagar**. Efeitos opostos no saldo, não reaproveita a modelagem da #31 diretamente.
Pontos a fechar: escopo (só empréstimo bancário, ou também cheque especial etc.); efeito no saldo (contratação = entrada especial? separar principal de juros?); aba própria "Dívidas" vs. unificada com #31; cronograma simples vs. tabela Price/SAC; saldo devedor e integração com #28.
Pré-req: nenhum técnico pra começar a discussão; decidir junto com a #31 se viram uma única aba "Dívidas".

**#44 — Perfis personalizados de usuário ("Pouperfis")** 📋 PLANEJADA — ⚠️ precisa de decisão sobre armazenamento de imagem (pedido do usuário 2026-08-02)

Motivação: hoje não existe tela de perfil — só email/senha e preferências soltas (tema). Ideia: espaço próprio com foto de perfil e preferências centralizadas.
Pontos a fechar: armazenamento de foto (S3, reaproveitando a conta AWS do backup, vs. avatar gerado por iniciais sem upload); quais preferências entram (nome de exibição, mover toggle de tema pra cá); migration `display_name` em `User`; validação de upload se for S3.
Pré-req: nenhum técnico pra começar a discussão.

## 7. Fase 5 — Futuro (sem sessão planejada ainda)

Ideias soltas: Multi-tenancy real, plano free/pago, cotações via brapi.dev, app mobile consumindo a mesma API. *(A feature "a receber/emprestado" virou a sessão #31; a "dívida tomada" virou a #43.)*

Nota de arquitetura sobre escala/SaaS (tenancy, migrações em produção, custos aproximados de infra HA) preservada em `docs/PLANO-HISTORICO.md` — ainda não é uma sessão planejada, só análise de referência pra quando fizer sentido.

## 8. Grafo de dependências (resumo)

```
#1 → #2 → #3 → #4
      #2 ─────────→ #13 → #14 → #15
      #3 → #5 → #6 → #7 ──┐        #13 → #16
                 #6 → #8   ├→ #11 → #12
                 #6 → #9   │   #11 → #17, #20
                 #6 → #10 ─┘   #9 → #18   #7 → #19
                               #4 + #12 → #21
```

Sessões #13–#16 (investimentos) puderam rodar em paralelo com a Fase 1 a partir da sessão #2.

## 9. Decisões em aberto (herdadas da spec)

- Categoria "Itaú" da planilha mistura conta com categoria → no import (#12), mapear cartão como conta e pedir categoria real.
- Keycloak em vez de JWT próprio se SSO for necessário no futuro (migração possível sem quebrar a API).

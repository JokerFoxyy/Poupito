# Sessão #34 — Bugfix: nome de conta e de cartão único por usuário

> **Status:** em desenvolvimento (iniciada 2026-07-25)
> **Branch:** `feature/session-34-nome-unico-conta-cartao` (a partir de `develop`)
> **Origem:** reportado pelo usuário 2026-07-25 — "cadastro de contas com o mesmo nome não deveria
> ser possível por conta da característica do app".

## 1. O problema

`categories` já garante unicidade desde a V2 (`uq_categories_user_name_kind` + checagem
`existsByUserIdAndNameIgnoreCaseAndKind` no service, retornando 409), mas **`accounts` e `cards`
não têm nada**: `AccountService.create` apenas instancia e salva.

Por que importa neste app: conta e cartão são **rótulos de escolha** em toda a UI — o seletor
"Pagar com" (transações e, desde a #32, fixos), os filtros, o mapeamento do importador, a coluna
"Pago com". Duas contas "Nubank" produzem duas opções visualmente idênticas no dropdown, e o usuário
não tem como saber em qual está lançando. O dado fica correto no banco (ids diferentes) mas
**inutilizável para quem lê a tela** — e o importador, que casa por **nome**, fica ambíguo.

## 2. Escopo

1. **Migration V15** — índices únicos `uq_accounts_user_lower_name` e `uq_cards_user_lower_name`,
   ambos em `(user_id, lower(name))`.
   - **Case-insensitive** (`lower(name)`): "Nubank" e "nubank" são a mesma conta para quem lê a
     tela. Isso deixa o **banco de acordo com o service**, que já compara com `IgnoreCase` — em
     `categories` a constraint é case-sensitive enquanto o service não é, uma inconsistência que
     não vamos repetir aqui.
   - **Deduplicação antes do índice** (crítico): quem já tem nomes repetidos veria a migration
     falhar e a **API não subiria**. A migration renomeia as duplicatas primeiro, mantendo a mais
     antiga (`ORDER BY created_at, id`) com o nome original e sufixando as outras — "Nubank (2)",
     "Nubank (3)". `left(name, 90)` garante que o sufixo caiba no `VARCHAR(100)`.
2. **Backend** — `existsByUserIdAndNameIgnoreCase` nos dois repositories;
   `AccountService`/`CardService` lançam `DuplicateResourceException` (→ **409** pelo
   `GlobalExceptionHandler`) no create e no update. No update, a checagem só roda **se o nome
   mudou** (`!equalsIgnoreCase`), senão salvar sem renomear colidiria com o próprio registro —
   mesmo padrão já usado em `CategoryService`.
3. **Frontend** — `accounts-panel` e `cards-panel` passam a mostrar a mensagem real do servidor no
   409 ("Você já tem uma conta com esse nome") em vez do genérico "Erro ao salvar".

## 3. Decisões

- **Unicidade por usuário, não global**: outra pessoa pode ter a conta "Nubank" — é o modelo pool
  multi-tenant do app (tudo escopado por `user_id`).
- **Case-insensitive**: o valor é ser um rótulo distinguível na tela; diferença de caixa não
  distingue nada para o usuário.
- **Defesa em duas camadas**: service dá a mensagem amigável; o índice único no banco garante a
  invariante mesmo se algum caminho novo esquecer a validação (importador, seed, script).
- **Vale para cartões também**, mesmo que o usuário só tenha citado contas: é exatamente o mesmo
  problema no mesmo tipo de seletor, e a #32 acabou de colocar cartão em mais um lugar ("Pagar com"
  em Fixos). Corrigir só metade deixaria o bug vivo.
- **Nome não é normalizado** (segue salvando como digitado, só com `trim()`): o usuário mantém a
  capitalização que escolheu; a unicidade é que ignora caixa.

## 4. Tasks

1. Migration V15 (dedupe + dois índices únicos).
2. Repositories + services (`create` e `update`) com 409.
3. Frontend: mensagem do 409 nos dois painéis.
4. Testes: unitários (duplicata, diferença só de caixa, rename, salvar mantendo o próprio nome) +
   integração contra Postgres real (409, isolamento entre usuários) — JaCoCo ≥90%.
5. Verificação e2e + docs (CLAUDE.md, PLANO-SDD.md) + PR.

## 5. Critérios de sucesso

- Criar duas contas "Uniclass" → a segunda falha com **409** e mensagem clara na tela.
- "Nubank" e "  nubank  " colidem (case + espaços).
- Outro usuário pode ter uma conta com o mesmo nome.
- Editar uma conta **sem** trocar o nome continua funcionando (não colide consigo mesma).
- Renomear uma conta para o nome de outra → 409.
- O mesmo vale para cartões.
- Quem já tinha duplicatas: a migration renomeia para "Nome (2)" e a API sobe normalmente.
- Cobertura: API JaCoCo ≥90%; web ≥90/80/90/90.

## 6. Ordem de merge (atenção)

Esta migration é a **V15** porque a **V14 está no PR #62 (sessão #32)**, ainda aberto. **Mergear o
PR #62 antes deste** — se este entrar primeiro, o Flyway vai recusar a V14 depois (migration mais
antiga aplicada fora de ordem) sem `outOfOrder=true`.

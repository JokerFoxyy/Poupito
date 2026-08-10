# Sessão #42 — Correção de bugs reportados por usuários

> **Status:** em desenvolvimento (iniciada 2026-08-10)
> **Branch:** `feature/session-42-correcao-bugs` (a partir de `develop`)
> **Origem:** feedback direto de usuários, registrado pelo usuário-dono do projeto em 2026-08-02.
> Reúne dois bugs pontuais numa sessão só (não são features novas).

## 1. Bug 1 — Apagar cartão/fixo vinculado vira "arquivar"

### 1.1 Causa-raiz (confirmada)

Não é defeito novo: é o comportamento **documentado desde a #25/#32** — `CardService.delete`
(`api/src/main/java/com/poupito/api/card/CardService.java:70`) e `RecurringService.delete`
(`api/.../recurring/RecurringService.java:66`) chamam `repository.delete(...)` direto; se existe
transação/fatura/ocorrência vinculada, o Postgres rejeita por FK e a API devolve **409**. No
frontend, `CardsPanel.remove()` (`web/.../settings/cards-panel.ts:82`) já trata esse 409 com uma
mensagem amigável ("pode ter transações ou faturas vinculadas"); `Recurring.remove()`
(`web/.../recurring/recurring.ts:187`) **não trata** — cai no genérico "Erro ao excluir o fixo".

Usuários reportaram isso como bug porque, na prática, **não existe alternativa** — o cartão/fixo
fica preso para sempre assim que tem uma transação. A solução correta não é só melhorar a
mensagem: é oferecer **arquivar**.

### 1.2 Modelagem

Migration **V16** (`archive_cards_and_recurring.sql`), aditiva, sem backfill (tudo nasce
`archived = false`):

```sql
ALTER TABLE cards ADD COLUMN archived BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE recurring_transactions ADD COLUMN archived BOOLEAN NOT NULL DEFAULT false;
```

**`archived` é um conceito novo, distinto de `RecurringTransaction.active`** (que já existe desde
a #8/#32 e só pausa/retoma a materialização mensal, mas mantém o fixo visível na tela com
checkbox). Diferença:

| Flag | O que controla | Onde já existia |
|---|---|---|
| `active` (Recurring) | Job mensal gera ou não uma nova ocorrência | #8/#32 |
| `archived` (Card + Recurring, **novo**) | Aparece ou não na tela principal e nos seletores "Pagar com" | #42 |

Um fixo **arquivado nunca materializa**, independente de `active` — arquivar implica parar de
gerar novo lançamento (não faria sentido esconder da tela e continuar criando transação nova
todo mês). `RecurringMaterializationService` passa a filtrar por `active AND !archived`.

### 1.3 Backend

**Cartões** (`Card`, `CardRepository`, `CardController`, `CardService`, `CardResponse`):
- `Card` ganha campo `archived` (default `false`) + métodos `archive()`/`unarchive()`.
- `CardRepository.findAllByUserIdAndArchivedOrderByNameAsc(UUID userId, boolean archived)`.
- `GET /v1/cards?archived=false` — parâmetro opcional, **default `false`** (não quebra nenhum
  consumidor existente que já chama `GET /v1/cards` sem parâmetro).
- `PATCH /v1/cards/{id}/archive` e `PATCH /v1/cards/{id}/unarchive` (endpoints dedicados, mesmo
  padrão de `POST /v1/invoices/{id}/pay` — ação de negócio explícita, não um PATCH genérico de
  corpo).
- `CardResponse` ganha `archived`.
- **`DELETE /v1/cards/{id}` continua idêntico** (409 se houver vínculo) — arquivar é oferecido
  como alternativa, não substitui a exclusão.

**Fixos** (`RecurringTransaction`, `RecurringTransactionRepository`, `RecurringController`,
`RecurringService`, `RecurringResponse`, `RecurringMaterializationService`):
- `RecurringTransaction` ganha `archived` + `archive()`/`unarchive()`.
- `RecurringTransactionRepository.findAllByUserIdAndArchivedOrderByDescriptionAsc(UUID, boolean)`;
  `findAllByActiveTrue()` → **`findAllByActiveTrueAndArchivedFalse()`** (usado pela
  materialização — fixo arquivado nunca gera ocorrência nova, mesmo se `active` ficou `true`).
- `GET /v1/recurring?archived=false` (default `false`, mesmo padrão do Cards).
- `PATCH /v1/recurring/{id}/archive` e `PATCH /v1/recurring/{id}/unarchive`.
- `RecurringResponse` ganha `archived`.
- `GET /v1/recurring/occurrences` e `POST /v1/recurring/materialize` **não filtram por
  archived** — histórico de ocorrências já geradas continua intacto mesmo se o fixo for
  arquivado depois.

**Histórico nunca filtra por archived** (crítico, é o motivo de existir a feature): `GET
/v1/transactions`, `GET /v1/invoices`, export CSV/xlsx — nenhum desses toca a lógica de archived,
continuam devolvendo tudo. O nome do cartão/fixo arquivado ainda aparece nessas telas via
`cardName`/`accountName` já existentes nas respectivas responses.

### 1.4 Frontend

- `Card`/`Recurring` (models): ganham `archived: boolean`.
- `CardService`/`RecurringService` (frontend): `list(archived = false)` manda `?archived=`;
  novos `archive(id)`/`unarchive(id)` (`PATCH`).
- **`CardStore`/`RecurringService` consumidos por seletores "Pagar com"** (Transações, Fixos,
  Importer) **não mudam** — continuam chamando `list()` sem parâmetro (default `false`), então
  cartões/fixos arquivados já saem sozinhos de todo seletor de novo lançamento, sem tocar em
  `transactions.ts`/`recurring.ts` (`cardsForType()` etc. inalterados).
- **Painel de Cartões** (`cards-panel.ts`/`.html`) e **tela de Fixos** (`recurring.ts`/`.html`):
  - Tabela principal continua lendo só os não-arquivados (via `CardStore`/`RecurringService`
    padrão) — some da "tela principal", exatamente como pedido.
  - Nova ação **"Arquivar"** por linha, ao lado de "Editar"/"Excluir" (sempre visível, não só
    depois de um 409 — mais descobrível).
  - Nova seção **"Arquivados"** (colapsada/discreta, só aparece se houver algum), buscada à parte
    via `cardService.list(true)`/`recurringService.list(true)` — **não** entra no `CardStore`
    compartilhado (só a tela de gestão precisa disso; contaminar o store globalmente obrigaria
    todo consumidor a filtrar de novo). Cada item mostra nome + botão **"Desarquivar"** (sem
    "Editar" — depois de desarquivado volta a aparecer na lista principal, editável normalmente).
  - Mensagem do 409 de "Excluir" atualizada pra mencionar a alternativa: "Não é possível excluir —
    tem transação/fatura vinculada. Use Arquivar para tirar da tela sem perder o histórico."

**Fora de escopo desta sessão** (registrado, não implementado agora):
- Importer: mapeamento de conta/cartão no import **não lista cartões arquivados** (mesma regra
  dos outros seletores) — se isso incomodar alguém importando dados antigos de um cartão já
  arquivado, tratar como bug novo depois, não antecipar agora.
- Exclusão em cascata explícita (apagar de vez com confirmação e contagem) — fica pra **#27**,
  que junta isso com o caso de Conta.

## 2. Bug 2 — Vazamento de dados entre contas ao trocar de usuário no mesmo navegador

### 2.1 Causa-raiz (confirmada)

`AccountStore`/`CardStore`/`CategoryStore` (`web/src/app/core/state/*.store.ts`, sessão #26) são
singletons `@Injectable({ providedIn: 'root' })` com uma flag `loaded` que **nunca é resetada**:

```ts
ensureLoaded(): void {
  if (!this.loaded) {
    this.loaded = true;
    this.refresh();
  }
}
```

`Shell.logout()` (`web/src/app/core/layout/shell.ts:65`) faz só `router.navigate(['/login'])` —
**sem reload de página** — então nenhum singleton Angular é recriado. Se o usuário B loga na
mesma aba logo depois de A deslogar, os componentes chamam `ensureLoaded()` de novo no
`ngOnInit`, mas como `loaded` já é `true` (desde a sessão de A), **o `refresh()` nunca roda** —
as telas mostram os dados de A em memória até alguma mutação disparar `refresh()` manualmente.

### 2.2 Auditoria de outros caches (feita nesta sessão, antes de escrever o SDD)

Levantamento de todo serviço `providedIn: 'root'` do frontend (17 arquivos) em busca de outro
`signal(...)` com o mesmo padrão de risco:

- **`AccountStore`/`CardStore`/`CategoryStore`** — confirmado, é o bug. Corrigido abaixo.
- **`AuthService`** (`currentUser`/`authed`) — já se auto-limpa em `clearSession()`; é o ponto de
  correção, não um cache adicional.
- **`ThemeService`** (tema claro/escuro) — tem signal, mas **não é um leak**: preferência de tema
  é intencionalmente por *dispositivo/navegador*, não por usuário — não deve resetar no logout.
- **`Shell.budgetAlertCount`** (badge de orçamento estourado, sessão #17) — é um signal, mas vive
  no **componente** `Shell`, não num serviço `root`. `Shell` só existe atrás do `authGuard`;
  quando o router sai da árvore de rotas autenticadas (logout → `/login`), o Angular **destrói a
  instância do componente** — o signal morre junto. Login de B recria o `Shell` do zero e
  `ngOnInit` busca os alertas de novo. **Não é bug.**
- Os outros 12 serviços `providedIn: 'root'` (`transaction.service.ts`, `invoice.service.ts`,
  `account.service.ts`, `privacy.service.ts`, `category.service.ts`, `recurring.service.ts`,
  `investment.service.ts`, `import.service.ts`, `goal.service.ts`, `dashboard.service.ts`,
  `budget.service.ts`, `card.service.ts`) são **wrappers HTTP sem estado** (nenhum `signal(`) —
  nada pra vazar.

Conclusão: o bug está **contido nos 3 stores**, sem outro ponto oculto.

### 2.3 Fix

`reset()` em cada store (zera o signal de dados e a flag `loaded`):

```ts
// AccountStore / CardStore / CategoryStore
reset(): void {
  this._accounts.set([]); // (ou _cards / _categories)
  this.loaded = false;
}
```

`AuthService.clearSession()` (`web/src/app/core/auth/auth.service.ts:60`) passa a injetar os 3
stores e chamar `reset()` nos três — é o **único ponto** que já roda tanto no logout explícito
(`logout()`, via `finalize`) quanto na falha silenciosa do refresh de token (interceptor, sessão
#S), cobrindo os dois caminhos de saída de sessão sem duplicar lógica em cada um.

## 3. Tasks (ordem de execução)

1. Migration **V16** (`archived` em `cards` e `recurring_transactions`).
2. Backend Cartões: entidade + repository + `archive`/`unarchive` no service + endpoints +
   `CardResponse.archived` + `GET ?archived=`.
3. Backend Fixos: entidade + repository (+ `findAllByActiveTrueAndArchivedFalse` na
   materialização) + `archive`/`unarchive` no service + endpoints + `RecurringResponse.archived`
   + `GET ?archived=`.
4. Testes backend (arquivar/desarquivar, listagem default exclui archived, materialização não
   gera ocorrência de fixo arquivado, histórico/faturas/ocorrências já geradas continuam
   completos) — JaCoCo ≥90%.
5. `reset()` nos 3 stores (`AccountStore`/`CardStore`/`CategoryStore`) + injeção e chamada em
   `AuthService.clearSession()`.
6. Frontend Cartões: `CardService.archive/unarchive/list(archived)`, ação "Arquivar" +
   seção "Arquivados" com "Desarquivar" em `cards-panel`.
7. Frontend Fixos: mesmo padrão em `recurring.service.ts`/`recurring.ts`/`recurring.html`.
8. Testes web (regressão dos stores + novo teste de logout→login trocando usuário na mesma aba;
   telas de Cartões/Fixos com arquivar/desarquivar) — ≥90/80/90/90.
9. Verificação e2e (usuário de teste local, `teste.dev@poupito.local` — ver `CLAUDE.md`):
   - arquivar cartão com fatura vinculada → some do seletor "Pagar com", fatura antiga continua
     visível em Faturas/Transações;
   - desarquivar → volta a aparecer no seletor;
   - logar como A, ver dados, deslogar, logar como B na mesma aba → nenhuma tela mostra dado de A.
10. Docs (`CLAUDE.md`, `docs/PLANO-SDD.md` — marcar #42 concluída) + commit/push/PR (sem merge).

## 4. Critérios de sucesso

- Cartão/fixo com transação vinculada pode ser **arquivado** (não mais preso permanentemente).
- Arquivado some da tela principal e de todo seletor "Pagar com" (Transações, Fixos, Importer).
- Transações/faturas/ocorrências **já existentes** de um cartão/fixo arquivado continuam 100%
  visíveis no histórico, com nome preservado.
- Fixo arquivado **não gera** nova ocorrência mensal, mesmo que `active` esteja `true`.
- Dá pra desarquivar e o item volta a aparecer normalmente, editável.
- `DELETE` continua se comportando exatamente como hoje (409 se houver vínculo).
- Logout seguido de login com outro usuário, na mesma aba, **nunca** mostra dado do usuário
  anterior em nenhuma tela (Configurações incluída).
- Cobertura mantida: API JaCoCo ≥90%; web ≥90/80/90/90.

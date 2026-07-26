# Sessão #32 — Bugfix: gastos fixos no cartão de crédito

> **Status:** em desenvolvimento (iniciada 2026-07-25)
> **Branch:** `feature/session-32-fixos-no-cartao` (a partir de `develop`)
> **Origem:** bug reportado pelo usuário 2026-07-25 — "no lançamento de gastos fixos só permite vincular a uma conta, mas na prática também dá pra passar no cartão de crédito".
> Fecha a pendência que a **#25** deixou explícita no código (`RecurringMaterializationService`: *"fixos são sempre em conta (débito) desde a sessão #25 — fixo em cartão é melhoria futura"*).

## 1. O bug (causa-raiz)

Não é validação de tela: a limitação é **estrutural**, herdada da migration **V5** (anterior à
remodelagem da #25).

- `recurring_transactions.account_id` é **`NOT NULL`** e **não existe `card_id`**.
- `RecurringService.validate()` resolve **só** `accountRepository.findByIdAndUserId(...)`.
- `RecurringMaterializationService.createOccurrence()` chama `Transaction.materialized(...)`, que
  internamente usa `Transaction.forAccount(...)` — sempre conta, nunca cartão, nunca fatura.

Consequência real: assinaturas/mensalidades cobradas no cartão (Netflix, Spotify, academia…) não
podem ser cadastradas como fixo — ou o usuário mente a origem (lança na conta, o que distorce o
saldo de caixa e não aparece na fatura) ou não usa o recurso.

## 2. Escopo

### Backend

1. **Migration V14** em `recurring_transactions`:
   - `account_id` passa a **nullable**; adiciona `card_id UUID NULL REFERENCES cards (id)`;
   - `CHECK (chk_recurring_account_xor_card)`: exatamente um dos dois preenchido — mesma
     modelagem de `transactions` na #25 (`chk_transactions_account_xor_card`);
   - índice em `card_id`.
   - **Compatível com dados existentes:** todo fixo atual tem `account_id` preenchido e
     `card_id NULL` → satisfaz o XOR sem backfill. Migration puramente aditiva/relaxante
     (`DROP NOT NULL` + `ADD COLUMN`), sem downtime.
2. **Entidade/DTO**: `RecurringTransaction` ganha `cardId` (e `accountId` nullable);
   `RecurringRequest` troca `@NotNull UUID accountId` por `accountId`/`cardId` **ambos opcionais**,
   validados no service (400 "Informe conta OU cartão" se vierem zero ou os dois) — mesma mensagem
   e semântica do `TransactionRequest` da #25.
3. **`RecurringService.validate()`**: resolve conta **ou** cartão (404 se não for do usuário).
   Regra herdada do domínio: **cartão só em gasto** — fixo `INCOME` em cartão → 400 ("Entrada não
   pode ser lançada em cartão de crédito"), igual às transações.
4. **Materialização vinculando à fatura** (`RecurringMaterializationService.createOccurrence`):
   - fixo **em conta** → segue idêntico (`Transaction.materialized`, `paid=false`, flag "pago?");
   - fixo **em cartão** → resolve o `Card`, chama `CardInvoiceService.getOrCreateInvoiceFor(card, date)`
     (regra do `closing_day` da #9/#25) e cria a transação **vinculada ao cartão + fatura**, via
     nova fábrica `Transaction.materializedOnCard(...)`.
   - **`paid` no cartão:** a ocorrência nasce com **`paid = true`**. Motivo: a compra no crédito já
     está "efetivada" na fatura — quem quita é o **pagamento da fatura** (`INVOICE_PAYMENT`), não um
     checkbox por fixo. Marcar como não-paga faria a UI pedir uma ação que não existe nesse fluxo e
     duplicaria a semântica de quitação. Decisão registrada na seção 3.
5. **Respostas**: `RecurringResponse` e `OccurrenceResponse` ganham `cardId`/`cardName` e
   **`method`** (`PaymentMethod` derivado — `CREDITO`/`DEBITO`/`DINHEIRO`, reusando
   `PaymentMethod.of(...)` da #25, nunca digitado).

### Frontend

6. **Seletor "Pagar com"** no form de Fixos, no mesmo padrão da #25 (Transações): `<optgroup>`
   Contas + `<optgroup>` Cartões de crédito, valores `account:<id>` / `card:<id>`; cartões
   **escondidos quando o tipo é Entrada** (`cardsForType()`), e se havia cartão escolhido ao trocar
   para Entrada, volta pra primeira conta (`onTypeChange`).
7. **`CardStore`** (`core/state/card.store.ts`, da #26) passa a ser consumido pela tela de Fixos
   (`ensureLoaded()` no `ngOnInit`) — sem carregar lista isolada.
8. **Badge de método** na listagem de fixos e nas ocorrências (Crédito/Débito/Dinheiro), reusando o
   padrão visual já existente em Transações.
9. **Checkbox "pago?"**: só faz sentido para fixo em conta. Em fixo no cartão, mostrar o vínculo com
   a fatura em vez do checkbox (a quitação é o pagamento da fatura).

## 3. Decisões

- **XOR conta/cartão** (não um campo "tipo de origem"): espelha exatamente `transactions` da #25 —
  um só jeito de modelar "onde o dinheiro sai" no projeto todo.
- **Fixo no cartão entra na fatura** (decisão do usuário 2026-07-25): a ocorrência é uma compra no
  crédito e cai na fatura do período pelo `closing_day`. Sem isso, a aba Faturas ficaria incompleta
  (assinatura do cartão não apareceria lá) — foi o motivo original do bug da aba Faturas zerada no
  importer, corrigido na #25; não repetir o mesmo erro aqui.
- **`paid = true` para ocorrência no cartão** (ver 4 acima): a quitação é o `INVOICE_PAYMENT`.
  Fixo em conta mantém `paid = false` + checkbox (comportamento atual preservado).
- **Sem migração de dados**: fixos existentes continuam em conta, válidos pelo XOR.
- **Entrada (`INCOME`) não vai em cartão**: consistente com `TransactionService` da #25.

## 4. Tasks (ordem de execução)

1. Migration V14 + entidade `RecurringTransaction` (accountId nullable + cardId) + `RecurringRequest`.
2. `RecurringService`: validação XOR + cartão-só-em-gasto + respostas com `cardId`/`cardName`/`method`.
3. `Transaction.materializedOnCard(...)` + materialização resolvendo cartão → fatura.
4. Testes backend (XOR, INCOME em cartão → 400, materialização em cartão cria/vincula fatura,
   regressão de fixo em conta) — JaCoCo ≥90%.
5. Frontend: "Pagar com" (contas + cartões), `CardStore`, badge de método, checkbox só em conta.
6. Testes web (≥90/80/90/90) + verificação e2e no browser.
7. Docs vivos (CLAUDE.md, PLANO-SDD.md) + PR (sem merge).

## 5. Critérios de sucesso

- Consigo cadastrar um fixo "Netflix" **no cartão de crédito** (antes: impossível).
- Ao gerar os lançamentos do mês, a ocorrência do fixo no cartão **aparece na fatura** do cartão no
  período correto (respeitando o `closing_day`).
- Fixo em conta continua funcionando **exatamente** como antes (checkbox "pago?", débito na conta).
- Entrada em cartão é recusada (400) e os cartões nem aparecem no seletor com tipo Entrada.
- Fixos já cadastrados (em conta) seguem íntegros após a migration.
- Cobertura: API JaCoCo ≥90%; web ≥90/80/90/90.

# SDD — Sessão #33: Refino visual (tipografia, tokens, ícones, mobile e botões)

## Motivação

A marca tem personalidade (navy + esmeralda, logo próprio — #23), mas a **execução
visual** é genérica, e usuários já estão sentindo os problemas na prática (bugs
reportados com print em 2026-07-26/27). Escopo consolidado no `PLANO-SDD.md` (#33).

## Decisões tomadas com o usuário

- **Tabelas no mobile → colapsar em cards empilhados** (decisão do usuário 2026-08-01,
  escolhida sobre a alternativa "scroll horizontal + coluna sticky"). Mais trabalho, mas
  elimina scroll horizontal de vez e dá cara de app, não de planilha.
- Ordem de execução: tokens/tipografia primeiro (base), depois os bugs, depois o
  polimento — assim os bugs já são corrigidos em cima da base nova, sem retrabalho.

## Tasks

1. **Tokens de design em `styles.css`**: escala tipográfica com hierarquia real
   (`--text-xs` ... `--text-3xl`, com salto de verdade pros números financeiros — hoje
   quase tudo vive entre 11-15px e salta pra 24), tokens `--radius-*` (reduzir os 4/6/8/9/10/12px
   espalhados pra 2-3 raios com propósito) e `--space-*` (ritmo vertical consistente).
2. **Aplicar os tokens** nos componentes existentes, substituindo valores hardcoded — sem
   mudar estrutura, só trocar número solto por token.
3. **Emoji → SVG** no `shell.html`: `☰` (menu hambúrguer) e `☀️/🌙` (toggle de tema). Padrão
   já validado na #29 (olho de mostrar senha, SVG inline com `currentColor`).
4. **Bugfix — coluna de ações desalinhada**: `.row-actions` aplica `display: flex` direto no
   `<td>`, sobrescrevendo o `display: table-cell` nativo e quebrando o `vertical-align`.
   Correção: envolver o conteúdo num `<div class="row-actions">` dentro do `<td>`. Afeta
   **8 telas**: transactions, recurring, cards-panel, accounts-panel, categories-panel,
   investments, goals, budgets.
5. **Bugfix — responsividade mobile**: nas mesmas 8 telas, abaixo de ~640px a tabela colapsa
   em **cards empilhados** (cada linha vira um card com rótulo + valor por campo, via
   `@media` + `display: block` nos elementos de tabela e `::before` com `data-label` para os
   rótulos, ou markup dedicado onde ficar mais limpo).
6. **Animações e refino de botões**: `transition` (150-200ms) em `.btn`/`.btn-ghost`/`.link`,
   estado `:active` com leve scale-down, sombra sutil no CTA primário no hover, e foco
   visível desenhado (`:focus-visible`) consistente.
7. **Testes web** (≥90/80/90/90 no Karma) + verificação visual nos dois temas **e em viewport
   mobile**.

## Fora de escopo (decidido)

- **Troca de fonte** (item 4 do plano original): parte mais subjetiva, depende de escolha de
  marca do usuário. Fica pra uma sessão futura ou pra um segundo momento desta, depois de
  ver o resultado dos tokens no browser.
- Loja de temas (#37) — sessão própria, roda depois desta.

## Critério de sucesso

- Nenhum valor solto de `font-size`/`border-radius`/espaçamento nos componentes novos —
  tudo via token.
- Coluna de ações alinhada verticalmente com as demais colunas nas 8 telas.
- Nenhuma tabela com scroll horizontal no mobile (viewport 375px) — todas colapsam em cards.
- Botões com feedback visual de hover/active perceptível.
- Karma ≥90/80/90/90 mantido; verificação visual nos dois temas + mobile.

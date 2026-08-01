# SDD — Sessão #40: Landing pública + FAQ para novos usuários

## Motivação

Hoje não existe nenhuma página pública. `path: ''` casa com o `Shell` autenticado
(`authGuard`), que redireciona qualquer visitante não-logado direto pro `/login` — sem
nenhum contexto sobre o que é o Poupito antes disso. Pedido do usuário (2026-08-01):
"colocar algumas coisas agora que tá produtivo" — landing + FAQ pra novos usuários.

## Decisão de roteamento (a parte que exige cuidado)

`path: ''` (vazio, sem `pathMatch: 'full'`) hoje faz **prefix match** — ou seja, o `Shell`
intercepta qualquer URL e delega pros filhos (`dashboard`, `transacoes`...). Pra encaixar a
Landing sem mudar nenhum path já existente:

1. Nova rota **antes** do `Shell` no array: `{ path: '', pathMatch: 'full', canActivate:
   [redirectIfAuthenticatedGuard], loadComponent: Landing }` — o `pathMatch: 'full'` é
   essencial: só intercepta a barra `/` exata, nunca `/dashboard` (que tem segmento
   restante, então essa rota não entra em jogo pra ele).
2. `redirectIfAuthenticatedGuard` (novo, espelha o `authGuard` existente): se já
   autenticado, `router.createUrlTree(['/dashboard'])`; senão `true` (mostra a Landing).
   Sem isso, um usuário logado que voltasse pra `/` veria a landing de novo (a rota do
   `Shell` nunca seria tentada pra path vazio, já que a Landing responde primeiro).
3. `Shell` continua com `path: ''` (sem `pathMatch`), então continua fazendo prefix match
   pra tudo que não é a barra exata — `/dashboard`, `/transacoes` etc. **inalterados**.
4. Nova rota `{ path: 'faq', loadComponent: Faq }` — pública, sem guard.

## Tasks

1. `redirectIfAuthenticatedGuard` (`core/auth/`) + teste.
2. `features/landing/` (`Landing`): hero explicando o Poupito (substitui planilha —
   transações, contas/cartões com fatura, fixos, orçamentos, dashboard, investimentos,
   metas), lista de features, CTA "Criar conta"/"Entrar", link pra FAQ.
3. `features/faq/` (`Faq`): perguntas comuns pré-cadastro (é grátis? dados seguros/LGPD?
   dá pra importar minha planilha? funciona no celular?), link de volta pra Landing/login.
4. Atualizar `app.routes.ts` (ordem importa — ver decisão acima).
5. Testes (Landing, Faq, guard) + Karma ≥90/80/90/90.
6. Verificação: `/` sem sessão mostra Landing; `/faq` acessível sem sessão; `/dashboard`
   sem sessão ainda redireciona pro login (comportamento existente preservado).

## Fora de escopo

SEO propriamente dito (meta tags, sitemap, robots.txt, JSON-LD) — vira a sessão #39,
que depende desta existir primeiro.

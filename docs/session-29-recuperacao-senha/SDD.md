# Sessão #29 — Senhas: recuperação por email + endurecimento

> **Status:** em desenvolvimento (iniciada 2026-07-25)
> **Branch:** `feature/session-29-recuperacao-senha` (a partir de `develop`)
> **Fonte:** roadmap `docs/PLANO-SDD.md` (sessão #29) — gap real de produção: hoje, esquecendo a senha, o usuário fica trancado pra fora (não há fluxo de recuperação).

## 1. Objetivo

Fechar o buraco de acesso da produção (recuperar senha por email) e, junto, endurecer o
subsistema de senha/login: política de senha mais forte, botão "mostrar senha", lockout por
conta e limpeza do hint de política na tela de login.

Alinha com o modelo de auth da sessão #S (tokens **opacos** + **hash SHA-256** + rate limiting),
reusando os padrões já existentes (`RefreshTokenService`, `LoginRateLimiter`,
`revokeAllForUser`).

## 2. Escopo

### Backend

1. **Migration V13** `password_reset_tokens` (`id`, `user_id` FK→`users`, `token_hash` UNIQUE,
   `expires_at`, `used_at` nullable, `created_at`). Índice em `user_id`.
2. **Fluxo de recuperação** (`AuthController` + `AuthService` + novo `password/` package):
   - `POST /v1/auth/forgot-password {email}` → **sempre 204** (anti-enumeração — não revela se o
     email existe). Se existir, gera token de reset **opaco (256 bits)**, guarda só o **hash
     SHA-256** em `password_reset_tokens` (single-use, expiry curto — default **PT30M**), invalida
     tokens de reset ativos anteriores do mesmo usuário e **envia email** com o link
     `${app.frontend.reset-url-base}?token=...`. Rate-limit por IP+email (reusa `LoginRateLimiter`).
   - `POST /v1/auth/reset-password {token, newPassword}` → valida (existe, não expirado, não usado),
     aplica a nova senha (BCrypt, **política forte** — ver item 3), marca o token como usado e
     **revoga todos os refresh tokens** do usuário (`revokeAllForUser` — força re-login em todo
     lugar, caso a conta estivesse comprometida). Token inválido/expirado → **400**.
3. **Política de senha forte compartilhada** (`common/validation/@StrongPassword` +
   `StrongPasswordValidator`): **≥12 caracteres com maiúscula + minúscula + número + símbolo**.
   Aplicada em `RegisterRequest.password` e `ResetPasswordRequest.newPassword` (validador único, sem
   duplicar regra). **`LoginRequest` continua só `@NotBlank`** — usuário com senha antiga (regime
   ≥10) tem que conseguir logar; login valida contra o hash BCrypt, não contra a política.
   > Migração de senha: não há. Fortalecer a política só afeta **novas** senhas (registro/reset);
   > as existentes seguem válidas no login. Sem re-hash forçado.
4. **Envio de email pluggável** (`email/` package): interface `EmailSender` com duas implementações
   selecionadas por propriedade `app.mail.enabled`:
   - `LoggingEmailSender` (**default**, `app.mail.enabled=false`) — loga o link (dev/test/prod
     sem SES ainda). Não bloqueia o desenvolvimento nem o deploy.
   - `SmtpEmailSender` (`app.mail.enabled=true`) — usa `JavaMailSender` (Spring Mail) → AWS SES
     via SMTP. Config em `application-prod.yml` (host/port/user/pass via env), documentada em
     `D:/Docs/Poupito/setup-ses-email.md`.
   - `PasswordResetMailer` monta o email branded (assunto + corpo texto com o link). Nos testes,
     `EmailSender` é **mockado**.
   - Dependência nova: `spring-boot-starter-mail` no `api/pom.xml`.
5. **Lockout por conta** (`common/security/LoginAttemptLimiter`): **5 falhas de login por conta →
   bloqueio de 5 minutos.** Conta **falhas** (não toda tentativa) e **reseta no sucesso** —
   diferente do `LoginRateLimiter` (janela fixa por IP+email, anti-burst, conta toda tentativa e
   já existe). Os dois coexistem (defesa em profundidade). Fluxo do login no controller:
   `rateLimiter.check(ip:email)` → `attemptLimiter.checkNotLocked(email)` → tenta autenticar →
   em falha `attemptLimiter.recordFailure(email)` + rethrow; em sucesso `attemptLimiter.reset(email)`.
   O 429 do lockout carrega **segundos restantes** (`Retry-After` header) pra o front mostrar o
   tempo. `TooManyRequestsException` ganha um campo opcional `retryAfterSeconds` + o handler seta o
   header.
6. **SecurityConfig**: `permitAll` para `/v1/auth/forgot-password` e `/v1/auth/reset-password`.

### Frontend

7. **Telas novas** (rotas públicas, fora do Shell):
   - `/esqueci-senha` (`ForgotPassword`) — pede o email; após enviar mostra **sempre** a mesma
     mensagem neutra ("Se o email existir, enviamos um link..."), sem revelar existência.
   - `/redefinir-senha` (`ResetPassword`) — lê `token` da query string, pede a nova senha (com o
     hint de política + validação forte), chama `reset-password`, e em sucesso manda pro login com
     aviso. Token ausente/erro → mensagem clara.
8. **Toggle "mostrar senha"** (botão de olho) nos campos de senha de **login, cadastro e
   redefinir**.
9. **Hint de política de senha só onde se DEFINE senha** (cadastro + redefinir), **nunca no
   login** — hoje o `login.html` mostra "A senha deve ter pelo menos 10 caracteres..." mesmo no
   modo login. Ajustar: validador de senha dinâmico por modo (`login` = só `required`; `register`
   = política forte) e o texto do hint só no modo cadastro. Link "Esqueci minha senha" no login.
10. **Feedback de lockout**: em 429 no login, mostrar "Muitas tentativas. Tente de novo em X min"
    usando o `Retry-After`.
11. `AuthService`: métodos `forgotPassword(email)` e `resetPassword(token, newPassword)`.

## 3. Decisões

- **Anti-enumeração:** forgot-password sempre responde 204 (não 200 com corpo, não 404). O front
  mostra mensagem neutra. Custo: um usuário que digita email errado não é avisado — aceitável e
  padrão de mercado pra esse fluxo.
- **Token opaco + hash no banco** (não JWT): mesmo padrão do refresh token (#S). Vazamento do banco
  não expõe tokens usáveis. Single-use + expiry curto (30 min) + invalidação dos anteriores.
- **Reset revoga refresh tokens:** se a conta foi comprometida, trocar a senha derruba todas as
  sessões. Consistente com "trocar senha = re-login em todo lugar".
- **Política forte só em novas senhas:** login nunca aplica a política (senão trancaria usuários
  antigos). Sem migração/rehash.
- **Email pluggável por propriedade:** o core (#29) desenvolve e testa com email **logado**; ligar
  o SES é flipar `app.mail.enabled=true` + credenciais — não bloqueia esta sessão (o SES depende de
  DNS/infra, feito pelo usuário depois, ver `setup-ses-email.md`).
- **Lockout separado do rate limiter:** semânticas diferentes (falha-com-reset vs. toda-tentativa).
  Componente próprio in-memory (ok pra single-instance Lightsail; multi-instância → store
  compartilhado, mesma ressalva já documentada no `LoginRateLimiter`).

## 4. Configuração nova (`application.yml`)

```yaml
app:
  security:
    lockout:
      max-failures: ${LOGIN_LOCKOUT_MAX:5}
      window: ${LOGIN_LOCKOUT_WINDOW:PT5M}
  password-reset:
    ttl: ${PASSWORD_RESET_TTL:PT30M}
  frontend:
    reset-url-base: ${FRONTEND_RESET_URL:http://localhost:4200/redefinir-senha}
  mail:
    enabled: ${MAIL_ENABLED:false}
    from: ${MAIL_FROM:nao-responda@poupito.com}
```

Em `application-prod.yml`: `reset-url-base: https://poupito.com/redefinir-senha`, `mail.enabled`
conforme SES, e `spring.mail.*` (host/port/user/pass via env).

## 5. Tasks (ordem de execução)

1. `@StrongPassword` + validador; aplicar em `RegisterRequest`; `LoginRequest` intacto.
2. Migration V13 + entidade/repository + `PasswordResetService` (opaco/hash/single-use/expiry).
3. `EmailSender` (log/SMTP) + `PasswordResetMailer` + `spring-boot-starter-mail`.
4. `LoginAttemptLimiter` (5/5min, reset no sucesso) + `Retry-After` no 429.
5. Endpoints forgot/reset no `AuthController` + `AuthService` + SecurityConfig + config yml.
6. Testes backend (validador, service, endpoints, lockout, email mockado) — JaCoCo ≥90%.
7. Frontend: telas esqueci/redefinir, toggle mostrar senha, hint só no cadastro/reset, link no
   login, feedback de lockout, `AuthService`.
8. Testes web (≥90/80/90/90) + verificação e2e no browser.
9. Docs vivos (CLAUDE.md, PLANO-SDD.md marcando #29 concluída) + PR (sem merge).

## 6. Critérios de sucesso

- Esqueci a senha → recebo o link (logado em dev), redefino, e as sessões antigas caem.
- forgot-password não revela se o email existe (sempre 204 / mensagem neutra).
- Registro/reset exigem senha forte; login aceita a senha antiga sem impor a política nova.
- 5 logins errados na mesma conta → 429 por 5 min, com contagem regressiva no front.
- O hint de política **não** aparece na tela de login, só em cadastro/redefinir.
- Botão de olho mostra/oculta a senha nos três formulários.
- Cobertura: API JaCoCo ≥90%; web ≥90/80/90/90.

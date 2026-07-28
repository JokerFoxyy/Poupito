# Deploy em produção (sessão #21)

Stack: **AWS Lightsail** (instância Ubuntu, US$5/mês) + Docker Compose + Caddy (TLS automático via Let's Encrypt) + backup para S3.

## Visão geral

```
Internet → Caddy (:80/:443, TLS automático)
             ├─ /api/*  → api (Spring Boot, :8080)
             └─ /*      → estático do Angular (SPA fallback)
                              ↓
                          postgres (rede interna, não exposta)
```

- `api` e `web` (Caddy + estático) são publicados pelo CI em `ghcr.io/jokerfoxyy/poupito-api` e `ghcr.io/jokerfoxyy/poupito-web` a cada merge em `main` (`.github/workflows/ci-api.yml` / `ci-web.yml`, job `docker`).
- O deploy (`.github/workflows/deploy.yml`) **não builda nada na instância** — só faz `pull` das imagens prontas e sobe via SSH. Instância de 1GB de RAM não aguentaria buildar Maven+Angular.
- Disparo do deploy é **manual** (`workflow_dispatch`), não a cada merge — decisão deliberada de quando ir pra produção.

## Passo a passo (manual, uma vez)

Nada disso é automatizado — requer acesso à conta AWS/domínio do usuário.

1. **Domínio**: comprar um domínio (Route 53, Registro.br, Namecheap etc.).
2. **Instância Lightsail**: criar uma instância Ubuntu 22.04+ (plano US$5/mês), reservar o **IP estático** (Lightsail cobra separado se não usar, mas sem IP fixo o domínio quebra a cada restart).
3. **DNS**: criar um registro **A** do domínio apontando para o IP estático da instância.
4. **Setup da instância** (via SSH): copiar e rodar `infra/scripts/setup-host.sh` — instala Docker, cria swap de 2GB, clona o repo em `/opt/poupito`.
5. **Configurar `.env`**: `cp infra/.env.prod.example infra/.env` na instância e preencher com valores reais (senhas, `JWT_SECRET` gerado com `openssl rand -base64 48`, `DOMAIN`, `ACME_EMAIL`).
6. **Subir a stack pela primeira vez** (na instância): `docker compose -f infra/docker-compose.prod.yml --env-file infra/.env up -d`.
7. **Bucket S3 de backup**: criar o bucket, depois rodar `./infra/scripts/configure-s3-lifecycle.sh <bucket>` uma vez (expira objetos com 30 dias).
8. **Agendar backup**: `crontab -e` na instância → `0 3 * * * /opt/poupito/infra/scripts/backup.sh >> /var/log/poupito-backup.log 2>&1`.
9. **Credenciais AWS na instância**: `aws configure` (ou IAM role anexada à instância, preferível) com permissão `s3:PutObject` no bucket de backup.
10. **Secrets no GitHub** (para o workflow de deploy funcionar), rodados pelo próprio usuário — nunca cole chave privada SSH em uma sessão de IA:
    ```
    gh secret set DEPLOY_HOST --body "<ip-ou-dominio>"
    gh secret set DEPLOY_USER --body "ubuntu"
    gh secret set DEPLOY_SSH_KEY < caminho/para/chave_privada.pem
    ```

## Deploys seguintes

Depois do setup inicial: merge em `main` → CI builda e publica as imagens no GHCR → rodar manualmente o workflow **Deploy** (Actions → Deploy → Run workflow) → ele faz SSH na instância, `git pull` (pra pegar mudanças em compose/Caddyfile/scripts) + `docker compose pull` + `up -d`.

## Verificado localmente (sem domínio real ainda)

Smoke test rodado nesta sessão com imagens buildadas localmente e `DOMAIN=:80` (bypassa TLS automático do Caddy, só pra validar rede/proxy):
- `GET /api/actuator/health` → `200 {"status":"UP"}` via `reverse_proxy` do Caddy para o container `api`.
- `GET /` → `200`, serve o `index.html` do build Angular.
- `GET /dashboard` (rota client-side) → `200`, `try_files` cai no `index.html` (SPA fallback funcionando).

**O que ainda não foi verificado** (depende de infraestrutura real que só o usuário pode provisionar): TLS automático de verdade (precisa de domínio público + porta 80/443 abertas pro Let's Encrypt validar o desafio HTTP-01), a instância Lightsail em si, o backup rodando contra um bucket S3 real, e o workflow de deploy via SSH contra um host real.

# Observabilidade + Hardening (sessão #24)

O rate limiting geral da API, o logging estruturado (JSON) e o tuning de HikariCP/Tomcat
(ver `CLAUDE.md`, seção "Auth & Segurança") já vêm prontos no código/imagem — não exigem
nenhum passo manual. O que falta é infraestrutura **fora da instância**, que só o usuário
pode provisionar (conta AWS CloudWatch, conta Cloudflare, acesso root à instância). Runbook
abaixo — nenhum destes passos foi automatizado por IA, mesmo padrão da seção acima.

## 1. Export de logs pro CloudWatch + alarme

Os containers em produção já emitem logs em **JSON estruturado** no stdout (profile
`prod`, `logback-spring.xml`) — eventos de segurança (login falho, lockout, rate limit
excedido) carregam `event`/`ip`/`path` no corpo do log, prontos pra filtro no CloudWatch
Insights.

1. Instalar o **CloudWatch Agent** na instância Lightsail (`amazon-cloudwatch-agent` via apt,
   ou o pacote `.deb` da AWS) e configurar pra capturar o log do Docker (`journalctl -u docker`
   ou o driver de log `awslogs` direto no `docker-compose.prod.yml`, adicionando
   `logging: { driver: awslogs, options: {...} }` nos serviços `api`/`web`).
2. Criar um **Log Group** (ex. `/poupito/api`) com retenção curta (**30-90 dias** — controla
   custo; segurança já foi tratada, não é o arquivo permanente de auditoria).
3. Criar um **CloudWatch Alarm** simples via **Metric Filter** no Log Group, casando o padrão
   `{ $.event = "login_lockout_triggered" }` (ou `rate_limit_exceeded`), disparando uma métrica
   customizada; alarme dispara acima de um threshold (ex. >10 no período) → notifica por
   **SNS → email**.
4. Credenciais: usar uma **IAM role anexada à instância** (preferível, mesma recomendação do
   backup S3 acima) com permissão `logs:PutLogEvents`/`logs:CreateLogStream`, nunca chave de
   acesso em texto puro na instância.

## 2. Cloudflare gratuito na frente da instância

DDoS/WAF na borda antes mesmo de chegar no Lightsail — mais barato e efetivo que construir
isso na aplicação.

1. Criar conta Cloudflare gratuita, adicionar o domínio, apontar os nameservers pro Cloudflare
   (no registrador do domínio).
2. Recriar o registro **A** já existente (aponta pro IP da instância) dentro do painel
   Cloudflare.
3. **Antes** de ligar o proxy (nuvem laranja): ir em **SSL/TLS → Overview** e mudar o modo pra
   **Full (strict)**. Fazer isso depois de ligar o proxy quebra com loop de redirect (o Caddy
   já serve HTTPS válido via Let's Encrypt; "Flexible" faria o Cloudflare falar HTTP com o
   Caddy, que redireciona pra HTTPS, gerando o loop).
4. Ligar o proxy (nuvem laranja) no registro A. A partir daí, tráfego passa pelo WAF/DDoS
   gratuito do Cloudflare antes de chegar na instância.
5. Regras de rate limiting adicionais (opcional, plano gratuito tem cota limitada de regras)
   podem complementar o `ApiRateLimitFilter` da aplicação — a defesa em profundidade não
   depende de nenhuma das duas camadas sozinha.

## 3. Endurecer SSH sem quebrar o deploy

1. Confirmar `PasswordAuthentication no` em `/etc/ssh/sshd_config` (Lightsail já vem assim por
   padrão — só autentica com a chave `.pem`).
2. Instalar **fail2ban** (`apt install fail2ban`) com o jail padrão `sshd` habilitado
   (`/etc/fail2ban/jail.local`: `[sshd]` `enabled = true`) — bloqueia IPs com tentativas
   repetidas de força bruta na porta 22.
3. **Não** restringir a porta 22 do firewall do Lightsail a um IP fixo do usuário — o workflow
   `deploy.yml` faz SSH a partir de **runners do GitHub Actions**, que usam IPs dinâmicos e
   mudam a cada execução. Restringir por IP quebraria o deploy automatizado na próxima vez que
   o runner saísse do range liberado. `fail2ban` já dá proteção equivalente sem depender de
   IP fixo.
4. Verificar que o firewall do Lightsail (aba Networking) continua liberando só 22/80/443 —
   isso já cobre a camada de rede (L3/L4); fail2ban cobre a camada de aplicação SSH por cima.

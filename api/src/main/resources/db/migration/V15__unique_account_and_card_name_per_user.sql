-- Sessão #34: nome de conta e de cartão passa a ser único por usuário.
-- Antes só `categories` tinha essa garantia (uq_categories_user_name_kind, V2); conta e cartão
-- aceitavam nomes repetidos, o que torna os seletores ("Pagar com", filtros) ambíguos — o usuário
-- vê duas linhas "Nubank" e não sabe qual é qual.
--
-- Unicidade é CASE-INSENSITIVE (índice em lower(name)): "Nubank" e "nubank" são a mesma conta para
-- quem lê a tela. Isso deixa o banco de acordo com o service, que já compara com IgnoreCase.

-- 1) Deduplicar o que já existe, senão a criação do índice falharia e a API não subiria.
--    Mantém o registro mais antigo com o nome original e sufixa os demais: "Nubank (2)", "Nubank (3)".
--    left(name, 90) garante que o sufixo caiba no VARCHAR(100).
WITH duplicated AS (
    SELECT id,
           row_number() OVER (PARTITION BY user_id, lower(name) ORDER BY created_at, id) AS rn
    FROM accounts
)
UPDATE accounts a
SET name = left(a.name, 90) || ' (' || d.rn || ')'
FROM duplicated d
WHERE a.id = d.id
  AND d.rn > 1;

WITH duplicated AS (
    SELECT id,
           row_number() OVER (PARTITION BY user_id, lower(name) ORDER BY created_at, id) AS rn
    FROM cards
)
UPDATE cards c
SET name = left(c.name, 90) || ' (' || d.rn || ')'
FROM duplicated d
WHERE c.id = d.id
  AND d.rn > 1;

-- 2) Garantir a unicidade de agora em diante.
CREATE UNIQUE INDEX uq_accounts_user_lower_name ON accounts (user_id, lower(name));
CREATE UNIQUE INDEX uq_cards_user_lower_name ON cards (user_id, lower(name));

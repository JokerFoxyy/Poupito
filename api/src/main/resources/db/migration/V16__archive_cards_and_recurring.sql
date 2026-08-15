-- Sessão #42: apagar cartão/fixo com transação/fatura/ocorrência vinculada sempre bloqueava (409
-- por FK, comportamento documentado desde a #25/#32). Em vez de só melhorar a mensagem de erro, a
-- solução é "arquivar": o item some da tela principal e dos seletores "Pagar com", mas o histórico
-- (transações/faturas/ocorrências já geradas) continua intacto.
--
-- Migration puramente aditiva/relaxante — todo registro existente nasce archived=false, sem
-- backfill nenhum.

ALTER TABLE cards ADD COLUMN archived BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE recurring_transactions ADD COLUMN archived BOOLEAN NOT NULL DEFAULT false;

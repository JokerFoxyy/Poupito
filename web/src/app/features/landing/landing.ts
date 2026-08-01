import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface Feature {
  icon: string;
  title: string;
  description: string;
}

@Component({
  selector: 'app-landing',
  imports: [RouterLink],
  templateUrl: './landing.html',
  styleUrl: './landing.css'
})
export class Landing {
  readonly currentYear = new Date().getFullYear();

  readonly features: Feature[] = [
    { icon: '💸', title: 'Transações', description: 'Lance gastos e entradas rápido, com categorias e tags — como você já fazia na planilha, mas sem fórmula quebrando.' },
    { icon: '💳', title: 'Contas e cartões', description: 'Contas e cartões de crédito separados de verdade, com fatura calculada automaticamente pelo dia de fechamento.' },
    { icon: '🔁', title: 'Gastos fixos', description: 'Assinaturas e contas recorrentes materializam sozinhas todo mês — no cartão ou na conta, do jeito que você paga.' },
    { icon: '📋', title: 'Orçamentos', description: 'Defina um limite por categoria e acompanhe o quanto já gastou no mês, com alerta quando estourar.' },
    { icon: '📊', title: 'Dashboard', description: 'Panorama do mês e do ano: saldo, entradas e gastos por categoria, tudo num só lugar.' },
    { icon: '📈', title: 'Investimentos e metas', description: 'Acompanhe a evolução do seu patrimônio contra o CDI e defina metas com aporte mensal calculado.' }
  ];
}

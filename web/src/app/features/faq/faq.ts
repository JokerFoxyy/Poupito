import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface QuestionAnswer {
  question: string;
  answer: string;
}

@Component({
  selector: 'app-faq',
  imports: [RouterLink],
  templateUrl: './faq.html',
  styleUrl: './faq.css'
})
export class Faq {
  readonly items: QuestionAnswer[] = [
    {
      question: 'O Poupito é gratuito?',
      answer: 'Sim, hoje o app é de uso pessoal e gratuito.'
    },
    {
      question: 'Meus dados financeiros ficam seguros?',
      answer: 'Sim. A senha nunca é enviada nem guardada em texto puro, a sessão usa cookies protegidos, e você pode exportar ou apagar todos os seus dados a qualquer momento na área de privacidade (LGPD), dentro de Configurações.'
    },
    {
      question: 'Dá pra importar minha planilha atual?',
      answer: 'Sim — em Importar você sobe o arquivo, revisa o que foi reconhecido (contas, categorias, cartões) e confirma antes de gravar. Nada é importado sem sua confirmação.'
    },
    {
      question: 'Funciona no celular?',
      answer: 'Sim, o layout se adapta a telas pequenas e o app pode ser instalado como um aplicativo (PWA) direto do navegador.'
    },
    {
      question: 'Qual a diferença entre conta e cartão de crédito?',
      answer: 'Conta é onde o dinheiro fica (conta corrente, dinheiro em espécie); cartão é uma forma de pagamento vinculada a uma conta, com fatura calculada automaticamente pelo dia de fechamento — você lança a compra no cartão, e o Poupito organiza a fatura sozinho.'
    },
    {
      question: 'O que acontece com um gasto fixo (assinatura) todo mês?',
      answer: 'O lançamento daquele mês é gerado automaticamente (ou manualmente, se preferir) — na conta ou direto na fatura do cartão, dependendo de como você cadastrou o fixo.'
    }
  ];
}

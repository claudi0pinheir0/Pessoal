# Ações B3 — app Android

App Android (Kotlin + Jetpack Compose) para acompanhar ações da bolsa brasileira.

## Funcionalidades

- **Minha lista**: ações favoritas com preço e variação do dia (toque e segure para remover).
- **Busca** por ticker ou nome da empresa (ex.: `PETR4`, `Itaú`, `TAEE11`).
- **Cotação** com gráfico interativo (toque e arraste) nos períodos
  **1D** (intradiário), **1S**, **1M**, **6M**, **1A**, **5A** e **10A**, além de resumo
  do período (abertura, máxima, mínima, variação, volume, máx./mín. 52 semanas).
- **Fundamentos**: perfil da empresa e indicadores agrupados — toque em qualquer um para ver a definição:
  - *Valuation*: D.Y., P/L, PEG Ratio, P/VP, EV/EBITDA, EV/EBIT, P/EBITDA, P/EBIT, P/Ativo, PSR,
    P/Capital de giro, P/Ativo circ. líquido, LPA, VPA
  - *Endividamento*: Dív. líquida/PL, Dív. líquida/EBITDA, Dív. líquida/EBIT, PL/Ativos, Passivos/Ativos, Liquidez corrente
  - *Eficiência*: margens bruta, EBITDA, EBIT e líquida
  - *Rentabilidade*: ROE, ROA, ROIC, Giro de ativos
  - *Crescimento*: CAGR de receitas e de lucros (até 5 anos)
  - *Proventos*: proventos por ação (12m), Payout
  - *Dados de mercado e balanço*: valor de mercado, EV, nº de ações, free float, beta, volume médio,
    receita, EBITDA, lucro, patrimônio, ativo, dívida bruta, caixa, dívida líquida
- **Notícias** em português sobre o ticker (Google Notícias); toque para abrir no navegador.

## Fontes de dados

Sem necessidade de chave de API nem servidor próprio — o app consulta diretamente:

| Dado | Fonte |
|------|-------|
| Cotações e histórico | Yahoo Finance (`/v8/finance/chart`) — atraso de até 15 min |
| Demonstrações financeiras | Yahoo Finance (`fundamentals-timeseries`) |
| Perfil, beta, free float | Yahoo Finance (`quoteSummary`, opcional) |
| Notícias | RSS do Google Notícias (pt-BR) |

Os indicadores são **calculados pelo app** a partir dos dados contábeis (resultado dos últimos 12 meses
e último balanço trimestral) e podem divergir levemente de sites como Status Invest ou Fundamentus.
São APIs públicas não oficiais: podem mudar sem aviso. Não é recomendação de investimento.

## Estrutura

```
AcoesB3/
├── core/   Kotlin puro: clientes HTTP, parsers, cálculo dos indicadores, formatação pt-BR (+ testes)
└── app/    Android: telas em Jetpack Compose, ViewModels, cache e lista de favoritos
```

## Como obter o APK

O workflow `.github/workflows/android-acoes-b3.yml` roda os testes e gera o APK a cada push.
Em **GitHub → Actions → "App Android - Ações B3"** abra a execução mais recente e baixe o artefato
`acoes-b3-apk`. No celular, permita a instalação de apps de fontes desconhecidas e abra o `.apk`.

## Desenvolvimento local

Requer JDK 17+ e Android SDK (ou Android Studio — basta abrir a pasta `AcoesB3`).

```bash
./gradlew :app:assembleDebug        # gera app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug              # instala no aparelho/emulador conectado
./gradlew -PcoreOnly :core:test     # testa só a lógica, sem precisar do Android SDK
```

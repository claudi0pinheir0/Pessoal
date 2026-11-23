# Ações B3 — app iPhone

Versão iOS (SwiftUI, iOS 16+) do app para acompanhar ações da bolsa brasileira.
Mesmas funcionalidades e mesmas fontes de dados da versão Android (`../AcoesB3`).

## Funcionalidades

- **Minha lista** de ações com preço e variação do dia (deslize para remover, puxe para atualizar,
  "Editar" para reordenar).
- **Busca** por ticker ou empresa (ex.: `PETR4`, `Itaú`, `TAEE11`).
- **Cotação**: gráfico interativo (toque e arraste) em **1D**, **1S**, **1M**, **6M**, **1A**, **5A**, **10A**
  e resumo do período.
- **Fundamentos**: perfil da empresa e ~45 indicadores (valuation, endividamento, eficiência,
  rentabilidade, crescimento, proventos, dados de mercado/balanço). Toque para ver a definição.
- **Notícias** em português sobre o ticker (Google Notícias).

## Estrutura

```
AcoesB3-iOS/
├── AcoesCore/            Pacote Swift: clientes HTTP, parsers, indicadores, formatação pt-BR (+ testes)
├── AcoesB3/              Telas SwiftUI (Swift Charts para o gráfico)
└── AcoesB3.xcodeproj     Projeto Xcode (Xcode 16 ou superior)
```

## Como instalar no iPhone

A Apple só permite instalar apps assinados. Há três caminhos:

### 1. Com um Mac (recomendado)
1. Instale o **Xcode 16+** (App Store) e abra `AcoesB3-iOS/AcoesB3.xcodeproj`.
2. Selecione o alvo **AcoesB3 → Signing & Capabilities**, marque *Automatically manage signing*
   e escolha seu **Team** (basta entrar com seu Apple ID em Xcode → Settings → Accounts).
3. Se aparecer erro de identificador, troque o *Bundle Identifier* (ex.: `br.com.seunome.acoesb3`).
4. Conecte o iPhone por cabo, ative o **Modo de Desenvolvedor** (Ajustes → Privacidade e Segurança)
   e clique em **Run (⌘R)**.
5. No iPhone: Ajustes → Geral → VPN e Gerenciamento de Dispositivo → confie no seu certificado.

Com Apple ID gratuito o app funciona por **7 dias** (depois basta rodar de novo pelo Xcode).
Com a conta paga do Apple Developer Program (US$ 99/ano) ele dura 1 ano e pode ser distribuído via TestFlight.

### 2. Sem Mac (Windows)
O workflow do GitHub Actions gera um **IPA sem assinatura** (artefato `acoes-b3-ipa-sem-assinatura`
em *Actions → "App iPhone - Ações B3"*). Ferramentas como **Sideloadly** ou **AltStore** assinam esse IPA
com o seu Apple ID e instalam no iPhone pelo cabo (mesma regra dos 7 dias).

### 3. Simulador
No Mac, escolha um simulador de iPhone no Xcode e clique em Run.

## Testes

```bash
swift test --package-path AcoesCore
```

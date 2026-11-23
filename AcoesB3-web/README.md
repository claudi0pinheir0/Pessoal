# Ações B3 — versão web (funciona no iPhone)

Mesmo app das versões Android/iOS, como **app web instalável (PWA)**: abre no Safari e pode ser
adicionado à Tela de Início, abrindo em tela cheia como um app — sem Mac, sem App Store, sem conta paga.

- Minha lista (com edição), busca por ticker ou empresa
- Cotação com gráfico de toque e arraste: **1D, 1S, 1M, 6M, 1A, 5A, 10A** + resumo do período
- ~45 indicadores fundamentalistas (toque para ver a definição) e perfil da empresa
- Notícias em português (Google Notícias)
- Tema claro/escuro automático

## Publicar pelo iPhone (Vercel, grátis)

O navegador não pode consultar o Yahoo Finance e o Google Notícias diretamente; por isso o app tem
uma pequena API (pasta `api/`) que roda de graça na Vercel.

1. O código precisa estar na branch principal do repositório: no app do GitHub (ou github.com),
   abra o pull request desta branch e toque em **Merge**.
2. No Safari, acesse **vercel.com** → *Sign Up / Log in* → **Continue with GitHub**.
3. Toque em **Add New… → Project**, escolha o repositório **Pessoal** → **Import**.
4. Em **Root Directory** toque em *Edit* e selecione **`AcoesB3-web`**. Deixe *Framework Preset* como **Other**.
5. Toque em **Deploy**. Em ~1 minuto aparece o endereço (ex.: `https://pessoal-xyz.vercel.app`).
6. Abra o endereço no **Safari** → botão **Compartilhar** → **Adicionar à Tela de Início**.

A cada novo push na branch principal a Vercel publica a atualização sozinha.

## Rodar no computador

Requer Node.js 18+ (sem dependências para instalar).

```bash
npm start          # http://localhost:3000 com dados reais
npm run dev        # dados simulados, funciona offline
npm test           # testes
```

## Estrutura

```
AcoesB3-web/
├── index.html, style.css, app.js, manifest.webmanifest, icons/   Interface (PWA)
├── api/        Funções da API: quote, chart, fundamentals, news, search
├── lib/        Clientes Yahoo/Google Notícias, cálculo dos indicadores, formatação pt-BR
├── server.js   Servidor local (mesmas funções da API)
└── test/       Testes (node:test) e dados simulados
```

Fontes: Yahoo Finance (cotações com atraso de até 15 min e demonstrações financeiras) e Google Notícias.
Os indicadores são calculados pelo app e podem divergir de outros sites. Não é recomendação de investimento.

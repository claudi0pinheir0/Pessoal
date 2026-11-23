// Calcula os indicadores fundamentalistas a partir das demonstrações financeiras,
// do preço atual e dos proventos.
// Resultado: últimos 12 meses (trailing) quando disponível; senão, último ano.
// Balanço: último trimestre disponível; senão, último ano.

const INCOME = ["TotalRevenue", "GrossProfit", "EBIT", "EBITDA", "NormalizedEBITDA", "OperatingIncome",
  "NetIncomeCommonStockholders", "NetIncome", "PretaxIncome", "TaxProvision", "ReconciledDepreciation"];
const BALANCE = ["TotalAssets", "CurrentAssets", "CurrentLiabilities", "TotalLiabilitiesNetMinorityInterest",
  "StockholdersEquity", "TotalDebt", "NetDebt", "CashAndCashEquivalents",
  "CashCashEquivalentsAndShortTermInvestments", "OrdinarySharesNumber", "ShareIssued"];
const CASHFLOW = ["CashDividendsPaid", "FreeCashFlow"];

export const SERIES_TYPES = [
  ...INCOME.flatMap((i) => [`annual${i}`, `trailing${i}`]),
  ...BALANCE.flatMap((i) => [`annual${i}`, `quarterly${i}`]),
  ...CASHFLOW.flatMap((i) => [`annual${i}`, `trailing${i}`]),
];

const DEFAULT_TAX = 0.34;
const YEAR = 365 * 86400;

const isNum = (v) => typeof v === "number" && Number.isFinite(v);
const div = (a, b) => (isNum(a) && isNum(b) && b !== 0 && Number.isFinite(a / b) ? a / b : null);
const mul = (a, b) => (isNum(a) && isNum(b) ? a * b : null);
const sub = (a, b) => (isNum(a) && isNum(b) ? a - b : null);
const first = (...vals) => vals.find(isNum) ?? null;

/** CAGR entre o valor mais antigo (até 5 anos atrás) e o mais recente: {years, rate}. */
export function cagr(values) {
  const v = (values || []).slice(-6);
  if (v.length < 2) return null;
  const a = v[0][1], b = v[v.length - 1][1];
  if (!(a > 0) || !(b > 0)) return null;
  const years = v.length - 1;
  return { years, rate: Math.pow(b / a, 1 / years) - 1 };
}

export function calculate({ symbol, price, companyName, series = {}, summary, dividends = [], nowSec = Math.floor(Date.now() / 1000) }) {
  const s = summary || {};
  const notes = [];
  const latest = (type) => series[type]?.at(-1)?.[1] ?? null;
  const latestDate = (type) => series[type]?.at(-1)?.[0] ?? null;
  const income = (i) => first(latest(`trailing${i}`), latest(`annual${i}`));
  const balance = (i) => first(latest(`quarterly${i}`), latest(`annual${i}`));
  const seriesEmpty = Object.values(series).every((v) => !v.length);

  // Resultado (12 meses)
  const revenue = first(income("TotalRevenue"), s.totalRevenue);
  const grossProfit = income("GrossProfit");
  const ebit = first(income("EBIT"), income("OperatingIncome"));
  const dep = income("ReconciledDepreciation");
  const ebitda = first(income("EBITDA"), income("NormalizedEBITDA"), isNum(ebit) && isNum(dep) ? ebit + Math.abs(dep) : null, s.ebitda);
  const netIncome = first(income("NetIncomeCommonStockholders"), income("NetIncome"));
  const pretax = income("PretaxIncome");
  const tax = income("TaxProvision");

  // Balanço
  const totalAssets = balance("TotalAssets");
  const currentAssets = balance("CurrentAssets");
  const currentLiabilities = balance("CurrentLiabilities");
  const totalLiabilities = balance("TotalLiabilitiesNetMinorityInterest");
  const equity = balance("StockholdersEquity");
  const totalDebt = first(balance("TotalDebt"), s.totalDebt);
  const cash = first(balance("CashCashEquivalentsAndShortTermInvestments"), balance("CashAndCashEquivalents"), s.totalCash);
  const netDebt = isNum(totalDebt) && isNum(cash) ? totalDebt - cash : balance("NetDebt");
  const shares = first(balance("OrdinarySharesNumber"), balance("ShareIssued"), s.sharesOutstanding);
  const balanceSheetDate = latestDate("quarterlyTotalAssets") ?? latestDate("annualTotalAssets");

  // Mercado
  const marketCap = first(mul(price, shares), s.marketCap);
  const ev = isNum(marketCap) ? marketCap + (netDebt ?? 0) : null;
  const lpa = first(div(netIncome, shares), s.trailingEps);
  const vpa = first(div(equity, shares), s.bookValue);

  const sum12m = dividends.filter((d) => d.t >= nowSec - YEAR).reduce((acc, d) => acc + d.amount, 0);
  const dividends12m = sum12m > 0 ? sum12m : (s.trailingAnnualDividendRate ?? null);
  const dy = first(div(dividends12m, price), s.dividendYield);
  const pl = div(price, lpa);

  // Crescimento
  const revenueCagr = cagr(series.annualTotalRevenue);
  const profitCagr = cagr(series.annualNetIncomeCommonStockholders?.length ? series.annualNetIncomeCommonStockholders : series.annualNetIncome);
  const peg = pl > 0 && profitCagr?.rate > 0 ? pl / (profitCagr.rate * 100) : null;

  // Rentabilidade
  const effective = pretax > 0 ? div(tax, pretax) : null;
  const taxRate = isNum(effective) ? Math.min(Math.max(effective, 0), DEFAULT_TAX) : DEFAULT_TAX;
  const nopat = isNum(ebit) ? ebit * (1 - taxRate) : null;
  const investedCapital = isNum(equity) ? equity + (netDebt ?? 0) : null;

  const annualNet = first(latest("annualNetIncomeCommonStockholders"), latest("annualNetIncome"));
  const paid = latest("annualCashDividendsPaid");
  const payout = annualNet > 0 && isNum(paid) ? div(Math.abs(paid), annualNet) : null;

  let freeFloat = div(s.floatShares, shares);
  if (freeFloat > 1) freeFloat = null;

  if (seriesEmpty) notes.push("Demonstrações financeiras indisponíveis para este ativo; alguns indicadores não puderam ser calculados.");
  if (grossProfit == null && ebit == null && revenue != null) {
    notes.push("Instituições financeiras não divulgam lucro bruto/EBIT; indicadores baseados nessas linhas não se aplicam.");
  }

  const I = (name, value, format, description) => ({ name, value: isNum(value) ? value : null, format, description });
  const cagrLabel = (base, c) => `${base} (${c?.years ?? 5} anos)`;

  const groups = [
    { title: "Valuation", indicators: [
      I("Dividend Yield (D.Y.)", dy, "percent", "Proventos pagos por ação nos últimos 12 meses divididos pelo preço atual da ação."),
      I("P/L", pl, "multiple", "Preço / Lucro por ação. Quantos anos de lucro atual seriam necessários para pagar o preço da ação."),
      I("PEG Ratio", peg, "multiple", "P/L dividido pelo crescimento anual (CAGR) do lucro. Abaixo de 1 pode indicar ação barata em relação ao crescimento."),
      I("P/VP", div(price, vpa), "multiple", "Preço / Valor patrimonial por ação. Quanto o mercado paga por cada R$ 1 de patrimônio líquido."),
      I("EV/EBITDA", div(ev, ebitda), "multiple", "Valor da firma (valor de mercado + dívida líquida) dividido pelo EBITDA dos últimos 12 meses."),
      I("EV/EBIT", div(ev, ebit), "multiple", "Valor da firma dividido pelo EBIT (lucro operacional) dos últimos 12 meses."),
      I("P/EBITDA", div(marketCap, ebitda), "multiple", "Valor de mercado dividido pelo EBITDA."),
      I("P/EBIT", div(marketCap, ebit), "multiple", "Valor de mercado dividido pelo EBIT."),
      I("P/Ativo", div(marketCap, totalAssets), "multiple", "Valor de mercado dividido pelo ativo total."),
      I("P/SR (PSR)", div(marketCap, revenue), "multiple", "Price Sales Ratio: valor de mercado dividido pela receita líquida dos últimos 12 meses."),
      I("P/Capital de giro", div(marketCap, sub(currentAssets, currentLiabilities)), "multiple", "Valor de mercado dividido pelo capital de giro (ativo circulante − passivo circulante)."),
      I("P/Ativo circ. líquido", div(marketCap, sub(currentAssets, totalLiabilities)), "multiple", "Valor de mercado dividido pelo ativo circulante líquido (ativo circulante − passivo total)."),
      I("LPA", lpa, "money", "Lucro por ação: lucro líquido dos últimos 12 meses dividido pelo número de ações."),
      I("VPA", vpa, "money", "Valor patrimonial por ação: patrimônio líquido dividido pelo número de ações."),
    ] },
    { title: "Endividamento", indicators: [
      I("Dív. líquida/PL", div(netDebt, equity), "multiple", "Dívida bruta menos caixa, dividida pelo patrimônio líquido."),
      I("Dív. líquida/EBITDA", div(netDebt, ebitda), "multiple", "Quantos anos de EBITDA seriam necessários para quitar a dívida líquida."),
      I("Dív. líquida/EBIT", div(netDebt, ebit), "multiple", "Dívida líquida dividida pelo EBIT."),
      I("PL/Ativos", div(equity, totalAssets), "multiple", "Patrimônio líquido dividido pelo ativo total: parcela dos ativos financiada por capital próprio."),
      I("Passivos/Ativos", div(totalLiabilities, totalAssets), "multiple", "Passivo total dividido pelo ativo total: parcela dos ativos financiada por terceiros."),
      I("Liquidez corrente", first(div(currentAssets, currentLiabilities), s.currentRatio), "multiple", "Ativo circulante dividido pelo passivo circulante. Acima de 1 indica folga para pagar obrigações de curto prazo."),
    ] },
    { title: "Eficiência", indicators: [
      I("Margem bruta", first(div(grossProfit, revenue), s.grossMargins), "percent", "Lucro bruto dividido pela receita líquida."),
      I("Margem EBITDA", first(div(ebitda, revenue), s.ebitdaMargins), "percent", "EBITDA dividido pela receita líquida."),
      I("Margem EBIT", first(div(ebit, revenue), s.operatingMargins), "percent", "EBIT (lucro operacional) dividido pela receita líquida."),
      I("Margem líquida", first(div(netIncome, revenue), s.profitMargins), "percent", "Lucro líquido dividido pela receita líquida."),
    ] },
    { title: "Rentabilidade", indicators: [
      I("ROE", first(div(netIncome, equity), s.returnOnEquity), "percent", "Retorno sobre o patrimônio líquido: lucro líquido / patrimônio líquido."),
      I("ROA", first(div(netIncome, totalAssets), s.returnOnAssets), "percent", "Retorno sobre os ativos: lucro líquido / ativo total."),
      I("ROIC", div(nopat, investedCapital), "percent", "Retorno sobre o capital investido: EBIT após impostos / (patrimônio líquido + dívida líquida)."),
      I("Giro de ativos", div(revenue, totalAssets), "multiple", "Receita líquida dividida pelo ativo total: eficiência no uso dos ativos para gerar receita."),
    ] },
    { title: "Crescimento", indicators: [
      I(cagrLabel("CAGR receitas", revenueCagr), revenueCagr?.rate, "percent", "Taxa de crescimento anual composta da receita líquida nos últimos anos disponíveis (até 5)."),
      I(cagrLabel("CAGR lucros", profitCagr), profitCagr?.rate, "percent", "Taxa de crescimento anual composta do lucro líquido nos últimos anos disponíveis (até 5)."),
    ] },
    { title: "Proventos", indicators: [
      I("Proventos por ação (12m)", dividends12m, "money", "Soma dos dividendos e JCP por ação com data-com nos últimos 12 meses."),
      I("Payout", payout, "percent", "Percentual do lucro líquido do último exercício distribuído aos acionistas."),
    ] },
    { title: "Dados de mercado e balanço", indicators: [
      I("Valor de mercado", marketCap, "moneyLarge", "Preço da ação multiplicado pelo número total de ações."),
      I("Valor da firma (EV)", ev, "moneyLarge", "Valor de mercado somado à dívida líquida."),
      I("Nº total de ações", shares, "numberLarge", "Quantidade total de ações emitidas pela empresa."),
      I("Free float", freeFloat, "percent", "Percentual das ações em livre circulação no mercado."),
      I("Beta", s.beta, "decimal", "Sensibilidade da ação às oscilações do mercado. Acima de 1: mais volátil que o mercado."),
      I("Mín. 52 semanas", s.fiftyTwoWeekLow, "money", "Menor cotação nas últimas 52 semanas."),
      I("Máx. 52 semanas", s.fiftyTwoWeekHigh, "money", "Maior cotação nas últimas 52 semanas."),
      I("Volume médio (3 meses)", s.averageVolume, "numberLarge", "Média de ações negociadas por dia nos últimos 3 meses."),
      I("Receita líquida (12m)", revenue, "moneyLarge", "Receita líquida dos últimos 12 meses."),
      I("EBITDA (12m)", ebitda, "moneyLarge", "Lucro antes de juros, impostos, depreciação e amortização (últimos 12 meses)."),
      I("Lucro líquido (12m)", netIncome, "moneyLarge", "Lucro líquido atribuído aos acionistas nos últimos 12 meses."),
      I("Patrimônio líquido", equity, "moneyLarge", "Patrimônio líquido no último balanço."),
      I("Ativo total", totalAssets, "moneyLarge", "Ativo total no último balanço."),
      I("Dívida bruta", totalDebt, "moneyLarge", "Empréstimos e financiamentos de curto e longo prazo."),
      I("Caixa e aplicações", cash, "moneyLarge", "Caixa, equivalentes de caixa e aplicações financeiras de curto prazo."),
      I("Dívida líquida", netDebt, "moneyLarge", "Dívida bruta menos caixa e aplicações. Negativa indica caixa líquido."),
    ] },
  ];

  return {
    symbol,
    profile: {
      name: s.longName || companyName,
      sector: s.sector ?? null, industry: s.industry ?? null, website: s.website ?? null,
      employees: s.employees ?? null, summary: s.businessSummary ?? null,
    },
    groups,
    balanceSheetDate,
    notes,
  };
}

import AcoesCore
import SwiftUI

@MainActor
final class StockModel: ObservableObject {
    let ticker: String
    @Published var quote: LoadState<Quote> = .loading
    @Published private(set) var range: ChartRange = .dia
    @Published var chart: LoadState<ChartData> = .loading
    @Published var fundamentals: LoadState<Fundamentals> = .loading
    @Published var news: LoadState<[NewsItem]> = .loading

    private let repo = StockRepository.shared
    private var chartTask: Task<Void, Never>?

    init(ticker: String) { self.ticker = Tickers.b3Code(ticker) }

    func refresh(force: Bool) async {
        loadChart(force: force)
        async let fundamentalsDone: Void = loadFundamentals(force: force)
        await loadQuote(force: force)
        // O nome da empresa melhora a busca de notícias.
        await loadNews(force: force)
        await fundamentalsDone
    }

    func setRange(_ r: ChartRange) {
        guard r != range else { return }
        range = r
        loadChart(force: false)
    }

    func loadChart(force: Bool) {
        chartTask?.cancel()
        chart = .loading
        let r = range
        chartTask = Task {
            do {
                let c = try await repo.chart(ticker, range: r, force: force)
                if !Task.isCancelled { chart = .loaded(c) }
            } catch {
                if !Task.isCancelled && !isCancellation(error) { chart = .failed(userMessage(error)) }
            }
        }
    }

    func loadQuote(force: Bool) async {
        if quote.value == nil { quote = .loading }
        do { quote = .loaded(try await repo.quote(ticker, force: force)) } catch {
            if !isCancellation(error) { quote = .failed(userMessage(error)) }
        }
    }

    func loadFundamentals(force: Bool) async {
        if fundamentals.value == nil { fundamentals = .loading }
        do { fundamentals = .loaded(try await repo.fundamentals(ticker, force: force)) } catch {
            if !isCancellation(error) { fundamentals = .failed(userMessage(error)) }
        }
    }

    func loadNews(force: Bool) async {
        if news.value == nil { news = .loading }
        do { news = .loaded(try await repo.news(ticker, companyName: quote.value?.name, force: force)) } catch {
            if !isCancellation(error) { news = .failed(userMessage(error)) }
        }
    }
}

private enum StockTab: String, CaseIterable, Identifiable {
    case price = "Cotação", fundamentals = "Fundamentos", news = "Notícias"
    var id: String { rawValue }
}

struct StockView: View {
    @EnvironmentObject private var watchlist: WatchlistStore
    @StateObject private var model: StockModel
    @State private var tab: StockTab = .price

    init(ticker: String) {
        _model = StateObject(wrappedValue: StockModel(ticker: ticker))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                Picker("Seção", selection: $tab) {
                    ForEach(StockTab.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)

                switch tab {
                case .price: PriceSection(model: model)
                case .fundamentals: FundamentalsSection(model: model)
                case .news: NewsSection(model: model)
                }
            }
            .padding()
        }
        .navigationTitle(model.ticker)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button {
                    watchlist.toggle(model.ticker)
                } label: {
                    Image(systemName: watchlist.contains(model.ticker) ? "star.fill" : "star")
                }
                .accessibilityLabel(watchlist.contains(model.ticker) ? "Remover da lista" : "Adicionar à lista")
            }
        }
        .refreshable { await model.refresh(force: true) }
        .task { await model.refresh(force: false) }
    }

    @ViewBuilder
    private var header: some View {
        LoadView(state: model.quote, minHeight: 72, retry: { Task { await model.loadQuote(force: true) } }) { q in
            VStack(alignment: .leading, spacing: 4) {
                Text(q.name).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    Text(Br.money(q.price)).font(.largeTitle.bold()).monospacedDigit()
                    ChangeText(change: q.change, percent: q.changePercent)
                }
                if let t = q.marketTime {
                    Text("Atualizado em \(Br.dateTime(t)) · atraso de até 15 min")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }
}

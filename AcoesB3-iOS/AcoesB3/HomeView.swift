import AcoesCore
import SwiftUI

@MainActor
final class HomeModel: ObservableObject {
    @Published var quotes: [String: LoadState<Quote>] = [:]
    @Published var results: [SearchResult] = []

    /// Sugestões locais imediatas, complementadas pela busca online após uma pausa na digitação.
    func search(_ query: String) async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { results = []; return }
        results = Tickers.searchLocal(q)
        try? await Task.sleep(nanoseconds: 350_000_000)
        guard !Task.isCancelled else { return }
        let remote = await StockRepository.shared.search(q)
        if !Task.isCancelled { results = remote }
    }

    func loadQuotes(_ tickers: [String], force: Bool) async {
        await withTaskGroup(of: Void.self) { group in
            for t in tickers where force || quotes[t]?.value == nil {
                if quotes[t]?.value == nil { quotes[t] = .loading }
                group.addTask { await self.loadQuote(t, force: force) }
            }
        }
    }

    private func loadQuote(_ t: String, force: Bool) async {
        do {
            quotes[t] = .loaded(try await StockRepository.shared.quote(t, force: force))
        } catch {
            if !isCancellation(error) { quotes[t] = .failed(userMessage(error)) }
        }
    }
}

func isCancellation(_ error: Error) -> Bool {
    error is CancellationError || (error as? URLError)?.code == .cancelled
}

struct HomeView: View {
    @EnvironmentObject private var watchlist: WatchlistStore
    @StateObject private var model = HomeModel()
    @State private var query = ""
    @State private var path: [String] = []

    var body: some View {
        NavigationStack(path: $path) {
            List {
                if query.trimmingCharacters(in: .whitespaces).isEmpty {
                    watchlistSection
                } else {
                    searchResults
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Ações B3")
            .searchable(text: $query, prompt: "Ticker ou empresa (ex.: PETR4, Itaú)")
            .autocorrectionDisabled()
            .textInputAutocapitalization(.characters)
            .onSubmit(of: .search, submit)
            .task(id: query) { await model.search(query) }
            .task(id: watchlist.tickers) { await model.loadQuotes(watchlist.tickers, force: false) }
            .refreshable { await model.loadQuotes(watchlist.tickers, force: true) }
            .toolbar { if query.isEmpty && !watchlist.tickers.isEmpty { EditButton() } }
            .navigationDestination(for: String.self) { StockView(ticker: $0) }
        }
    }

    private var watchlistSection: some View {
        Section {
            if watchlist.tickers.isEmpty {
                Text("Sua lista está vazia. Busque uma ação e toque na estrela para acompanhá-la.")
                    .foregroundStyle(.secondary)
            }
            ForEach(watchlist.tickers, id: \.self) { t in
                NavigationLink(value: t) {
                    WatchlistRow(ticker: t, state: model.quotes[t] ?? .loading)
                }
            }
            .onDelete { watchlist.remove(at: $0) }
            .onMove { watchlist.move(from: $0, to: $1) }
        } header: {
            Text("Minha lista")
        } footer: {
            Text("Deslize para a esquerda para remover; puxe para atualizar. Cotações com atraso de até 15 minutos (fonte: Yahoo Finance). Não é recomendação de investimento.")
        }
    }

    @ViewBuilder
    private var searchResults: some View {
        if model.results.isEmpty {
            Text(Tickers.looksLikeB3Ticker(query)
                 ? "Toque em \"Buscar\" no teclado para abrir \(Tickers.b3Code(query))."
                 : "Nenhuma ação encontrada.")
                .foregroundStyle(.secondary)
        }
        ForEach(model.results) { r in
            NavigationLink(value: r.ticker) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(r.ticker).font(.headline)
                        Text(r.name).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer()
                    Button {
                        watchlist.toggle(r.ticker)
                    } label: {
                        Image(systemName: watchlist.contains(r.ticker) ? "star.fill" : "star")
                            .foregroundStyle(watchlist.contains(r.ticker) ? Color.accentGreen : .secondary)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(watchlist.contains(r.ticker) ? "Remover da lista" : "Adicionar à lista")
                }
            }
        }
    }

    private func submit() {
        let q = query.trimmingCharacters(in: .whitespaces)
        if Tickers.looksLikeB3Ticker(q) {
            path.append(Tickers.b3Code(q))
        } else if let first = model.results.first {
            path.append(first.ticker)
        }
    }
}

private struct WatchlistRow: View {
    let ticker: String
    let state: LoadState<Quote>

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(ticker).font(.headline)
                switch state {
                case .loaded(let q): Text(q.name).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                case .failed(let msg): Text(msg).font(.subheadline).foregroundStyle(.red).lineLimit(1)
                case .loading: Text("Carregando…").font(.subheadline).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if let q = state.value {
                VStack(alignment: .trailing, spacing: 2) {
                    Text(Br.money(q.price)).font(.headline).monospacedDigit()
                    ChangeText(change: q.change, percent: q.changePercent, showAbsolute: false)
                }
            } else if case .loading = state {
                ProgressView()
            }
        }
    }
}

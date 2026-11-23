import AcoesCore
import SwiftUI

struct NewsSection: View {
    @ObservedObject var model: StockModel

    var body: some View {
        LoadView(state: model.news, minHeight: 300, retry: { Task { await model.loadNews(force: true) } }) { items in
            VStack(alignment: .leading, spacing: 0) {
                if items.isEmpty {
                    ErrorView(message: "Nenhuma notícia encontrada para \(model.ticker).",
                              retry: { Task { await model.loadNews(force: true) } })
                }
                ForEach(items) { n in
                    if let url = URL(string: n.link) {
                        Link(destination: url) { NewsRow(item: n) }
                            .buttonStyle(.plain)
                        Divider()
                    }
                }
                Text("Fonte: Google Notícias. Toque em uma notícia para abri-la no navegador.")
                    .font(.caption).foregroundStyle(.secondary).padding(.top, 12)
            }
        }
    }
}

private struct NewsRow: View {
    let item: NewsItem

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(item.title).font(.body.weight(.medium)).multilineTextAlignment(.leading)
            Text([item.source, item.publishedEpochSec.map { Br.relative($0) }].compactMap { $0 }.joined(separator: " · "))
                .font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

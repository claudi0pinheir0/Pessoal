import AcoesCore
import SwiftUI

struct FundamentalsSection: View {
    @ObservedObject var model: StockModel
    @State private var info: Indicator?

    var body: some View {
        LoadView(state: model.fundamentals, minHeight: 300, retry: { Task { await model.loadFundamentals(force: true) } }) { f in
            VStack(alignment: .leading, spacing: 8) {
                CompanyCard(profile: f.profile)
                ForEach(f.notes, id: \.self) { note in
                    Text(note).font(.caption).foregroundStyle(.secondary)
                }
                ForEach(f.groups) { group in
                    SectionHeader(title: group.title)
                    LazyVGrid(columns: twoColumns, spacing: 8) {
                        ForEach(group.indicators) { ind in
                            Button { info = ind } label: {
                                StatCell(label: ind.name, value: Br.indicator(ind.value, ind.format))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                Text(footer(f)).font(.caption).foregroundStyle(.secondary).padding(.top, 12)
            }
        }
        .sheet(item: $info) { ind in
            VStack(alignment: .leading, spacing: 12) {
                Text(ind.name).font(.title2.bold())
                Text(Br.indicator(ind.value, ind.format)).font(.largeTitle.bold()).monospacedDigit()
                Text(ind.description).font(.body)
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(24)
            .presentationDetents([.medium])
            .presentationDragIndicator(.visible)
        }
    }

    private func footer(_ f: Fundamentals) -> String {
        var s = "Toque em um indicador para ver sua definição. Resultados: últimos 12 meses. "
        if let d = f.balanceSheetDate {
            s += "Balanço de referência: \(d.split(separator: "-").reversed().joined(separator: "/")). "
        }
        s += "Fonte: Yahoo Finance (demonstrações financeiras) — valores calculados pelo app e podem divergir de outras fontes."
        return s
    }
}

private struct CompanyCard: View {
    let profile: CompanyProfile
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(profile.name).font(.headline)
            HStack(alignment: .top, spacing: 16) {
                if let s = profile.sector { labeled("Setor", s) }
                if let i = profile.industry { labeled("Segmento", i) }
                if let e = profile.employees { labeled("Funcionários", Br.number(Double(e), digits: 0)) }
            }
            if let site = profile.website, let url = URL(string: site) {
                Link(site, destination: url).font(.subheadline)
            }
            if let summary = profile.summary {
                Text(summary)
                    .font(.footnote)
                    .lineLimit(expanded ? nil : 4)
                Button(expanded ? "Mostrar menos" : "Ler mais") { expanded.toggle() }
                    .font(.footnote.weight(.semibold))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    private func labeled(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption2).foregroundStyle(.secondary)
            Text(value).font(.subheadline)
        }
    }
}

import AcoesCore
import SwiftUI

struct LoadingView: View {
    var minHeight: CGFloat = 160
    var body: some View {
        ProgressView().frame(maxWidth: .infinity, minHeight: minHeight)
    }
}

struct ErrorView: View {
    let message: String
    var retry: (() -> Void)?

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle").font(.title2).foregroundStyle(.orange)
            Text(message).multilineTextAlignment(.center).foregroundStyle(.secondary)
            if let retry { Button("Tentar novamente", action: retry).buttonStyle(.bordered) }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
    }
}

/// Renderiza um LoadState delegando o conteúdo carregado.
struct LoadView<T, Content: View>: View {
    let state: LoadState<T>
    var minHeight: CGFloat = 160
    let retry: () -> Void
    @ViewBuilder let content: (T) -> Content

    var body: some View {
        switch state {
        case .loading: LoadingView(minHeight: minHeight)
        case .failed(let msg): ErrorView(message: msg, retry: retry)
        case .loaded(let v): content(v)
        }
    }
}

/// "+1,23 (+0,45%)" colorido conforme alta/baixa.
struct ChangeText: View {
    let change: Double?
    let percent: Double?
    var showAbsolute = true

    var body: some View {
        Text(text)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Color.market(percent ?? change))
            .monospacedDigit()
    }

    private var text: String {
        if showAbsolute, let c = change {
            return (c > 0 ? "+" : "") + Br.number(c) + " (" + Br.percent(percent, signed: true) + ")"
        }
        return Br.percent(percent, signed: true)
    }
}

struct StatCell: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            Text(value).font(.headline).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 10))
    }
}

struct SectionHeader: View {
    let title: String
    var body: some View {
        Text(title)
            .font(.headline)
            .foregroundStyle(Color.accentGreen)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 16)
    }
}

let twoColumns = [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)]

import AcoesCore
import Charts
import SwiftUI

struct PriceSection: View {
    @ObservedObject var model: StockModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(ChartRange.allCases) { r in
                        Button(r.label) { model.setRange(r) }
                            .font(.subheadline.weight(.semibold))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 6)
                            .background(r == model.range ? Color.accentGreen : Color(.secondarySystemBackground), in: Capsule())
                            .foregroundStyle(r == model.range ? Color.white : Color.primary)
                            .accessibilityAddTraits(r == model.range ? .isSelected : [])
                    }
                }
            }
            LoadView(state: model.chart, minHeight: 300, retry: { model.loadChart(force: true) }) { data in
                ChartDetails(data: data, range: model.range)
            }
        }
    }
}

private struct ChartDetails: View {
    let data: ChartData
    let range: ChartRange
    @State private var selected: Int?

    var body: some View {
        let points = data.points
        if points.count < 2 {
            ErrorView(message: "Sem dados de cotação para este período.")
        } else {
            let base = data.chartPreviousClose ?? points[0].close
            let last = points[points.count - 1].close
            let pct: Double? = base != 0 ? last / base - 1 : nil
            let color = Color.market(last - base)

            VStack(alignment: .leading, spacing: 8) {
                infoLine(points: points, base: base, change: last - base, pct: pct)
                    .frame(height: 44, alignment: .topLeading)

                PriceChartView(points: points, baseline: range == .dia ? base : nil, color: color, selected: $selected)
                    .frame(height: 260)

                HStack {
                    ForEach(Array(Set([0, (points.count - 1) / 2, points.count - 1])).sorted(), id: \.self) { i in
                        Text(axisLabel(points[i].epochSec))
                        if i != points.count - 1 { Spacer() }
                    }
                }
                .font(.caption2)
                .foregroundStyle(.secondary)
                .padding(.trailing, 44)

                if range == .dia {
                    Text("Linha tracejada: fechamento anterior (\(Br.money(base)))")
                        .font(.caption2).foregroundStyle(.secondary)
                }

                SectionHeader(title: "Resumo do período")
                LazyVGrid(columns: twoColumns, spacing: 8) {
                    StatCell(label: "Abertura", value: Br.money(points[0].open ?? points[0].close))
                    StatCell(label: "Último", value: Br.money(last))
                    StatCell(label: "Máxima", value: Br.money(points.map { $0.high ?? $0.close }.max()))
                    StatCell(label: "Mínima", value: Br.money(points.map { $0.low ?? $0.close }.min()))
                    StatCell(label: "Variação", value: Br.percent(pct, signed: true))
                    StatCell(label: "Volume negociado", value: volumeText(points))
                    StatCell(label: "Máx. 52 semanas", value: Br.money(data.fiftyTwoWeekHigh))
                    StatCell(label: "Mín. 52 semanas", value: Br.money(data.fiftyTwoWeekLow))
                }
                Text("Toque e arraste no gráfico para ver o preço em cada data.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder
    private func infoLine(points: [PricePoint], base: Double, change: Double, pct: Double?) -> some View {
        if let i = selected, i < points.count {
            let p = points[i]
            VStack(alignment: .leading, spacing: 2) {
                Text(Br.money(p.close)).font(.title3.weight(.semibold)).monospacedDigit()
                Text((range.isIntraday ? Br.dateTime(p.epochSec) : Br.date(p.epochSec)) + "  ·  "
                     + Br.percent(base != 0 ? p.close / base - 1 : nil, signed: true) + " no período")
                    .font(.caption).foregroundStyle(.secondary)
            }
        } else {
            VStack(alignment: .leading, spacing: 2) {
                Text(range.description).font(.subheadline.weight(.semibold))
                ChangeText(change: change, percent: pct)
            }
        }
    }

    private func axisLabel(_ epoch: Int64) -> String {
        switch range {
        case .dia: return Br.time(epoch)
        case .semana, .mes, .seisMeses, .ano: return Br.shortDate(epoch)
        case .cincoAnos, .dezAnos: return Br.monthYear(epoch)
        }
    }

    private func volumeText(_ points: [PricePoint]) -> String {
        let total = points.compactMap(\.volume).reduce(0, +)
        return total > 0 ? Br.compact(Double(total)) : Br.empty
    }
}

/// Gráfico de linha com área, eixo de preços à direita, referência tracejada e seleção por arraste.
struct PriceChartView: View {
    let points: [PricePoint]
    let baseline: Double?
    let color: Color
    @Binding var selected: Int?

    var body: some View {
        let closes = points.map(\.close)
        let lo = min(closes.min() ?? 0, baseline ?? .infinity)
        let hi = max(closes.max() ?? 1, baseline ?? -.infinity)
        let pad = max((hi - lo) * 0.06, 0.01)
        let floor = lo - pad

        Chart {
            ForEach(points.indices, id: \.self) { i in
                AreaMark(
                    x: .value("Índice", Double(i)),
                    yStart: .value("Base", floor),
                    yEnd: .value("Preço", closes[i])
                )
                .foregroundStyle(LinearGradient(colors: [color.opacity(0.3), color.opacity(0.02)],
                                                startPoint: .top, endPoint: .bottom))
                LineMark(x: .value("Índice", Double(i)), y: .value("Preço", closes[i]))
                    .foregroundStyle(color)
                    .lineStyle(StrokeStyle(lineWidth: 2, lineJoin: .round))
            }
            if let b = baseline {
                RuleMark(y: .value("Fechamento anterior", b))
                    .foregroundStyle(Color.secondary)
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [5, 4]))
            }
            if let s = selected, s < points.count {
                RuleMark(x: .value("Índice", Double(s)))
                    .foregroundStyle(Color.secondary)
                PointMark(x: .value("Índice", Double(s)), y: .value("Preço", closes[s]))
                    .foregroundStyle(color)
                    .symbolSize(80)
            }
        }
        .chartXScale(domain: 0...Double(max(points.count - 1, 1)))
        .chartYScale(domain: floor...(hi + pad))
        .chartXAxis(.hidden)
        .chartYAxis {
            AxisMarks(position: .trailing, values: .automatic(desiredCount: 4)) { value in
                AxisGridLine()
                AxisValueLabel {
                    if let d = value.as(Double.self) { Text(Br.number(d)) }
                }
            }
        }
        .chartOverlay { proxy in
            GeometryReader { geo in
                Rectangle()
                    .fill(Color.clear)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { g in
                                let origin = geo[proxy.plotAreaFrame].origin
                                if let x: Double = proxy.value(atX: g.location.x - origin.x) {
                                    selected = min(max(Int(x.rounded()), 0), points.count - 1)
                                }
                            }
                            .onEnded { _ in selected = nil }
                    )
            }
        }
        .accessibilityLabel("Gráfico de cotação")
    }
}

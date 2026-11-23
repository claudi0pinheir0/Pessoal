import SwiftUI

@main
struct AcoesB3App: App {
    @StateObject private var watchlist = WatchlistStore()

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(watchlist)
                .tint(.accentGreen)
        }
    }
}

extension Color {
    static let accentGreen = Color(red: 0.04, green: 0.43, blue: 0.31)
    /// Alta / baixa com contraste nos temas claro e escuro.
    static let marketUp = Color(uiColor: UIColor { $0.userInterfaceStyle == .dark
        ? UIColor(red: 0.29, green: 0.87, blue: 0.50, alpha: 1)
        : UIColor(red: 0.08, green: 0.50, blue: 0.24, alpha: 1) })
    static let marketDown = Color(uiColor: UIColor { $0.userInterfaceStyle == .dark
        ? UIColor(red: 0.97, green: 0.44, blue: 0.44, alpha: 1)
        : UIColor(red: 0.73, green: 0.11, blue: 0.11, alpha: 1) })

    static func market(_ change: Double?) -> Color {
        guard let c = change, c != 0 else { return .secondary }
        return c > 0 ? .marketUp : .marketDown
    }
}

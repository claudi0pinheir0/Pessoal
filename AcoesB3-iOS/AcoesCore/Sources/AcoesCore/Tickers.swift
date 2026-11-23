import Foundation

public enum Tickers {
    /// "petr4" -> "PETR4.SA"; símbolos com sufixo (ou índices como ^BVSP) são mantidos.
    public static func yahooSymbol(_ input: String) -> String {
        let t = input.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if t.hasPrefix("^") || t.contains(".") { return t }
        return t + ".SA"
    }

    /// "PETR4.SA" -> "PETR4"
    public static func b3Code(_ symbol: String) -> String {
        let t = symbol.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return t.hasSuffix(".SA") ? String(t.dropLast(3)) : t
    }

    public static func looksLikeB3Ticker(_ input: String) -> Bool {
        b3Code(input).range(of: "^[A-Z0-9]{4}[0-9]{1,2}[A-Z]?$", options: .regularExpression) != nil
    }

    static func normalize(_ s: String) -> String {
        s.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pt_BR")).lowercased()
    }

    public static func searchLocal(_ query: String, limit: Int = 15) -> [SearchResult] {
        let q = normalize(query.trimmingCharacters(in: .whitespacesAndNewlines))
        guard !q.isEmpty else { return [] }
        return popular
            .map { SearchResult(ticker: $0.0, name: $0.1) }
            .filter { normalize($0.ticker).contains(q) || normalize($0.name).contains(q) }
            .sorted { a, b in
                let ap = normalize(a.ticker).hasPrefix(q), bp = normalize(b.ticker).hasPrefix(q)
                return ap != bp ? ap : a.ticker < b.ticker
            }
            .prefix(limit)
            .map { $0 }
    }

    /// Ações mais negociadas da B3 (sugestões rápidas e offline).
    public static let popular: [(String, String)] = [
        ("PETR4", "Petrobras PN"), ("PETR3", "Petrobras ON"), ("VALE3", "Vale ON"),
        ("ITUB4", "Itaú Unibanco PN"), ("ITUB3", "Itaú Unibanco ON"), ("BBDC4", "Bradesco PN"),
        ("BBDC3", "Bradesco ON"), ("BBAS3", "Banco do Brasil ON"), ("SANB11", "Santander Brasil Unit"),
        ("BPAC11", "BTG Pactual Unit"), ("B3SA3", "B3 ON"), ("ITSA4", "Itaúsa PN"),
        ("ABEV3", "Ambev ON"), ("WEGE3", "WEG ON"), ("RENT3", "Localiza ON"),
        ("SUZB3", "Suzano ON"), ("GGBR4", "Gerdau PN"), ("GOAU4", "Metalúrgica Gerdau PN"),
        ("CSNA3", "CSN ON"), ("USIM5", "Usiminas PNA"), ("CMIN3", "CSN Mineração ON"),
        ("PRIO3", "PetroRio ON"), ("RECV3", "PetroReconcavo ON"), ("BRAV3", "Brava Energia ON"),
        ("UGPA3", "Ultrapar ON"), ("VBBR3", "Vibra Energia ON"), ("CSAN3", "Cosan ON"),
        ("RAIZ4", "Raízen PN"), ("ELET3", "Eletrobras ON"), ("ELET6", "Eletrobras PNB"),
        ("NEOE3", "Neoenergia ON"), ("EQTL3", "Equatorial ON"), ("CPFE3", "CPFL Energia ON"),
        ("CMIG4", "Cemig PN"), ("CMIG3", "Cemig ON"), ("EGIE3", "Engie Brasil ON"),
        ("TAEE11", "Taesa Unit"), ("TRPL4", "ISA CTEEP PN"), ("ENGI11", "Energisa Unit"),
        ("CPLE6", "Copel PNB"), ("CPLE3", "Copel ON"), ("AURE3", "Auren Energia ON"),
        ("SBSP3", "Sabesp ON"), ("CSMG3", "Copasa ON"), ("SAPR11", "Sanepar Unit"),
        ("ALUP11", "Alupar Unit"), ("ENEV3", "Eneva ON"), ("MGLU3", "Magazine Luiza ON"),
        ("LREN3", "Lojas Renner ON"), ("ASAI3", "Assaí ON"), ("CRFB3", "Carrefour Brasil ON"),
        ("PCAR3", "GPA ON"), ("NTCO3", "Natura ON"), ("RADL3", "Raia Drogasil ON"),
        ("HYPE3", "Hypera ON"), ("RDOR3", "Rede D'Or ON"), ("HAPV3", "Hapvida ON"),
        ("FLRY3", "Fleury ON"), ("QUAL3", "Qualicorp ON"), ("BBSE3", "BB Seguridade ON"),
        ("CXSE3", "Caixa Seguridade ON"), ("PSSA3", "Porto Seguro ON"), ("IRBR3", "IRB Re ON"),
        ("BRAP4", "Bradespar PN"), ("KLBN11", "Klabin Unit"), ("JBSS3", "JBS ON"),
        ("BRFS3", "BRF ON"), ("MRFG3", "Marfrig ON"), ("BEEF3", "Minerva ON"),
        ("SLCE3", "SLC Agrícola ON"), ("SMTO3", "São Martinho ON"), ("EMBR3", "Embraer ON"),
        ("AZUL4", "Azul PN"), ("GOLL4", "Gol PN"), ("CCRO3", "CCR ON"),
        ("MOTV3", "Motiva ON"), ("ECOR3", "Ecorodovias ON"), ("RAIL3", "Rumo ON"),
        ("STBP3", "Santos Brasil ON"), ("TIMS3", "TIM ON"), ("VIVT3", "Telefônica Brasil ON"),
        ("TOTS3", "Totvs ON"), ("LWSA3", "Locaweb ON"), ("CYRE3", "Cyrela ON"),
        ("MRVE3", "MRV ON"), ("EZTC3", "EZTec ON"), ("DIRR3", "Direcional ON"),
        ("CURY3", "Cury ON"), ("MULT3", "Multiplan ON"), ("ALOS3", "Allos ON"),
        ("IGTI11", "Iguatemi Unit"), ("YDUQ3", "Yduqs ON"), ("COGN3", "Cogna ON"),
        ("SMFT3", "Smart Fit ON"), ("VAMO3", "Vamos ON"), ("MOVI3", "Movida ON"),
        ("POMO4", "Marcopolo PN"), ("RAPT4", "Randon PN"), ("TUPY3", "Tupy ON"),
        ("LEVE3", "Mahle Metal Leve ON"), ("KEPL3", "Kepler Weber ON"), ("ARZZ3", "Arezzo ON"),
        ("AZZA3", "Azzas 2154 ON"), ("VIVA3", "Vivara ON"), ("SOMA3", "Grupo Soma ON"),
        ("PETZ3", "Petz ON"), ("CASH3", "Méliuz ON"), ("BPAN4", "Banco Pan PN"),
        ("BRSR6", "Banrisul PNB"), ("ABCB4", "ABC Brasil PN"), ("BMGB4", "Banco BMG PN"),
        ("INTB3", "Intelbras ON"), ("POSI3", "Positivo ON"), ("DXCO3", "Dexco ON"),
        ("UNIP6", "Unipar PNB"), ("BRKM5", "Braskem PNA"), ("FESA4", "Ferbasa PN"),
        ("GRND3", "Grendene ON"), ("ODPV3", "Odontoprev ON"), ("SIMH3", "Simpar ON"),
        ("AMER3", "Americanas ON"), ("VIIA3", "Casas Bahia ON"), ("BHIA3", "Grupo Casas Bahia ON"),
        ("CVCB3", "CVC ON"), ("ORVR3", "Orizon ON"), ("PGMN3", "Pague Menos ON"),
        ("SBFG3", "Grupo SBF ON"), ("MDIA3", "M. Dias Branco ON"), ("CAML3", "Camil ON"),
        ("TTEN3", "3tentos ON"), ("AGRO3", "BrasilAgro ON"), ("JHSF3", "JHSF ON"),
        ("EVEN3", "Even ON"), ("TEND3", "Tenda ON"), ("PLPL3", "Plano & Plano ON"),
        ("ISAE4", "ISA Energia PN"),
    ]
}

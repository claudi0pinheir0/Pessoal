package br.com.neo.acoes.core.data

import br.com.neo.acoes.core.model.SearchResult
import java.text.Normalizer
import java.util.Locale

object Tickers {
    private val b3Pattern = Regex("^[A-Z0-9]{4}\\d{1,2}[A-Z]?$")

    /** "petr4" -> "PETR4.SA"; símbolos que já possuem sufixo (ou índices como ^BVSP) são mantidos. */
    fun toYahooSymbol(input: String): String {
        val t = input.trim().uppercase(Locale.ROOT)
        return when {
            t.startsWith("^") || t.contains('.') -> t
            else -> "$t.SA"
        }
    }

    /** "PETR4.SA" -> "PETR4" */
    fun toB3Code(symbol: String): String = symbol.trim().uppercase(Locale.ROOT).removeSuffix(".SA")

    fun looksLikeB3Ticker(input: String): Boolean = b3Pattern.matches(toB3Code(input))

    private fun normalize(s: String) =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    fun searchLocal(query: String, limit: Int = 15): List<SearchResult> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return emptyList()
        return POPULAR
            .map { (code, name) -> SearchResult(code, name) }
            .filter { normalize(it.ticker).contains(q) || normalize(it.name).contains(q) }
            .sortedWith(compareBy({ !normalize(it.ticker).startsWith(q) }, { it.ticker }))
            .take(limit)
    }

    /** Ações mais negociadas da B3 (usadas para sugestões rápidas e offline). */
    val POPULAR: List<Pair<String, String>> = listOf(
        "PETR4" to "Petrobras PN", "PETR3" to "Petrobras ON", "VALE3" to "Vale ON",
        "ITUB4" to "Itaú Unibanco PN", "ITUB3" to "Itaú Unibanco ON", "BBDC4" to "Bradesco PN",
        "BBDC3" to "Bradesco ON", "BBAS3" to "Banco do Brasil ON", "SANB11" to "Santander Brasil Unit",
        "BPAC11" to "BTG Pactual Unit", "B3SA3" to "B3 ON", "ITSA4" to "Itaúsa PN",
        "ABEV3" to "Ambev ON", "WEGE3" to "WEG ON", "RENT3" to "Localiza ON",
        "SUZB3" to "Suzano ON", "GGBR4" to "Gerdau PN", "GOAU4" to "Metalúrgica Gerdau PN",
        "CSNA3" to "CSN ON", "USIM5" to "Usiminas PNA", "CMIN3" to "CSN Mineração ON",
        "PRIO3" to "PetroRio ON", "RECV3" to "PetroReconcavo ON", "BRAV3" to "Brava Energia ON",
        "UGPA3" to "Ultrapar ON", "VBBR3" to "Vibra Energia ON", "CSAN3" to "Cosan ON",
        "RAIZ4" to "Raízen PN", "ELET3" to "Eletrobras ON", "ELET6" to "Eletrobras PNB",
        "NEOE3" to "Neoenergia ON", "EQTL3" to "Equatorial ON", "CPFE3" to "CPFL Energia ON",
        "CMIG4" to "Cemig PN", "CMIG3" to "Cemig ON", "EGIE3" to "Engie Brasil ON",
        "TAEE11" to "Taesa Unit", "TRPL4" to "ISA CTEEP PN", "ENGI11" to "Energisa Unit",
        "CPLE6" to "Copel PNB", "CPLE3" to "Copel ON", "AURE3" to "Auren Energia ON",
        "SBSP3" to "Sabesp ON", "CSMG3" to "Copasa ON", "SAPR11" to "Sanepar Unit",
        "ALUP11" to "Alupar Unit", "ENEV3" to "Eneva ON", "MGLU3" to "Magazine Luiza ON",
        "LREN3" to "Lojas Renner ON", "ASAI3" to "Assaí ON", "CRFB3" to "Carrefour Brasil ON",
        "PCAR3" to "GPA ON", "NTCO3" to "Natura ON", "RADL3" to "Raia Drogasil ON",
        "HYPE3" to "Hypera ON", "RDOR3" to "Rede D'Or ON", "HAPV3" to "Hapvida ON",
        "FLRY3" to "Fleury ON", "QUAL3" to "Qualicorp ON", "BBSE3" to "BB Seguridade ON",
        "CXSE3" to "Caixa Seguridade ON", "PSSA3" to "Porto Seguro ON", "IRBR3" to "IRB Re ON",
        "BRAP4" to "Bradespar PN", "KLBN11" to "Klabin Unit", "JBSS3" to "JBS ON",
        "BRFS3" to "BRF ON", "MRFG3" to "Marfrig ON", "BEEF3" to "Minerva ON",
        "SLCE3" to "SLC Agrícola ON", "SMTO3" to "São Martinho ON", "EMBR3" to "Embraer ON",
        "AZUL4" to "Azul PN", "GOLL4" to "Gol PN", "CCRO3" to "CCR ON", "MOTV3" to "Motiva ON",
        "ECOR3" to "Ecorodovias ON", "RAIL3" to "Rumo ON", "STBP3" to "Santos Brasil ON",
        "TIMS3" to "TIM ON", "VIVT3" to "Telefônica Brasil ON", "TOTS3" to "Totvs ON",
        "LWSA3" to "Locaweb ON", "CYRE3" to "Cyrela ON", "MRVE3" to "MRV ON",
        "EZTC3" to "EZTec ON", "DIRR3" to "Direcional ON", "CURY3" to "Cury ON",
        "MULT3" to "Multiplan ON", "ALOS3" to "Allos ON", "IGTI11" to "Iguatemi Unit",
        "YDUQ3" to "Yduqs ON", "COGN3" to "Cogna ON", "SMFT3" to "Smart Fit ON",
        "VAMO3" to "Vamos ON", "MOVI3" to "Movida ON", "POMO4" to "Marcopolo PN",
        "RAPT4" to "Randon PN", "TUPY3" to "Tupy ON", "LEVE3" to "Mahle Metal Leve ON",
        "KEPL3" to "Kepler Weber ON", "ARZZ3" to "Arezzo ON", "AZZA3" to "Azzas 2154 ON",
        "VIVA3" to "Vivara ON", "SOMA3" to "Grupo Soma ON", "PETZ3" to "Petz ON",
        "CASH3" to "Méliuz ON", "BPAN4" to "Banco Pan PN", "BRSR6" to "Banrisul PNB",
        "ABCB4" to "ABC Brasil PN", "BMGB4" to "Banco BMG PN", "INTB3" to "Intelbras ON",
        "POSI3" to "Positivo ON", "DXCO3" to "Dexco ON", "UNIP6" to "Unipar PNB",
        "BRKM5" to "Braskem PNA", "FESA4" to "Ferbasa PN", "GRND3" to "Grendene ON",
        "ODPV3" to "Odontoprev ON", "SIMH3" to "Simpar ON", "AMER3" to "Americanas ON",
        "VIIA3" to "Casas Bahia ON", "BHIA3" to "Grupo Casas Bahia ON", "CVCB3" to "CVC ON",
        "ORVR3" to "Orizon ON", "PGMN3" to "Pague Menos ON", "SBFG3" to "Grupo SBF ON",
        "MDIA3" to "M. Dias Branco ON", "CAML3" to "Camil ON", "TTEN3" to "3tentos ON",
        "AGRO3" to "BrasilAgro ON", "JHSF3" to "JHSF ON", "EVEN3" to "Even ON",
        "TEND3" to "Tenda ON", "PLPL3" to "Plano & Plano ON", "ISAE4" to "ISA Energia PN",
    )
}

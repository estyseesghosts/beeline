package me.foxtails.palustris.domain

/**
 * The writing system of a post, as far as its characters prove one. The font of the post follows
 * this value, so one post renders in one font.
 */
enum class PostScript {
    Japanese,
    Cantonese,
    TraditionalChinese,
    SimplifiedChinese,
}

/**
 * Classifies text by the characters that only one writing system uses. Stateless and pure.
 *
 * The order of the rules is the order of proof. Kana and Japan-only punctuation prove Japanese,
 * also beside Chinese characters. A Cantonese-only character proves Cantonese. A character that
 * simplified Chinese replaced proves traditional Chinese. A character that only simplified
 * Chinese has proves simplified Chinese. Text that holds only characters common to every system,
 * or no such character, has no class. Such text keeps the font of the application.
 *
 * The tables hold frequent characters only. They are evidence, not a dictionary.
 */
object PostScriptClassifier {
    fun classify(text: String): PostScript? {
        var cantonese = false
        var traditional = false
        var simplified = false
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            // ASCII and Latin-1 never decide. Skip them before any lookup.
            if (codePoint < FIRST_DECIDING_CODE_POINT) continue
            if (isJapanese(codePoint)) return PostScript.Japanese
            if (codePoint > Char.MAX_VALUE.code) continue
            val char = codePoint.toChar()
            when (char) {
                in CANTONESE_ONLY -> cantonese = true
                in TRADITIONAL_ONLY -> traditional = true
                in SIMPLIFIED_ONLY -> simplified = true
            }
        }
        return when {
            cantonese -> PostScript.Cantonese
            traditional -> PostScript.TraditionalChinese
            simplified -> PostScript.SimplifiedChinese
            else -> null
        }
    }

    private fun isJapanese(codePoint: Int): Boolean {
        if (codePoint <= Char.MAX_VALUE.code && codePoint.toChar() in JAPANESE_ONLY_PUNCTUATION) return true
        val script = Character.UnicodeScript.of(codePoint)
        return script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA
    }

    private const val FIRST_DECIDING_CODE_POINT = 0x2E80

    /**
     * Punctuation that only Japanese uses. The ideographic comma, the full stop, and the corner
     * brackets are absent, because Chinese uses them too. The katakana middle dot is absent,
     * because Chinese uses it in foreign names.
     */
    private val JAPANESE_ONLY_PUNCTUATION: Set<Char> = "ー〜〆〒〽ｰ".toSet()

    /** Characters that written Cantonese uses and standard Chinese does not. */
    private val CANTONESE_ONLY: Set<Char> =
        "嘅咗喺啲嚟冇佢哋嘢咁噉嗰唔啱喎囉㗎嚿攞瞓搵睇諗冚啩嗱氹唞嬲曱甴乸孭揸劏嘥攰".toSet()

    /** Traditional forms that simplified Chinese replaced. */
    private val TRADITIONAL_ONLY: Set<Char> = (
        "這們說時會對過還發樣國現讓問給話從兩長開見後來個裡麼為與學實點經關頭無愛聽覺邊東車書電機門馬語讀寫買賣錢飛風魚鳥龍貓歲樂業應當體總種處變幾氣進運動達選連難題類親觀" +
            "許認識論請謝該計記設試誰課調議護質報場塊聲單嗎團圖圓園壓夢夠寶層島帶幫廣張強彈態戰戲換數斷條楊極樓標歡殺決沒漢滿準灣燈熱獨獎環產畫療盡確禮離稱窮節簡紅級細終結絕" +
            "統絲綠維網緊線練繼續罵義習聯聖職腦臉興舉舊華萬葉藝藥號術衛裝複規視訊訴詞詩誤講證譯讚貝負財責貴資賽贏趕跡軍軟輕輪轉辦農遠適遲遺鄉醫釋針鋼錄鐵鐘閃閉間陣陽隊階際" +
            "雖雙雜雞雲靈靜響頁頂順須預領願顯飯飲養館驗驚髮鬥鮮麗麥黃齊齒"
        ).toSet()

    /**
     * Simplified forms that neither traditional Chinese nor Japanese uses. A simplified form that
     * is also a traditional or a Japanese character (for example 国, 会, 学, 来, 后, 里) is absent,
     * because it proves nothing.
     */
    private val SIMPLIFIED_ONLY: Set<Char> = (
        "这们说时对过还发样现让问给话两长开见为实经关头爱觉边东车书电门马语读买卖钱飞风鱼鸟龙岁乐业应总处变进运动达选连难题类亲观" +
            "许认识论请谢该计记设试谁课调议护质报场块单吗团图圆园压够层岛带帮张强弹态战戏换杨标欢杀汉满热奖环产疗穷节简红级细终结绝" +
            "统丝绿维紧线练继续骂义习联职脑脸兴举华艺药术卫规视讯诉词诗误讲证译赞贝负财责贵资赛赢军软轻轮转办农远迟遗乡释针钢录铁钟闪闭间阵阳队阶际" +
            "虽杂鸡灵响页顶顺须预领显饭饮养馆验鲜丽齐齿"
        ).toSet()
}

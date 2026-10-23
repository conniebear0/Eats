package com.eats.app.support.bot

import kotlin.math.abs
import kotlin.math.min

/**
 * Lowercases, drops apostrophes, turns everything that isn't a letter or digit into a space, then
 * maps common synonyms and slang onto one word ("delivery guy" -> "driver", "my kos" -> "my food").
 */
object TextNormaliser {
    private val apostrophes = Regex("['’`]")
    private val nonWord = Regex("[^a-z0-9]+")
    private val synonyms = listOf(
        Regex("\\b(delivery guy|delivery man|delivery lady|delivery person|the rider|rider|courier)\\b") to "driver",
        Regex("\\b(my stuff|my kos|die kos|my meal|my ukudla)\\b") to "my food",
        Regex("\\b(cooldrink|cool drink|cold drink|soft drink|soda)\\b") to "drink",
        Regex("\\b(u)\\b") to "you",
        Regex("\\b(ur)\\b") to "your",
        Regex("\\b(pls|plz|plse)\\b") to "please",
        Regex("\\b(cant|cannot)\\b") to "cant",
        Regex("\\b(wont)\\b") to "wont",
        Regex("\\b(r)\\s+(\\d)") to "r\$2",
    )

    fun normalise(text: String): String = synonyms.fold(
        text.lowercase().replace(apostrophes, "").replace(nonWord, " ").trim(),
    ) { t, (re, w) -> t.replace(re, w) }

    fun tokens(text: String): List<String> = normalise(text).split(' ').filter { it.isNotEmpty() }
}

/** Typo-tolerant word matching: "refnud" matches "refund", "delivred" matches "delivered". */
object TokenMatcher {
    private const val MIN_FUZZY = 5
    private const val LONG_WORD = 8

    /** Real words that sit one letter away from a keyword and must not be "corrected" into it. */
    private val neverCorrected = LexiconData.neverCorrected

    private fun stem(w: String) = if (w.length >= 4 && w.endsWith("s")) w.dropLast(1) else w

    fun matches(input: String, keyword: String): Boolean {
        if (input == keyword) return true
        if (stem(input) == stem(keyword)) return true
        if (keyword.length < MIN_FUZZY || input.length < 4) return false
        if (input in neverCorrected) return false
        val allowed = if (keyword.length >= LONG_WORD) 2 else 1
        return levenshtein(input, keyword, allowed) <= allowed
    }

    fun levenshtein(a: String, b: String, limit: Int): Int {
        if (abs(a.length - b.length) > limit) return limit + 1
        var beforePrevious = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
                // A swap of two neighbouring letters ("refnud") counts as one typo.
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    current[j] = min(current[j], beforePrevious[j - 2] + 1)
                }
                rowMin = min(rowMin, current[j])
            }
            if (rowMin > limit) return limit + 1
            val swap = beforePrevious; beforePrevious = previous; previous = current; current = swap
        }
        return previous[b.length]
    }
}

data class Phrase(val text: String, val weight: Double) {
    val tokens: List<String> = TextNormaliser.tokens(text)
}

data class Classification(
    val intent: Intent,
    val confidence: Double,
    val matched: String? = null,
    val source: ReplySource = ReplySource.Rules,
    /** Set when the second-opinion model chose this, not a phrase. */
    val p: Double? = null,
    val margin: Double? = null,
)

/**
 * Phrases for each intent, from [LexiconData] (shared with the browser build). STRONG phrases decide on
 * their own; KEYWORDs are single telling words; WEAK words only count when nothing better matches.
 * Words in a phrase may have up to two other words between them ("never got my food" matches
 * "never got food").
 */
object Lexicon {
    private val weights = mapOf("strong" to 1.0, "keyword" to 0.8, "weak" to 0.6)

    val phrases: List<Pair<Intent, List<Phrase>>> = LexiconData.phrases.map { it.split('|') }
        .groupBy({ Intent.valueOf(it[0]) }, { Phrase(it[2], weights.getValue(it[1])) })
        .toList()

    /** Every button label also works when typed ("Where's my order?"). */
    val all: List<Pair<Intent, List<Phrase>>> = phrases.map { (intent, list) -> intent to (list + Phrase(intent.label, 1.0)) } +
        Intent.entries.filter { i -> i != Intent.Unknown && phrases.none { it.first == i } }.map { it to listOf(Phrase(it.label, 1.0)) }

    /** When two intents score the same, the more specific phrase wins, then the one earlier in this list. */
    val tieBreak: List<Intent> = LexiconData.tieBreak.map { Intent.valueOf(it) }

    /** Words that show the customer is upset. Two upset messages in a row bring in a person. */
    val frustrationWords: Set<String> = LexiconData.frustration
}

/**
 * Second opinion: a small naive Bayes model trained on every phrase and example. It is only asked
 * when the phrase rules are unsure, and only answers when it is clearly confident.
 */
object NaiveBayes {
    data class Guess(val intent: Intent, val p: Double, val margin: Double, val tokens: Int)

    private val stop = setOf(
        "the", "a", "an", "my", "is", "it", "to", "of", "and", "i", "im", "me", "you", "your", "for", "on", "in", "at", "this",
        "that", "with", "please", "can", "do", "be", "so", "just", "its", "are", "was", "were", "have", "has", "had", "will", "would",
        "could", "from", "there", "about",
    )
    private const val ALPHA = 0.3

    private fun stem(w: String): String = when {
        w.length > 5 && w.endsWith("ing") -> w.dropLast(3)
        w.length > 4 && w.endsWith("ed") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    private fun tokens(text: String) = TextNormaliser.tokens(text).filter { it !in stop }.map(::stem)

    private val counts = LinkedHashMap<Intent, MutableMap<String, Double>>()
    private val totals = HashMap<Intent, Double>()
    private val vocab = LinkedHashSet<String>()

    private fun train(intent: Intent, text: String, weight: Double) {
        val c = counts.getOrPut(intent) { totals[intent] = 0.0; HashMap() }
        for (t in tokens(text)) {
            c[t] = (c[t] ?: 0.0) + weight
            totals[intent] = totals.getValue(intent) + weight
            vocab += t
        }
    }

    init {
        for (line in LexiconData.phrases) {
            val (intent, kind, text) = line.split('|')
            train(Intent.valueOf(intent), text, if (kind == "weak") 0.5 else 1.0)
        }
        for (line in LexiconData.examples) {
            val i = line.indexOf('|')
            train(Intent.valueOf(line.substring(0, i)), line.substring(i + 1), 1.5)
        }
        for (intent in Intent.entries) if (intent != Intent.Unknown) train(intent, intent.label, 1.0)
    }

    private fun known(t: String): String? = if (t in vocab) t else vocab.firstOrNull { it.length >= 5 && TokenMatcher.matches(t, it) }

    fun predict(text: String): Guess? {
        val toks = tokens(text).mapNotNull(::known)
        if (toks.isEmpty()) return null
        val v = vocab.size
        val scores = counts.map { (intent, c) ->
            val total = totals.getValue(intent)
            var lp = 0.0
            for (t in toks) lp += kotlin.math.ln(((c[t] ?: 0.0) + ALPHA) / (total + ALPHA * v))
            intent to lp
        }.sortedByDescending { it.second }
        val max = scores[0].second
        val z = scores.sumOf { kotlin.math.exp(it.second - max) }
        val p1 = 1 / z
        val p2 = kotlin.math.exp(scores[1].second - max) / z
        return Guess(scores[0].first, p1, p1 / maxOf(p2, 1e-9), toks.size)
    }
}

/**
 * What the bot has learned on this phone. When it misunderstands and the customer shows what they
 * meant (taps the right button, corrects it, or the AI helper works it out and the customer is
 * happy), the wording is saved here and recognised straight away next time.
 */
class LearningMemory(entries: List<Learned> = emptyList()) {

    /** Who taught the bot: the customer (by correcting it) or the AI helper (confirmed by the customer). */
    enum class Source { Customer, Ai }

    data class Learned(val phrase: String, val intent: Intent, val hits: Int, val source: Source, val learnedAt: Long)

    private val learned = entries.associateBy { it.phrase }.toMutableMap()

    /** How many times the bot got something wrong and was corrected (shown on "What I've learned"). */
    var mistakes: Int = 0

    val all: List<Learned> get() = learned.values.sortedByDescending { it.learnedAt }

    fun learn(text: String, intent: Intent, source: Source, now: Long) {
        val key = key(text) ?: return
        if (intent == Intent.Unknown || intent == Intent.Correction) return
        val existing = learned[key]
        learned[key] = if (existing != null && existing.intent == intent) {
            existing.copy(hits = existing.hits + 1, learnedAt = now)
        } else {
            Learned(key, intent, 1, source, now)
        }
    }

    fun forget(text: String): Boolean = key(text)?.let { learned.remove(it) != null } ?: false

    fun clear() {
        learned.clear()
        mistakes = 0
    }

    /** An exact match is trusted; a close match (most words shared) is used when the rules found nothing. */
    fun lookup(text: String): Classification? {
        val key = key(text) ?: return null
        learned[key]?.let { return Classification(it.intent, EXACT, it.phrase, ReplySource.Learned) }
        val words = contentWords(key)
        if (words.size < 2) return null
        var best: Learned? = null
        var bestScore = 0.0
        for (entry in all) {
            val other = contentWords(entry.phrase)
            if (other.isEmpty()) continue
            val shared = words.count { w -> other.any { TokenMatcher.matches(w, it) } }
            val score = shared.toDouble() / maxOf(words.size, other.size)
            if (score > bestScore) {
                bestScore = score
                best = entry
            }
        }
        val match = best ?: return null
        return if (bestScore >= SIMILAR) Classification(match.intent, CLOSE, match.phrase, ReplySource.Learned) else null
    }

    private fun key(text: String): String? = TextNormaliser.normalise(text).takeIf { it.length in 2..MAX_LENGTH }

    private fun contentWords(text: String) = text.split(' ').filter { it.length >= 3 && it !in stopWords }

    companion object {
        const val EXACT = 0.95
        const val CLOSE = 0.7
        const val SIMILAR = 0.6
        const val MAX_LENGTH = 160
        private val stopWords = LexiconData.stopWords
    }
}

/** Scores a message against the lexicon, then against what the bot has learned. */
class IntentClassifier(private val memory: LearningMemory = LearningMemory()) {

    /** The model's guess when the phrase rules are unsure; null unless it is clearly confident. */
    fun guess(text: String): Classification? {
        val g = NaiveBayes.predict(text) ?: return null
        if (g.intent == Intent.Unknown || g.intent == Intent.Correction) return null
        val sure = (g.p >= 0.6 && g.margin >= 2.5) || (g.p >= 0.35 && g.margin >= 6)
        return if (sure) Classification(g.intent, MODEL, null, ReplySource.Rules, g.p, g.margin) else null
    }

    fun classify(text: String): Classification {
        val tokens = TextNormaliser.tokens(text)
        if (tokens.isEmpty()) return Classification(Intent.Unknown, 0.0)

        // Something the customer has taught the bot wins over the built-in phrases.
        val learned = memory.lookup(text)
        if (learned != null && learned.confidence >= LearningMemory.EXACT) return learned

        val winner = score(tokens).firstOrNull()
        if (learned != null && (winner == null || winner.confidence < STRONG)) return learned
        val weakSocial = winner != null && winner.confidence < STRONG &&
            winner.intent in setOf(Intent.Greeting, Intent.Ack, Intent.Goodbye, Intent.Thanks) && tokens.size > 2
        val g = guess(text)
        // One keyword is weak evidence: a very confident model reading wins over it.
        if (winner != null && winner.confidence < 1 && g != null && g.intent != winner.intent && g.p!! >= 0.85 && g.margin!! >= 10) return g
        if (winner != null && winner.confidence >= STRONG) return winner
        if (g != null && (winner == null || weakSocial || winner.confidence < STRONG)) return g
        if (winner != null && winner.confidence >= THRESHOLD) return winner
        return Classification(Intent.Unknown, winner?.confidence ?: 0.0)
    }

    /** All intents that reached the threshold, best first (used when one message holds two things). */
    fun ranked(text: String): List<Classification> = score(TextNormaliser.tokens(text)).filter { it.confidence >= THRESHOLD }

    /** Best first: higher score, then the longer (more specific) phrase, then the tie-break list. */
    fun score(tokens: List<String>): List<Classification> {
        if (tokens.isEmpty()) return emptyList()
        return Lexicon.all.mapNotNull { (intent, phrases) ->
            val matched = phrases.filter { containsInOrder(tokens, it.tokens, 0, 0) }
            if (matched.isEmpty()) return@mapNotNull null
            val best = matched.sortedWith(compareByDescending<Phrase> { it.weight }.thenByDescending { it.tokens.size }).first()
            Classification(intent, min(1.0, best.weight + (matched.size - 1) * BONUS), best.text)
        }.sortedWith(
            compareByDescending<Classification> { it.confidence }
                .thenByDescending { TextNormaliser.tokens(it.matched ?: "").size }
                .thenBy { Lexicon.tieBreak.indexOf(it.intent).let { i -> if (i < 0) Int.MAX_VALUE else i } },
        )
    }

    /** True when the message says the bot got it wrong ("that's not what I meant"). */
    fun isCorrection(text: String): Boolean = ranked(text).any { it.intent == Intent.Correction }

    fun isFrustrated(text: String): Boolean = TextNormaliser.tokens(text).any { it in Lexicon.frustrationWords } ||
        angryEmoji.containsMatchIn(text) || maskedSwearing.containsMatchIn(text) || itsAJoke.containsMatchIn(text) ||
        (text.count { it == '!' } >= 3) || shouting(text)

    private fun shouting(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        return letters.length >= 12 && letters.count { it.isUpperCase() } >= letters.length * 0.8
    }

    private fun containsInOrder(tokens: List<String>, phrase: List<String>, from: Int, depth: Int): Boolean {
        if (depth == phrase.size) return true
        val limit = if (depth == 0) tokens.size else min(tokens.size, from + MAX_GAP + 1)
        for (i in from until limit) {
            if (TokenMatcher.matches(tokens[i], phrase[depth]) && containsInOrder(tokens, phrase, i + 1, depth + 1)) return true
        }
        return false
    }

    companion object {
        const val THRESHOLD = 0.5
        const val STRONG = 0.7
        const val MODEL = 0.65
        const val BONUS = 0.05
        const val MAX_GAP = 2
        private val angryEmoji = Regex("[\\x{1F620}\\x{1F621}\\x{1F92C}\\x{1F624}\\x{1F44E}\\x{1F4A9}]")
        private val maskedSwearing = Regex("\\b(f|s)[*#@!$]{2,}", RegexOption.IGNORE_CASE)
        private val itsAJoke = Regex("\\b(this is|what a|its a|it's a) (joke|mess|disgrace)\\b", RegexOption.IGNORE_CASE)
    }
}

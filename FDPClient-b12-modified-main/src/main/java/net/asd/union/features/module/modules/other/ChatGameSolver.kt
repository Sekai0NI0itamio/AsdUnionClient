package net.asd.union.features.module.modules.other

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.asd.union.config.*
import net.asd.union.event.EventState
import net.asd.union.event.PacketEvent
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.client.chat
import net.minecraft.network.play.server.S02PacketChat
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadLocalRandom
import javax.script.ScriptEngineManager
import kotlin.math.floor

object ChatGameSolver : Module("ChatGameSolver", Category.OTHER, subjective = true) {

    private val gson = Gson()

    private val solveEnabled by boolean("Solve", true)
    private val unscrambleEnabled by boolean("Unscramble", true)
    private val typeEnabled by boolean("Type", true)
    private val typeRandomEnabled by boolean("TypeRandom", true)
    private val unreverseEnabled by boolean("Unreverse", true)
    private val fillInEnabled by boolean("FillIn", true)
    private val triviaEnabled by boolean("Trivia", true)
    private val wordUnshuffleEnabled by boolean("WordUnshuffle", true)
    private val reverseWordUnshuffleEnabled by boolean("ReverseWordUnshuffle", true)

    private val solveDelay by intRange("SolveDelay", 800..1500, 0..30000, "ms")
    private val unscrambleDelay by intRange("UnscrambleDelay", 800..1500, 0..30000, "ms")
    private val typeDelay by intRange("TypeDelay", 500..1200, 0..30000, "ms")
    private val typeRandomDelay by intRange("TypeRandomDelay", 500..1200, 0..30000, "ms")
    private val unreverseDelay by intRange("UnreverseDelay", 500..1000, 0..30000, "ms")
    private val fillInDelay by intRange("FillInDelay", 800..1500, 0..30000, "ms")
    private val triviaDelay by intRange("TriviaDelay", 1000..2000, 0..30000, "ms")
    private val wordUnshuffleDelay by intRange("WordUnshuffleDelay", 800..1500, 0..30000, "ms")
    private val reverseWordUnshuffleDelay by intRange("ReverseWordUnshuffleDelay", 800..1500, 0..30000, "ms")

    private val delayCount by int("DelayCount", 1, 1..5)

    private val showAnswer by boolean("ShowAnswer", true)

    private var inGame = false
    private val buffer = StringBuilder()
    private var activeTriviaQuestion: String? = null
    private var gameStartTime = 0L
    private val gameTimeoutMs = 60_000L

    private val solvedPattern = Regex("solved .+? \\(\\d+\\) in")
    private val answeredPattern = Regex("~.*? (?:answered|unscrambled|typed|filled in|unreversed|unshuffled) '(.*?)' in")
    private val nobodyWonPattern = Regex("Nobody got it in time; the answer was '(.*?)'")

    private val jsEngine = runCatching { ScriptEngineManager().getEngineByName("JavaScript") }.getOrNull()

    private val defaultTriviaMap = mutableMapOf<String, List<String>>()
    private val learnedTriviaMap = mutableMapOf<String, MutableList<String>>()
    private val wordList = mutableListOf<String>()
    private val sortedCharCache = mutableMapOf<String, String>()

    private data class PendingAction(val message: String, val executeTime: Long)
    private val actionQueue = ConcurrentLinkedQueue<PendingAction>()

    private var dataLoaded = false

    override fun onInitialize() {
        loadData()
    }

    override fun onEnable() {
        if (!dataLoaded) loadData()
        inGame = false
        buffer.clear()
        activeTriviaQuestion = null
        actionQueue.clear()
    }

    override fun onDisable() {
        inGame = false
        buffer.clear()
        activeTriviaQuestion = null
        actionQueue.clear()
    }

    private fun loadData() {
        loadDefaultTrivia()
        loadDefaultWords()
        dataLoaded = true
    }

    private fun loadDefaultTrivia() {
        runCatching {
            val stream = javaClass.getResourceAsStream("/assets/minecraft/asdunionclient/chatgamesolver/trivia.json")
            if (stream != null) {
                val reader = InputStreamReader(stream, Charsets.UTF_8)
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val entries: List<Map<String, Any>> = gson.fromJson(reader, type)
                for (entry in entries) {
                    val q = entry["question"] as? String ?: continue
                    @Suppress("UNCHECKED_CAST")
                    val a = entry["answers"] as? List<String> ?: continue
                    defaultTriviaMap[cleanString(q)] = a
                }
                stream.close()
            }
        }.onFailure { it.printStackTrace() }
    }

    private fun loadDefaultWords() {
        runCatching {
            val stream = javaClass.getResourceAsStream("/assets/minecraft/asdunionclient/chatgamesolver/words.json")
            if (stream != null) {
                val reader = InputStreamReader(stream, Charsets.UTF_8)
                val type = object : TypeToken<List<String>>() {}.type
                val words: List<String> = gson.fromJson(reader, type)
                wordList.addAll(words)
                buildSortedCharCache()
                stream.close()
            }
        }.onFailure { it.printStackTrace() }
    }

    private fun buildSortedCharCache() {
        sortedCharCache.clear()
        for (entry in wordList) {
            val key = sortChars(entry.lowercase().replace(Regex("[^a-z]"), ""))
            sortedCharCache[entry] = key
        }
    }

    val onPacket = handler<PacketEvent> { event ->
        if (event.eventType != EventState.RECEIVE) return@handler

        val packet = event.packet as? S02PacketChat ?: return@handler
        val plainText = packet.chatComponent?.unformattedText?.trim() ?: return@handler
        if (plainText.isEmpty()) return@handler

        if (solvedPattern.containsMatchIn(plainText)) {
            inGame = false
            buffer.clear()
            activeTriviaQuestion = null
            return@handler
        }

        val winnerMatch = answeredPattern.find(plainText)
        if (winnerMatch != null) {
            val answer = winnerMatch.groupValues[1]
            activeTriviaQuestion?.let { q ->
                learnTrivia(q, answer)
                activeTriviaQuestion = null
            }
            inGame = false
            buffer.clear()
            return@handler
        }

        val nobodyMatch = nobodyWonPattern.find(plainText)
        if (nobodyMatch != null) {
            val answer = nobodyMatch.groupValues[1]
            activeTriviaQuestion?.let { q ->
                learnTrivia(q, answer)
                activeTriviaQuestion = null
            }
            inGame = false
            buffer.clear()
            return@handler
        }

        if (plainText.contains("CHAT GAME", ignoreCase = true)) {
            inGame = true
            gameStartTime = System.currentTimeMillis()
            buffer.clear()
            activeTriviaQuestion = null
            return@handler
        }

        if (inGame) {
            if (System.currentTimeMillis() - gameStartTime > gameTimeoutMs) {
                inGame = false
                buffer.clear()
                activeTriviaQuestion = null
                return@handler
            }

            buffer.append(plainText).append(" ")
            if (plainText.contains("wins!")) {
                val fullText = buffer.toString()
                inGame = false
                buffer.clear()
                handleGame(fullText)
            }
        }
    }

    val onUpdate = handler<UpdateEvent> {
        if (actionQueue.isEmpty()) return@handler
        if (mc.thePlayer == null) return@handler

        val now = System.currentTimeMillis()
        while (actionQueue.isNotEmpty() && actionQueue.peek().executeTime <= now) {
            val action = actionQueue.poll()
            mc.thePlayer.sendChatMessage(action.message)
        }
    }

    private fun handleGame(fullText: String) {
        val hasUnshuffle = fullText.contains("unshuffle")
        val hasUnreverse = fullText.contains("unreverse")
        val hasUnscramble = fullText.contains("unscramble")
        val hasFillIn = fullText.contains("fill in")
        val hasSolve = fullText.contains("solve '")
        val hasType = fullText.contains("type '")

        if (hasSolve && solveEnabled) {
            val start = fullText.indexOf("solve '") + 7
            val end = fullText.indexOf("'", start)
            if (end > start) {
                val answer = solveMath(fullText.substring(start, end).trim())
                if (answer != null) {
                    enqueueWithDelay(answer, solveDelay)
                    return
                }
            }
        }

        if (hasUnshuffle && hasUnreverse && reverseWordUnshuffleEnabled) {
            val target = extractQuoted(fullText, "unshuffle '")
                ?: extractQuoted(fullText, "unreverse '")
            if (target != null) {
                val answer = solveReverseWordUnshuffle(target)
                if (answer != null) {
                    enqueueWithDelay(answer, reverseWordUnshuffleDelay)
                    return
                }
            }
        }

        if (hasUnscramble && unscrambleEnabled) {
            val target = extractQuoted(fullText, "unscramble '")
            if (target != null) {
                val answer = solveScramble(target)
                if (answer != null) {
                    enqueueWithDelay(answer, unscrambleDelay)
                    return
                }
            }
        }

        if (hasUnshuffle && !hasUnreverse && wordUnshuffleEnabled) {
            val target = extractQuoted(fullText, "unshuffle '")
            if (target != null) {
                val answer = solveWordUnshuffle(target)
                if (answer != null) {
                    enqueueWithDelay(answer, wordUnshuffleDelay)
                    return
                }
            }
        }

        if (hasUnreverse && !hasUnshuffle && unreverseEnabled) {
            val target = extractQuoted(fullText, "unreverse '")
            if (target != null) {
                val answer = solveUnreverse(target)
                if (answer != null) {
                    enqueueWithDelay(answer, unreverseDelay)
                    return
                }
            }
        }

        if (hasFillIn && fillInEnabled) {
            val target = extractQuoted(fullText, "fill in '")
            if (target != null) {
                val answer = solveFillIn(target.replace(Regex("\\s+"), " ").trim())
                if (answer != null) {
                    enqueueWithDelay(answer, fillInDelay)
                    return
                }
            }
        }

        if (hasType) {
            val target = extractQuoted(fullText, "type '")
            if (target != null) {
                val isRandom = isRandomString(target)
                if (isRandom && typeRandomEnabled) {
                    enqueueWithDelay(target, typeRandomDelay)
                    return
                } else if (!isRandom && typeEnabled) {
                    enqueueWithDelay(target, typeDelay)
                    return
                }
            }
        }

        if (triviaEnabled) {
            var questionPart = fullText
            if (fullText.contains("(the first person")) {
                questionPart = fullText.substring(0, fullText.indexOf("(the first person")).trim()
            } else {
                val winsIdx = fullText.lastIndexOf("wins!")
                val sentenceStart = fullText.lastIndexOf(". ", winsIdx)
                questionPart = fullText.substring(0, if (sentenceStart == -1) winsIdx else sentenceStart + 2).trim()
                if (questionPart.isEmpty()) questionPart = fullText.substring(0, winsIdx).trim()
            }

            val answer = solveTrivia(questionPart)
            if (answer != null) {
                enqueueWithDelay(answer, triviaDelay)
            } else {
                activeTriviaQuestion = questionPart
            }
        }
    }

    private fun isRandomString(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.length < 4) return false
        val upperCount = letters.count { it.isUpperCase() }
        val lowerCount = letters.count { it.isLowerCase() }
        return upperCount > lowerCount && upperCount > 2
    }

    private fun extractQuoted(text: String, keyword: String): String? {
        val idx = text.indexOf(keyword)
        if (idx == -1) return null
        val start = idx + keyword.length
        val end = text.indexOf("'", start)
        return if (end > start) text.substring(start, end) else null
    }

    private fun findMatchingEntry(text: String): String? {
        val directMatch = wordList.find { it.equals(text, ignoreCase = true) }
        if (directMatch != null) return directMatch

        val sortedText = sortChars(text.lowercase().replace(Regex("[^a-z]"), ""))
        if (sortedText.isEmpty()) return null

        for (entry in wordList) {
            val sortedEntry = sortedCharCache[entry] ?: continue
            if (sortedText == sortedEntry) return entry
        }

        return null
    }

    private fun solveMath(expr: String): String? {
        val normalized = expr.replace("x", "*").replace("^", "**")
        if (!normalized.matches(Regex("[0-9+\\-*/%. ()]+"))) return null
        return runCatching {
            val result = jsEngine?.eval(normalized)
            if (result is Number) {
                val value = result.toDouble()
                if (value == floor(value)) (value.toLong()).toString() else value.toString()
            } else null
        }.getOrNull()
    }

    private fun solveScramble(scrambled: String): String? {
        val allLettersSorted = sortChars(scrambled.lowercase().replace(Regex("[^a-z]"), ""))
        if (allLettersSorted.isNotEmpty()) {
            for (entry in wordList) {
                val sortedEntry = sortedCharCache[entry] ?: continue
                if (allLettersSorted == sortedEntry) return entry
            }
        }

        val scrambledWords = scrambled.trim().split(Regex("\\s+"))
        if (scrambledWords.size > 1) {
            val perWordResult = solvePerWordScramble(scrambledWords)
            if (perWordResult != null) return perWordResult

            val multiWordEntries = wordList.filter { it.contains(" ") }
            for (entry in multiWordEntries) {
                val entryWords = entry.split(" ")
                if (entryWords.size != scrambledWords.size) continue

                val entrySortedPerWord = entryWords.map { sortChars(it.lowercase().replace(Regex("[^a-z]"), "")) }
                val scrambledSortedPerWord = scrambledWords.map { sortChars(it.lowercase().replace(Regex("[^a-z]"), "")) }

                if (entrySortedPerWord.sorted() == scrambledSortedPerWord.sorted()) {
                    val permResult = matchPermutationToEntry(scrambledWords, entryWords)
                    if (permResult != null) return permResult
                }
            }
        }

        return null
    }

    private fun solvePerWordScramble(scrambledWords: List<String>): String? {
        val solvedWords = mutableListOf<String>()
        for (scrambledWord in scrambledWords) {
            val sorted = sortChars(scrambledWord.lowercase().replace(Regex("[^a-z]"), ""))
            if (sorted.isEmpty()) return null

            val match = wordList.find { entry ->
                !entry.contains(" ") && sortedCharCache[entry] == sorted
            }
            if (match != null) {
                solvedWords.add(match)
            } else {
                return null
            }
        }
        return solvedWords.joinToString(" ")
    }

    private fun matchPermutationToEntry(scrambledWords: List<String>, entryWords: List<String>): String? {
        val scrambledSorted = scrambledWords.map { sortChars(it.lowercase().replace(Regex("[^a-z]"), "")) }
        val entrySorted = entryWords.map { sortChars(it.lowercase().replace(Regex("[^a-z]"), "")) }

        val used = mutableSetOf<Int>()
        val mapping = mutableMapOf<Int, Int>()

        for (i in scrambledSorted.indices) {
            for (j in entrySorted.indices) {
                if (j !in used && scrambledSorted[i] == entrySorted[j]) {
                    mapping[i] = j
                    used.add(j)
                    break
                }
            }
        }

        if (mapping.size == scrambledWords.size) {
            return entryWords.joinToString(" ")
        }
        return null
    }

    private fun solveUnreverse(target: String): String? {
        val reversed = target.reversed()
        val listMatch = findMatchingEntry(reversed)
        if (listMatch != null) return listMatch

        return reversed
    }

    private fun solveReverseWordUnshuffle(target: String): String? {
        val words = target.trim().split(Regex("\\s+"))
        if (words.isEmpty()) return null

        val unreversedWords = words.map { it.reversed() }

        val directPermutation = findPermutationInWordList(unreversedWords)
        if (directPermutation != null) return directPermutation

        val unscrambledWords = unreversedWords.map { word ->
            val sorted = sortChars(word.lowercase().replace(Regex("[^a-z]"), ""))
            if (sorted.isEmpty()) return@map null

            val singleWordMatch = wordList.find { entry ->
                !entry.contains(" ") && sortedCharCache[entry] == sorted
            }
            singleWordMatch ?: word
        }
        if (unscrambledWords.any { it == null }) {
            val partialPermutation = findPermutationInWordList(unreversedWords.map { it ?: it })
            if (partialPermutation != null) return partialPermutation
        } else {
            val unscrambledPermutation = findPermutationInWordList(unscrambledWords.filterNotNull())
            if (unscrambledPermutation != null) return unscrambledPermutation
        }

        val allLettersSorted = sortChars(target.lowercase().replace(Regex("[^a-z]"), ""))
        if (allLettersSorted.isNotEmpty()) {
            for (entry in wordList) {
                val sortedEntry = sortedCharCache[entry] ?: continue
                if (allLettersSorted == sortedEntry) return entry
            }
        }

        return null
    }

    private fun solveWordUnshuffle(shuffled: String): String? {
        val words = shuffled.trim().split(Regex("\\s+"))
        if (words.isEmpty()) return null

        val directPermutation = findPermutationInWordList(words)
        if (directPermutation != null) return directPermutation

        val unscrambledWords = words.map { word ->
            val sorted = sortChars(word.lowercase().replace(Regex("[^a-z]"), ""))
            if (sorted.isEmpty()) return@map word

            val singleWordMatch = wordList.find { entry ->
                !entry.contains(" ") && sortedCharCache[entry] == sorted
            }
            singleWordMatch ?: word
        }

        val unscrambledPermutation = findPermutationInWordList(unscrambledWords)
        if (unscrambledPermutation != null) return unscrambledPermutation

        val allLettersSorted = sortChars(shuffled.lowercase().replace(Regex("[^a-z]"), ""))
        if (allLettersSorted.isNotEmpty()) {
            for (entry in wordList) {
                val sortedEntry = sortedCharCache[entry] ?: continue
                if (allLettersSorted == sortedEntry) return entry
            }
        }

        return null
    }

    private fun findPermutationInWordList(words: List<String>): String? {
        if (words.size > 6) return null

        val permutations = generatePermutations(words)
        for (perm in permutations) {
            val candidate = perm.joinToString(" ")
            val match = wordList.find { it.equals(candidate, ignoreCase = true) }
            if (match != null) return match
        }
        return null
    }

    private fun generatePermutations(items: List<String>): List<List<String>> {
        if (items.size <= 1) return listOf(items)
        val result = mutableListOf<List<String>>()
        for (i in items.indices) {
            val rest = items.filterIndexed { index, _ -> index != i }
            for (perm in generatePermutations(rest)) {
                result.add(listOf(items[i]) + perm)
            }
        }
        return result
    }

    private fun solveFillIn(fillIn: String): String? {
        val regex = fillIn.lowercase().replace("_", ".").replace(Regex("[^a-z0-9.]"), "")
        val pattern = Regex("^$regex$")
        for (word in wordList) {
            val cleanWord = word.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (pattern.matches(cleanWord)) {
                return word
            }
        }
        return null
    }

    private fun solveTrivia(question: String): String? {
        val cleanQ = cleanString(question)
        val qTokens = tokenize(question)

        var bestAnswer: String? = null
        var bestScore = 0.0

        for (map in listOf(learnedTriviaMap, defaultTriviaMap)) {
            for ((key, answers) in map) {
                if (answers.isEmpty()) continue

                if (key == cleanQ) return answers[0]

                val kTokens = tokenize(key)
                var score = jaccardScore(qTokens, kTokens)

                if (cleanQ.contains(key) || key.contains(cleanQ)) {
                    score = maxOf(score, 0.6)
                }

                if (score > bestScore) {
                    bestScore = score
                    bestAnswer = answers[0]
                }
            }
        }

        return if (bestScore >= 0.4) bestAnswer else null
    }

    private fun learnTrivia(question: String, answer: String) {
        val cleanQ = cleanString(question)
        val cleanA = answer.lowercase()

        val defaults = defaultTriviaMap[cleanQ]
        if (defaults != null && defaults.contains(cleanA)) return

        val existing = learnedTriviaMap.getOrPut(cleanQ) { mutableListOf() }
        if (cleanA !in existing) {
            existing.add(cleanA)
        }
    }

    private fun tokenize(input: String): Set<String> {
        return input.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in STOP_WORDS }
            .toSet()
    }

    private fun jaccardScore(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b)
        val union = a.union(b)
        return intersection.size.toDouble() / union.size
    }

    private fun sortChars(input: String): String {
        val chars = input.toCharArray()
        chars.sort()
        return String(chars)
    }

    private fun cleanString(input: String): String {
        return input.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun enqueueWithDelay(answer: String, delayRange: IntRange) {
        val random = ThreadLocalRandom.current()
        val delay = random.nextLong(delayRange.first.toLong(), (delayRange.last + 1).toLong())

        if (showAnswer) {
            chat("§a§lChatGameSolver §7» §fAnswer: §e$answer §7(in ${delay}ms)")
        }

        for (i in 0 until delayCount) {
            val actionDelay = if (i == 0) delay else delay + random.nextLong(200, 800)
            actionQueue.add(PendingAction(answer, System.currentTimeMillis() + actionDelay))
        }
    }

    private val STOP_WORDS = setOf(
        "the", "and", "for", "are", "was", "what", "which", "who", "how", "when",
        "where", "that", "this", "with", "from", "have", "has", "had", "its",
        "first", "known", "called", "name", "named"
    )
}

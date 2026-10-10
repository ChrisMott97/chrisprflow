package app.dictation

/** Puts dictated text into a field's text at the selection. Pure, so it can be unit tested. */
object Splice {
    class Result(val text: String, val cursor: Int)

    /**
     * Replaces [selStart]..[selEnd] of [current] with [insert] and returns the new text and where the
     * cursor goes (just after the inserted text). A space is added in front when the text would
     * otherwise run into the word before it. Returns null when the selection does not fit [current].
     */
    fun apply(current: String, selStart: Int, selEnd: Int, insert: String): Result? {
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)
        if (start < 0 || end > current.length) return null
        val piece = spaced(current, start, insert)
        return Result(current.substring(0, start) + piece + current.substring(end), start + piece.length)
    }

    /** [insert] as it should go in at [start] of [current]: with a space in front if it needs one. */
    fun spaced(current: String, start: Int, insert: String): String =
        if (needsSpace(current, start, insert)) " $insert" else insert

    private fun needsSpace(current: String, start: Int, insert: String): Boolean {
        if (start <= 0 || start > current.length || insert.isEmpty()) return false
        val before = current[start - 1]
        if (before.isWhitespace() || before in OPENERS) return false
        return insert[0].isLetterOrDigit()
    }

    private const val OPENERS = "([{\"'“‘/@#-"
}

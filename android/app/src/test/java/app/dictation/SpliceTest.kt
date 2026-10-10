package app.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpliceTest {
    private fun splice(current: String, start: Int, end: Int, insert: String): Pair<String, Int>? =
        Splice.apply(current, start, end, insert)?.let { it.text to it.cursor }

    @Test fun emptyField() = assertEquals("Hello." to 6, splice("", 0, 0, "Hello."))

    @Test fun atCursorInTheMiddle() =
        assertEquals("one two three" to 8, splice("one three", 4, 4, "two "))

    @Test fun replacesTheSelection() =
        assertEquals("one two three" to 7, splice("one XXX three", 4, 7, "two"))

    @Test fun reversedSelection() =
        assertEquals("one two three" to 7, splice("one XXX three", 7, 4, "two"))

    @Test fun addsSpaceAfterAWord() =
        assertEquals("Done. Next one." to 15, splice("Done.", 5, 5, "Next one."))

    @Test fun noSpaceAfterWhitespaceOrOpener() {
        assertEquals("Done. Next" to 10, splice("Done. ", 6, 6, "Next"))
        assertEquals("line\nNext" to 9, splice("line\n", 5, 5, "Next"))
        assertEquals("(Next" to 5, splice("(", 1, 1, "Next"))
    }

    @Test fun noSpaceBeforePunctuation() = assertEquals("Done, ok" to 8, splice("Done", 4, 4, ", ok"))

    @Test fun spacedForPaste() {
        assertEquals(" Next", Splice.spaced("Done.", 5, "Next"))
        assertEquals("Next", Splice.spaced("", 0, "Next"))
        assertEquals("Next", Splice.spaced("abc", 9, "Next"))
    }

    @Test fun selectionOutsideTheText() {
        assertNull(splice("abc", 2, 9, "x"))
        assertNull(splice("", -1, -1, "x"))
    }
}

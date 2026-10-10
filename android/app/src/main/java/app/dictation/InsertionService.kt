package app.dictation

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.ParagraphStyle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** How a dictation reached the user: spliced into the field, pasted into it, or left on the clipboard. */
enum class InsertPath { DIRECT, PASTE, CLIPBOARD }

/**
 * Tracks whether an editable field has input focus (the bubble shows only then) and inserts
 * dictated text into that field. Field contents are read once, for the splice, and never kept.
 * Password fields are never read or written.
 */
class InsertionService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var checkPending = false
    private val check = Runnable { checkPending = false; publishFocus() }
    private val settle = Runnable { publishFocus() }

    override fun onServiceConnected() {
        instance = this
        publishFocus()
        // The system starts this service at boot, so the bubble comes back without opening the app.
        runCatching { OverlayService.sync(this) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The event only says that focus may have moved; its content is not used.
        if (!checkPending) {
            checkPending = true
            main.postDelayed(check, CHECK_MS)
        }
        // Focus often lands after the last event of a screen change, so look once more when it is quiet.
        main.removeCallbacks(settle)
        main.postDelayed(settle, SETTLE_MS)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        main.removeCallbacks(check)
        main.removeCallbacks(settle)
        if (instance === this) {
            instance = null
            setFocused(null)
        }
    }

    private fun publishFocus() = setFocused(target() != null)

    /** The field with input focus, if it is editable and not a password field. */
    private fun target(): AccessibilityNodeInfo? {
        val node = try {
            findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } catch (e: Exception) {
            null
        } ?: return null
        return node.takeIf { it.isEditable && !it.isPassword }
    }

    /**
     * Splices [text] in at the cursor with ACTION_SET_TEXT. Only tried when a paste did not work,
     * because rewriting a whole field behind an app's back can break the app: Google Keep keeps its
     * own copy of the text and crashed after one. False when the field cannot be read or set, or
     * when the rewrite could lose something (formatting, or a very long text).
     */
    private fun insertDirect(text: String): Boolean {
        val node = target() ?: return false
        if (!node.refresh() || node.isPassword || !isPlain(node)) return false
        if (node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) return false
        val current = shownText(node)
        if (current.length > MAX_SPLICE_CHARS) return false
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (start < 0 || end < 0) {
            if (current.isNotEmpty()) return false // cursor unknown: a splice could land anywhere
            start = 0
            end = 0
        }
        val spliced = Splice.apply(current, start, end, text) ?: return false
        val setText = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, spliced.text)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setText)) return false
        // Some apps accept the action and ignore it. Only a changed field counts.
        if (!changedFrom(node, current)) return false
        val setCursor = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, spliced.cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, spliced.cursor)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, setCursor)
        return true
    }

    /**
     * Whether ACTION_SET_TEXT, which writes plain text over the whole field, would lose nothing.
     * - Web content (Chrome and every WebView): only a real `<input>` is known to be plain. A
     *   `<textarea>` and a rich editor (Gmail's body, Obsidian) look the same from here, and
     *   setting a rich editor's text was seen to strip its formatting.
     * - Native fields: plain unless the text carries formatting spans (bold, links, mentions).
     */
    private fun isPlain(node: AccessibilityNodeInfo): Boolean {
        if (node.extras.containsKey(CHROME_ROLE)) return node.inputType != InputType.TYPE_NULL
        val text = node.text as? Spanned ?: return true
        return text.getSpans(0, text.length, Any::class.java).none {
            (it is CharacterStyle || it is ParagraphStyle) && it.javaClass.simpleName !in HARMLESS_SPANS
        }
    }

    /**
     * Puts [text] on the clipboard and pastes it at the cursor. This is the first choice: the app does
     * the insert itself, exactly as when the user pastes, so its own idea of the text stays right.
     * False when the app refuses the paste or the field is seen not to change.
     */
    private fun paste(text: String): Boolean {
        val node = target() ?: return false
        if (!node.refresh() || node.isPassword) return false
        val before = shownText(node)
        val piece = Splice.spaced(before, node.textSelectionStart, text)
        setClip(this, piece)
        Thread.sleep(CLIP_SETTLE_MS) // let the new clip reach the focused app
        node.refresh()
        if (!node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return false
        // A field whose text cannot be read cannot be checked; take the app's word for it.
        return node.text == null || changedFrom(node, before)
    }

    private fun changedFrom(node: AccessibilityNodeInfo, before: String): Boolean {
        repeat(VERIFY_TRIES) {
            if (node.refresh() && shownText(node) != before) return true
            Thread.sleep(VERIFY_STEP_MS)
        }
        return false
    }

    /** An empty field reports its hint as its text. */
    private fun shownText(node: AccessibilityNodeInfo): String =
        if (node.isShowingHintText) "" else node.text?.toString() ?: ""

    /** The app and the shape of the focused field, for the log. Never its content. */
    private fun describeFocus(): String = runCatching {
        val n = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return "none"
        val spans = (n.text as? Spanned)
            ?.getSpans(0, n.text.length, Any::class.java)?.map { it.javaClass.simpleName }?.distinct()
        "${n.packageName} class=${n.className} web=${n.extras.containsKey(CHROME_ROLE)} " +
            "inputType=0x${Integer.toHexString(n.inputType)} spans=$spans"
    }.getOrDefault("unknown")

    companion object {
        private const val TAG = "Dictation"
        private const val CHECK_MS = 150L
        private const val SETTLE_MS = 700L
        private const val CLIP_SETTLE_MS = 100L
        private const val VERIFY_TRIES = 10
        private const val VERIFY_STEP_MS = 50L
        // ACTION_SET_TEXT rewrites the whole field. Past this size a paste is the safer way in.
        private const val MAX_SPLICE_CHARS = 10_000
        private const val CHROME_ROLE = "AccessibilityNodeInfo.chromeRole"
        // Styling spans the keyboard and spell checker leave on plain text.
        private val HARMLESS_SPANS = setOf(
            "SuggestionSpan", "SuggestionRangeSpan", "SpellCheckSpan", "EasyEditSpan",
            "UnderlineSpan", "LocaleSpan", "TtsSpan",
        )

        @Volatile private var instance: InsertionService? = null

        /** Whether an editable field has focus. Null while the service is off and focus is unknown. */
        @Volatile var editableFocused: Boolean? = null
            private set

        val enabled get() = instance != null

        private fun setFocused(value: Boolean?) {
            if (editableFocused == value) return
            editableFocused = value
            DictationState.update()
        }

        /**
         * Gets [text] to the user by the first path that works: clipboard and paste into the focused
         * field, else splice it in directly, else clipboard alone. Blocks briefly; call it off the main thread.
         */
        fun insert(ctx: Context, text: String): InsertPath {
            val service = instance
            val path = when {
                service == null -> InsertPath.CLIPBOARD
                runCatching { service.paste(text) }.getOrDefault(false) -> InsertPath.PASTE
                runCatching { service.insertDirect(text) }.getOrDefault(false) -> InsertPath.DIRECT
                else -> InsertPath.CLIPBOARD
            }
            if (path == InsertPath.CLIPBOARD) setClip(ctx, text)
            // The app and the path only, for the per-app notes. Never the text.
            Log.i(TAG, "insert path=$path app=${service?.describeFocus() ?: "service-off"}")
            return path
        }

        private fun setClip(ctx: Context, text: String) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("dictation", text))
        }
    }
}

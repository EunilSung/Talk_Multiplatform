package com.eunilsung.talk.ui.chatroom.input

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import com.eunilsung.talk.util.wrapMention
import androidx.compose.ui.text.input.VisualTransformation

internal val MENTION_INPUT_REGEX = Regex("""(^|\s)@([^\s]*)$""")

data class MentionSpan(val userId: String, val name: String, val start: Int, val end: Int)

/** 입력창의 한 시점 — 글과 멘션. */
data class MentionFieldSnapshot(val value: TextFieldValue, val mentions: List<MentionSpan>)

class MentionFieldState {
    var value by mutableStateOf(TextFieldValue(""))
        private set
    var mentions by mutableStateOf<List<MentionSpan>>(emptyList())
        private set

    val text: String get() = value.text

    fun clear() {
        value = TextFieldValue("")
        mentions = emptyList()
    }

    /** 지금의 글과 멘션을 그대로 떠 둔다. [restore] 로 되돌린다. */
    fun snapshot(): MentionFieldSnapshot = MentionFieldSnapshot(value, mentions)

    fun restore(snapshot: MentionFieldSnapshot) {
        value = snapshot.value
        mentions = snapshot.mentions
    }

    /**
     * 글을 통째로 바꾼다. 멘션은 자리가 달라지므로, 새 글에 `@이름` 이 그대로 남아 있는 것만 찾아 다시 건다.
     * 이름이 바뀌었거나 사라진 멘션은 보통 글자로 남는다.
     */
    fun replaceText(newText: String) {
        var searchFrom = 0
        val kept = mentions.sortedBy { it.start }.mapNotNull { mention ->
            val chip = "@${mention.name}"
            val start = newText.indexOf(chip, searchFrom).takeIf { it >= 0 } ?: return@mapNotNull null
            searchFrom = start + chip.length
            mention.copy(start = start, end = searchFrom)
        }
        mentions = kept
        value = TextFieldValue(newText, TextRange(newText.length))
    }

    fun currentMentionQuery(): String? {
        val caret = value.selection.end.coerceIn(0, value.text.length)
        val beforeCaret = value.text.substring(0, caret)
        return MENTION_INPUT_REGEX.find(beforeCaret)?.groupValues?.get(2)
    }

    fun insertMention(userId: String, name: String) {
        val caret = value.selection.end.coerceIn(0, value.text.length)
        val before = value.text.substring(0, caret)
        val after = value.text.substring(caret)
        val m = MENTION_INPUT_REGEX.find(before) ?: return
        val boundary = m.groupValues[1]
        val tokenStart = m.range.first
        val head = before.substring(0, tokenStart)
        val chip = "@$name"
        val mentionStart = head.length + boundary.length
        val mentionEnd = mentionStart + chip.length
        val newText = head + boundary + chip + " " + after
        val caretAfter = mentionEnd + 1
        mentions = mentions + MentionSpan(userId, name, mentionStart, mentionEnd)
        value = TextFieldValue(newText, TextRange(caretAfter))
    }

    fun onValueChange(new: TextFieldValue) {
        val old = value
        if (new.text == old.text) {
            val norm = normalizeSelection(new.selection)
            value = if (norm == new.selection) new else new.copy(selection = norm)
            return
        }

        val o = old.text
        val n = new.text
        val minLen = minOf(o.length, n.length)
        var p = 0
        while (p < minLen && o[p] == n[p]) p++
        var s = 0
        while (s < minLen - p && o[o.length - 1 - s] == n[n.length - 1 - s]) s++

        var editStart = p
        var editEnd = o.length - s
        val inserted = n.substring(p, n.length - s)

        val broken = mentions.filter { it.start < editEnd && it.end > editStart }
        if (broken.isNotEmpty()) {
            editStart = minOf(editStart, broken.minOf { it.start })
            editEnd = maxOf(editEnd, broken.maxOf { it.end })
        }

        val resultText = o.substring(0, editStart) + inserted + o.substring(editEnd)
        val delta = inserted.length - (editEnd - editStart)
        val survived = mentions
            .filter { it.end <= editStart || it.start >= editEnd }
            .map { if (it.start >= editEnd) it.copy(start = it.start + delta, end = it.end + delta) else it }

        mentions = survived

        if (broken.isEmpty() && resultText == new.text) {
            value = new
        } else {
            val caret = (editStart + inserted.length).coerceIn(0, resultText.length)
            value = TextFieldValue(resultText, normalizeSelection(TextRange(caret), survived, resultText.length))
        }
    }

    fun visualTransformation(color: Color): VisualTransformation {
        val spans = mentions
        return VisualTransformation { input ->
            val styled = buildAnnotatedString {
                append(input.text)
                spans.forEach { m ->
                    val end = m.end.coerceAtMost(input.text.length)
                    if (m.start in 0 until end) addStyle(SpanStyle(color = color), m.start, end)
                }
            }
            TransformedText(styled, OffsetMapping.Identity)
        }
    }

    fun toTaggedText(): String {
        if (mentions.isEmpty()) return value.text
        val t = value.text
        val sb = StringBuilder()
        var last = 0
        mentions.sortedBy { it.start }.forEach { m ->
            val end = m.end.coerceAtMost(t.length)
            if (m.start < last || m.start > t.length || m.start >= end) return@forEach
            sb.append(t, last, m.start)
            sb.append(wrapMention(t, m.start, end))
            last = end
        }
        sb.append(t, last, t.length)
        return sb.toString()
    }

    private fun normalizeSelection(
        sel: TextRange,
        spans: List<MentionSpan> = mentions,
        textLen: Int = value.text.length,
    ): TextRange {
        if (sel.collapsed) {
            val c = sel.start
            val inside = spans.firstOrNull { it.start < c && c < it.end }
            return if (inside != null) TextRange(inside.start, inside.end) else sel
        }
        var start = minOf(sel.start, sel.end)
        var end = maxOf(sel.start, sel.end)
        var changed = true
        while (changed) {
            changed = false
            for (m in spans) {
                if (m.start < end && m.end > start) {
                    if (m.start < start) { start = m.start; changed = true }
                    if (m.end > end) { end = m.end; changed = true }
                }
            }
        }
        return TextRange(start.coerceIn(0, textLen), end.coerceIn(0, textLen))
    }
}

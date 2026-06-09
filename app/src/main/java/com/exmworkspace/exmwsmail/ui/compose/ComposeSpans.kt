package com.exmworkspace.exmwsmail.ui.compose

// Span arithmetic for the rich-text editor: shifting spans on text edits,
// toggling/clearing/merging inline spans. Extracted from ComposeViewModel.

private interface RangeSpan {
    val start: Int
    val end: Int
}

// Block-level shift: for ListSpan and AlignSpan we want the span to extend
// when text is inserted at or within its boundary, so newly typed characters
// stay inside the same list/paragraph.
internal fun shiftListSpans(
    spans: List<ListSpan>,
    oldText: String,
    newText: String,
): List<ListSpan> = shiftBlockSpans(spans, oldText, newText) { sp, s, e -> sp.copy(start = s, end = e) }

internal fun shiftAlignSpans(
    spans: List<AlignSpan>,
    oldText: String,
    newText: String,
): List<AlignSpan> = shiftBlockSpans(spans, oldText, newText) { sp, s, e -> sp.copy(start = s, end = e) }

private inline fun <T> shiftBlockSpans(
    spans: List<T>,
    oldText: String,
    newText: String,
    crossinline copyAt: (T, Int, Int) -> T,
): List<T> where T : Any {
    if (spans.isEmpty()) return spans
    val oldLen = oldText.length
    val newLen = newText.length
    if (oldLen == newLen) return spans
    val delta = newLen - oldLen
    val diffStart = (0 until minOf(oldLen, newLen)).firstOrNull { oldText[it] != newText[it] }
        ?: minOf(oldLen, newLen)
    return spans.mapNotNull { span ->
        val s = startOf(span)
        val e = endOf(span)
        when {
            e < diffStart -> span
            s > diffStart -> {
                val ns = (s + delta)
                val ne = (e + delta).coerceAtMost(newLen)
                if (ne >= ns && ns >= 0) copyAt(span, ns, ne) else null
            }
            else -> {
                // change at or within the span: extend end
                val ne = (e + delta).coerceAtLeast(s).coerceAtMost(newLen)
                copyAt(span, s, ne)
            }
        }
    }
}

internal fun mergeAdjacentLists(spans: List<ListSpan>, text: String): List<ListSpan> {
    if (spans.size < 2) return spans
    val sorted = spans.sortedBy { it.start }
    val merged = mutableListOf<ListSpan>()
    for (sp in sorted) {
        val last = merged.lastOrNull()
        if (last != null && last.kind == sp.kind &&
            (last.end == sp.start ||
                (last.end + 1 == sp.start && text.getOrNull(last.end) == '\n') ||
                (last.end < sp.start && (last.end until sp.start).all {
                    text.getOrNull(it) == '\n'
                }))
        ) {
            merged[merged.lastIndex] = last.copy(end = sp.end)
        } else {
            merged += sp
        }
    }
    return merged
}

// Generic helpers operating on any "span with start/end". We use reified copy lambdas.
internal inline fun <T> shiftSpans(
    spans: List<T>,
    oldText: String,
    newText: String,
    crossinline construct: T.(start: Int, end: Int) -> T,
): List<T> where T : Any {
    if (spans.isEmpty()) return spans
    val oldLen = oldText.length
    val newLen = newText.length
    if (oldLen == newLen) return spans
    val delta = newLen - oldLen
    val diffStart = (0 until minOf(oldLen, newLen)).firstOrNull { oldText[it] != newText[it] }
        ?: minOf(oldLen, newLen)
    return spans.mapNotNull { span ->
        val s = startOf(span)
        val e = endOf(span)
        when {
            e <= diffStart -> span
            s >= diffStart -> {
                val ns = (s + delta)
                val ne = (e + delta).coerceAtMost(newLen)
                if (ne > ns && ns >= 0) span.construct(ns, ne) else null
            }
            else -> {
                val ne = (e + delta).coerceAtLeast(s)
                if (ne > s) span.construct(s, ne.coerceAtMost(newLen)) else null
            }
        }
    }
}

internal fun startOf(span: Any): Int = when (span) {
    is StyleSpan -> span.start
    is ColorSpan -> span.start
    is SizeSpan -> span.start
    is FamilySpan -> span.start
    is AlignSpan -> span.start
    is ListSpan -> span.start
    else -> 0
}

internal fun endOf(span: Any): Int = when (span) {
    is StyleSpan -> span.end
    is ColorSpan -> span.end
    is SizeSpan -> span.end
    is FamilySpan -> span.end
    is AlignSpan -> span.end
    is ListSpan -> span.end
    else -> 0
}

// Toggle inline same-type span: if a span fully covers selection, split/remove; else add.
internal inline fun <T> toggleSpan(
    list: List<T>,
    start: Int,
    end: Int,
    type: StyleType,
    construct: (Int, Int) -> T,
): List<T> where T : Any {
    val matching = list.filter {
        startOf(it) < end && endOf(it) > start && (it as? StyleSpan)?.type == type
    }
    val containing = matching.firstOrNull { startOf(it) <= start && endOf(it) >= end }
    return if (containing != null) {
        val replacements = mutableListOf<T>()
        val cs = startOf(containing)
        val ce = endOf(containing)
        if (cs < start) replacements += construct(cs, start)
        if (end < ce) replacements += construct(end, ce)
        list - containing + replacements
    } else {
        val mergedStart = (matching.minOfOrNull { startOf(it) } ?: start).coerceAtMost(start)
        val mergedEnd = (matching.maxOfOrNull { endOf(it) } ?: end).coerceAtLeast(end)
        list - matching.toSet() + construct(mergedStart, mergedEnd)
    }
}

internal inline fun <T> clearSpansIn(
    list: List<T>,
    start: Int,
    end: Int,
): List<T> where T : Any {
    if (list.isEmpty()) return list
    @Suppress("UNCHECKED_CAST")
    val rebuilt = mutableListOf<T>()
    list.forEach { span ->
        val s = startOf(span)
        val e = endOf(span)
        when {
            e <= start || s >= end -> rebuilt += span
            s >= start && e <= end -> { /* drop */ }
            s < start && e > end -> {
                rebuilt += copyWith(span, s, start)
                rebuilt += copyWith(span, end, e)
            }
            s < start -> rebuilt += copyWith(span, s, start)
            else -> rebuilt += copyWith(span, end, e)
        }
    }
    return rebuilt
}

internal inline fun <T> mergeSpan(
    list: List<T>,
    new: T,
    sameAttrs: (T, T) -> Boolean,
): List<T> where T : Any {
    val ns = startOf(new)
    val ne = endOf(new)
    val toMerge = list.filter {
        sameAttrs(it, new) && startOf(it) <= ne && endOf(it) >= ns
    }
    val mergedStart = (toMerge.minOfOrNull { startOf(it) } ?: ns).coerceAtMost(ns)
    val mergedEnd = (toMerge.maxOfOrNull { endOf(it) } ?: ne).coerceAtLeast(ne)
    return list - toMerge.toSet() + copyWith(new, mergedStart, mergedEnd)
}

@Suppress("UNCHECKED_CAST")
internal fun <T> copyWith(span: T, s: Int, e: Int): T = when (span) {
    is StyleSpan -> span.copy(start = s, end = e) as T
    is ColorSpan -> span.copy(start = s, end = e) as T
    is SizeSpan -> span.copy(start = s, end = e) as T
    is FamilySpan -> span.copy(start = s, end = e) as T
    is AlignSpan -> span.copy(start = s, end = e) as T
    is ListSpan -> span.copy(start = s, end = e) as T
    else -> span
}

internal fun lineRangeFor(text: String, selStart: Int, selEnd: Int): Pair<Int, Int> {
    val length = text.length
    if (length == 0) return 0 to 0
    val s = selStart.coerceIn(0, length)
    val e = selEnd.coerceIn(0, length)
    var lineStart = s
    while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
    var lineEnd = e
    while (lineEnd < length && text[lineEnd] != '\n') lineEnd++
    return lineStart to lineEnd
}

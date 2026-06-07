# QuickNovel Line-Based Scroll Algorithm Migration Plan

## Executive Summary
Migrate from Miko's assumption-based character-to-paragraph scroll algorithm to QuickNovel's verify-then-adjust line-based coordinate system. This fixes boundary issues at chapter end and provides more precise position tracking.

---

## Current Architecture (Problems)

### scrollToCharacterPosition() Flow
```
1. Calculate target paragraph from character position
2. Call scrollToPositionWithOffset(targetParagraph, 1)
3. ASSUME paragraph is now at top
4. Calculate scrollBy() offset from ASSUMED position
5. Execute scrollBy()
```

**Problem**: At chapter boundaries, Step 2 fails (can't position paragraph at top), but Step 4 still calculates from false assumption → wrong scroll position.

### Coordinate System
- Uses TextView-relative coordinates (`layout.getLineTop(i)`)
- Can't compare across different TextViews or against screen boundaries
- extractVisibleLines() called BEFORE scroll operations

### Position Tracking
- Uses `firstVisibleItemPosition` for bookmarks (includes partially visible items)
- Character position based on TextView-relative interpolation

---

## Target Architecture (QuickNovel Pattern)

### scrollToCharacterPosition() Flow
```
1. Calculate target paragraph from character position
2. Call scrollToPositionWithOffset(targetParagraph, 1)
3. post {} to wait for layout completion
4. getAllVisibleLines() - get ACTUAL positions from NEW state
5. Find line containing target character
6. scrollBy(line.absoluteTop - getContentTopY())
```

**Why it works**: Step 4 extracts real positions AFTER scroll, no assumptions. Works at boundaries because we calculate from what's actually on screen.

### Coordinate System
- Uses window-absolute coordinates via `getLocationInWindow()`
- All lines have comparable Y coordinates in same reference frame
- getAllVisibleLines() called AFTER scroll operations inside post{}

### Position Tracking
- Uses first FULLY visible line (`line.absoluteTop >= getContentTopY()`)
- Character position from line's actual absoluteStartChar

---

## Migration Steps

### Phase 1: Foundation - Coordinate System (30 min)

**Add Window Coordinate Helpers**
```kotlin
// Similar to QuickNovel's getTopY/getBottomY
private fun getContentTopY(): Int {
    val location = IntArray(2)
    binding.novelRecyclerView.getLocationInWindow(location)
    return location[1] + binding.novelRecyclerView.paddingTop
}

private fun getContentBottomY(): Int {
    val location = IntArray(2)
    binding.novelRecyclerView.getLocationInWindow(location)
    return location[1] + binding.novelRecyclerView.height - binding.novelRecyclerView.paddingBottom
}
```

**Refactor TextVisualLine Data Class**
```kotlin
// BEFORE
private data class TextVisualLine(
    val startCharIndex: Int,
    val endCharIndex: Int,
    val topPixel: Int,        // TextView-relative
    val bottomPixel: Int,     // TextView-relative
    val paragraphIndex: Int
)

// AFTER
private data class TextVisualLine(
    val absoluteStartChar: Int,  // Absolute in chapter
    val absoluteEndChar: Int,    // Absolute in chapter
    val absoluteTop: Int,        // Window coordinate
    val absoluteBottom: Int,     // Window coordinate
    val paragraphIndex: Int      // Keep for debugging
)
```

---

### Phase 2: Line Extraction - Timing and Coordinates (45 min)

**Rewrite extractVisibleLines()**
```kotlin
// NEW SIGNATURE - accepts absolute character offset for paragraph
private fun extractLinesFromTextView(
    textView: TextView,
    paragraphStartChar: Int,
    paragraphIndex: Int
): List<TextVisualLine> {
    val lines = mutableListOf<TextVisualLine>()
    val layout = textView.layout ?: return lines
    
    // Get TextView's absolute position in window
    val location = IntArray(2)
    textView.getLocationInWindow(location)
    val viewY = location[1] + textView.paddingTop
    
    // Extract each line with absolute coordinates
    for (i in 0 until layout.lineCount) {
        val lineStartChar = layout.getLineStart(i)
        val lineEndChar = layout.getLineEnd(i)
        
        lines.add(TextVisualLine(
            absoluteStartChar = paragraphStartChar + lineStartChar,
            absoluteEndChar = paragraphStartChar + lineEndChar,
            absoluteTop = viewY + layout.getLineTop(i),      // Absolute Y
            absoluteBottom = viewY + layout.getLineBottom(i), // Absolute Y
            paragraphIndex = paragraphIndex
        ))
    }
    
    return lines
}
```

**Create getAllVisibleLines()**
```kotlin
// NEW METHOD - aggregates lines from all visible RecyclerView items
private fun getAllVisibleLines(): List<TextVisualLine> {
    val allLines = mutableListOf<TextVisualLine>()
    val layoutManager = binding.novelRecyclerView.layoutManager as? LinearLayoutManager ?: return allLines
    
    val firstVisible = layoutManager.findFirstVisibleItemPosition()
    val lastVisible = layoutManager.findLastVisibleItemPosition()
    
    if (firstVisible == RecyclerView.NO_POSITION) return allLines
    
    for (position in firstVisible..lastVisible) {
        val viewHolder = binding.novelRecyclerView.findViewHolderForAdapterPosition(position)
        if (viewHolder !is NovelContentAdapter.ParagraphViewHolder) continue
        
        val textView = viewHolder.getParagraphTextView()
        val paragraph = contentAdapter.getItems().getOrNull(position) as? NovelContentItem.Paragraph ?: continue
        
        allLines.addAll(
            extractLinesFromTextView(textView, paragraph.startCharIndex, position)
        )
    }
    
    return allLines
}
```

---

### Phase 3: Scroll Algorithm - The Critical Fix (60 min)

**Rewrite scrollToCharacterPosition()**
```kotlin
private fun scrollToCharacterPosition(characterPosition: Int, sliderProgress: Float? = null) {
    // Keep special case for character 0 (unchanged)
    if (characterPosition == 0) { /* existing code */ }
    
    val items = contentAdapter.getItems()
    if (items.isEmpty()) return
    
    val paragraphs = items.filterIsInstance<NovelContentItem.Paragraph>()
    if (paragraphs.isEmpty()) return
    
    // Keep special case for 100% slider (unchanged)
    val lastCharIndex = paragraphs.last().endCharIndex
    val isSliderAt100 = sliderProgress != null && sliderProgress >= 0.995f
    if (characterPosition >= lastCharIndex && isSliderAt100) { /* existing code */ }
    
    // NEW ALGORITHM STARTS HERE
    // Step 1: Find target paragraph
    val targetParagraph = paragraphs.find { paragraph ->
        characterPosition >= paragraph.startCharIndex && characterPosition < paragraph.endCharIndex
    } ?: return
    
    val targetAdapterPosition = items.indexOf(targetParagraph)
    if (targetAdapterPosition < 0) return
    
    Log.d("NovelReader", "Scrolling to character $characterPosition in paragraph $targetAdapterPosition")
    
    val layoutManager = binding.novelRecyclerView.layoutManager as LinearLayoutManager
    
    // Step 2: Rough scroll to target paragraph
    layoutManager.scrollToPositionWithOffset(targetAdapterPosition, 1)
    
    // Step 3: Wait for layout, then fine-tune based on ACTUAL positions
    binding.novelRecyclerView.post {
        // Get all visible lines from NEW state
        val visibleLines = getAllVisibleLines()
        
        // Find the line containing our target character
        val targetLine = visibleLines.firstOrNull { line ->
            characterPosition >= line.absoluteStartChar && 
            characterPosition < line.absoluteEndChar
        }
        
        if (targetLine != null) {
            // Calculate delta from ACTUAL line position to screen top
            val delta = targetLine.absoluteTop - getContentTopY()
            
            Log.d("NovelReader", "Fine-tuning: line at ${targetLine.absoluteTop}, " +
                  "screen top at ${getContentTopY()}, scrolling $delta px")
            
            binding.novelRecyclerView.scrollBy(0, delta)
        } else {
            Log.w("NovelReader", "Target line not found after scroll - may need to scroll more")
        }
    }
}
```

**DELETE Old Boundary Detection**
```kotlin
// REMOVE THESE LINES from scrollToCharacterPosition()
val firstVisible = layoutManager.findFirstVisibleItemPosition()
val lastVisible = layoutManager.findLastVisibleItemPosition()
val canScrollDown = binding.novelRecyclerView.canScrollVertically(1)
val isTargetAlreadyVisible = targetAdapterPosition in firstVisible..lastVisible

if (isTargetAlreadyVisible && !canScrollDown) {
    // ... entire special case block - DELETE
}
```

---

### Phase 4: Position Tracking - Bookmarks and Slider (30 min)

**Update calculateCurrentCharacterPosition()**
```kotlin
private fun calculateCurrentCharacterPosition(): Int {
    val visibleLines = getAllVisibleLines()
    if (visibleLines.isEmpty()) return 0
    
    val contentTopY = getContentTopY()
    
    // Find first FULLY visible line (not partially visible)
    val firstFullyVisibleLine = visibleLines.firstOrNull { line ->
        line.absoluteTop >= contentTopY
    } ?: visibleLines.firstOrNull() ?: return 0
    
    return firstFullyVisibleLine.absoluteStartChar
}
```

**Update updateSliderPosition()**
```kotlin
private fun updateSliderPosition() {
    if (chapterProgressSeekbar.isPressed) return
    
    val timeSinceUserDrag = System.currentTimeMillis() - lastUserDragTime
    if (timeSinceUserDrag < USER_DRAG_PROTECTION_WINDOW) return
    
    // Get all visible lines
    val lines = getAllVisibleLines()
    if (lines.isEmpty()) return
    
    val contentTopY = getContentTopY()
    
    // Find first FULLY visible line
    val topVisibleLine = lines.firstOrNull { it.absoluteTop >= contentTopY }
        ?: lines.firstOrNull()
        ?: return
    
    val characterPosition = topVisibleLine.absoluteStartChar
    
    // Rest unchanged - calculate progress and update slider
    val items = contentAdapter.getItems()
    val paragraphs = items.filterIsInstance<NovelContentItem.Paragraph>()
    if (paragraphs.isEmpty()) return
    
    val firstCharIndex = paragraphs.first().startCharIndex
    val lastCharIndex = paragraphs.last().endCharIndex
    val totalCharacters = (lastCharIndex - firstCharIndex).coerceAtLeast(1)
    
    val adjustedPosition = (characterPosition - firstCharIndex).coerceIn(0, totalCharacters)
    val progress = adjustedPosition.toFloat() / totalCharacters
    
    val rawProgress = (progress * 96f).coerceIn(0f, 96f)
    chapterProgressSeekbar.value = roundToNearestStep(rawProgress, 2f)
}
```

---

### Phase 5: Cleanup (15 min)

**Remove Obsolete Code**
1. Delete old `extractVisibleLines()` method (replaced by `extractLinesFromTextView()`)
2. Delete boundary detection variables and logic
3. Remove `isProgrammaticScroll` flag (no longer needed with new algorithm)

**Update updateTextSettings()**
```kotlin
// Keep character position saving
val savedCharacterPosition = calculateCurrentCharacterPosition()

// Keep ViewTreeObserver pattern
val listener = object : OnGlobalLayoutListener {
    override fun onGlobalLayout() {
        removeOnGlobalLayoutListener(this)
        layoutListeners.remove(this)
        
        // Use new scroll algorithm (no changes needed here)
        scrollToCharacterPosition(savedCharacterPosition)
        
        isRestoringScroll = false
    }
}
```

**Logging Cleanup**
- Remove decorative borders (`════════`)
- Change verbose logs to `Log.d()` instead of `Log.e()`
- Keep essential tracking: character position, scroll operations, errors

---

## Testing Checklist

### Boundary Scenarios
- [ ] Slider to 90% - verify different content
- [ ] Slider to 93% - verify different content
- [ ] Slider to 95% - verify different content
- [ ] Slider to 97% - verify different content  
- [ ] Slider to 96 (100%) - verify scrolls to absolute end
- [ ] Rapid slider drags between 90-96% - should be responsive

### Bookmark Scenarios
- [ ] Save bookmark at 0% - verify restores to exact beginning
- [ ] Save bookmark at 50% - verify restores to same sentence
- [ ] Save bookmark at 95% - verify restores to correct paragraph
- [ ] Save bookmark at 100% - verify restores to absolute end
- [ ] Close app, reopen - bookmark should restore precisely

### Spacing Change Scenarios
- [ ] At 0% - change spacing 4→8→12dp - should stay at beginning
- [ ] At 50% - change spacing rapidly - should maintain position
- [ ] At 95% - change spacing - should maintain position near end
- [ ] No visible jitter during changes
- [ ] Smooth single restoration per change

### Edge Cases
- [ ] Empty chapter (no paragraphs)
- [ ] Single paragraph chapter
- [ ] Very long paragraph (multiple screens)
- [ ] Character position beyond chapter length
- [ ] Negative character position

---

## Rollback Plan

If issues occur:

1. **Immediate**: Keep old code commented out during testing
2. **Revert**: Use git to restore `NovelReaderActivity.kt` to previous commit
3. **Hybrid**: Can keep new coordinate helpers but revert scroll algorithm

Files to backup before changes:
- `NovelReaderActivity.kt` (lines 580-1100 contain scroll logic)

---

## Success Metrics

### Before (Current State)
- ❌ Positions 95%, 97%, 100% show same content
- ❌ Spacing changes cause jitter
- ❌ Bookmarks imprecise at chapter end
- ❌ Complex boundary detection code

### After (Expected State)
- ✅ Each high percentage shows distinct content
- ✅ Smooth spacing changes with stable positions
- ✅ Precise bookmarks using fully visible lines
- ✅ Simpler algorithm with no special boundary cases

---

## Implementation Timeline

- **Phase 1**: 30 minutes
- **Phase 2**: 45 minutes
- **Phase 3**: 60 minutes (most critical)
- **Phase 4**: 30 minutes
- **Phase 5**: 15 minutes
- **Testing**: 30 minutes

**Total**: ~3.5 hours

---

## Key Architectural Changes Summary

| Component | Before | After |
|-----------|--------|-------|
| **Coordinate System** | TextView-relative | Window-absolute |
| **Line Extraction Timing** | Before scroll | After scroll in post{} |
| **Scroll Algorithm** | Assume then adjust | Verify then adjust |
| **Bookmark Reference** | First visible item | First fully visible line |
| **Boundary Handling** | Special case detection | Natural handling via actual positions |
| **Code Complexity** | High (boundary checks) | Low (single path) |

---

## Bookmark System Changes

### Current Bookmark Flow
```
User exits chapter
├─ calculateCurrentCharacterPosition()
│   ├─ Gets firstVisibleItemPosition (may be 99% scrolled off!)
│   └─ Interpolates character position
├─ viewModel.updateCharacterPosition(characterPosition)
└─ Saved to database

User returns to chapter
├─ Database loads characterPosition
├─ scrollToCharacterPosition(characterPosition)
│   ├─ Calculates target paragraph
│   ├─ scrollToPositionWithOffset() - may fail at boundaries
│   └─ scrollBy() - uses ASSUMED position
└─ User sees incorrect position at boundaries
```

### New Bookmark Flow
```
User exits chapter
├─ calculateCurrentCharacterPosition()
│   ├─ getAllVisibleLines()
│   ├─ Finds FIRST FULLY VISIBLE line (line.absoluteTop >= contentTopY)
│   └─ Returns line.absoluteStartChar (precise!)
├─ viewModel.updateCharacterPosition(characterPosition)
└─ Saved to database

User returns to chapter
├─ Database loads characterPosition
├─ scrollToCharacterPosition(characterPosition)
│   ├─ Calculates target paragraph
│   ├─ scrollToPositionWithOffset()
│   ├─ post { getAllVisibleLines() } ← Get ACTUAL positions
│   └─ scrollBy(line.absoluteTop - contentTopY) ← Use REALITY
└─ User sees EXACT position, even at boundaries ✅
```

**Key Improvement**: Bookmark references the first fully visible sentence, not a partially visible paragraph. Restoration uses actual line positions, not assumptions.

No database schema changes needed - still stores `Int` character position. Only the calculation method changes.

# WebNovel Extension Fix Plan

## Root Cause Analysis

After extensive investigation using network logs, HTML analysis, and API testing, here are the findings:

1. **Browse API is dead**: `/go/pcm/rank/getRank` returns 404. The ranking HTML page works fine.
2. **Chapter list API is empty**: `getContent?chapterId=0` returns book metadata but `groupItems` is an empty array (length 0). No chapters available via API.
3. **Catalog HTML contains ALL chapters**: The catalog page HTML is ~2MB and includes the full chapter list in standard `<ol><li><a>` elements. My CSS selectors were outdated class names (`.j_catalog_list`, etc.) which is why only 51 chapters were found.
4. **Content fetching**: Untested but likely also broken or returns encrypted data. The old `/apiajax/chapter/GetContent` endpoint has been dead since at least 2021.
5. **Search API still works**: Search returns results correctly.

## Fix Strategy (Hybrid: API + HTML)

### Phase 1: Browse (HTML Scraping)
- Replace the broken `/go/pcm/rank/getRank` API call with HTML scraping of `https://www.webnovel.com/ranking/hot`
- Extract novels by finding `<h3>` titles paired with `/book/{slug}_{bookId}` links
- Fall back to `/stories` page if ranking fails

### Phase 2: Chapter List (HTML Scraping)
- On the catalog page (`/book/{id}/catalog`), extract ALL `<a>` tags whose `href` matches `/book/{bookId}/{slug}_{chapterId}`
- This is class-name-agnostic and will capture all chapters regardless of CSS changes
- Parse chapter number and name from the link text and URL

### Phase 3: Chapter Content (API + HTML Fallback)
- Try the current `/go/pcm/chapter/getContent` API first
- If it returns empty/encrypted, scrape the chapter HTML page directly
- Look for `.chapter_content`, `#content`, or content inside `<p>` tags

### Phase 4: Testing & Verification
- Build and install the updated extension
- Test browse, search, chapter list, and content reading
- Collect logs to verify all 3000+ chapters are loaded

## Files to Modify
- `extensions/en/webnovel/src/main/java/yokai/extension/novel/en/webnovel/WebNovel.kt`
- `extensions/en/webnovel/build.gradle` (bump version)


## Status: APPROVED - Implementation in progress

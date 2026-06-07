# RESTORATION INSTRUCTIONS
## Created: 20251015_175947
## Purpose: Rollback from RecyclerView migration if needed

### HOW TO RESTORE:
1. Stop any running builds: .\gradlew --stop
2. Delete modified files in app/src/main/java/eu/kanade/tachiyomi/ui/novel/reader/
3. Copy all files from this backup directory back to original locations
4. Run: .\gradlew clean build

### FILES BACKED UP:
- NovelReaderActivity.kt (1,191 lines)
- NovelReaderViewModel.kt (507 lines)
- NovelContentAdapter.kt (351 lines)
- NovelContentItem.kt (96 lines)
- NovelChapterAdapter.kt (86 lines)
- NovelReaderNavigator.kt (68 lines)
- ParagraphTagHandler.kt (42 lines)
- 6 layout XML files
- Complete settings/ and components/ directories
- Database schema (novel_chapters.sq)
- Build configuration (app/build.gradle.kts)

### VERIFICATION AFTER RESTORE:
.\gradlew assembleStandardDebug installStandardDebug
Test novel reading functionality

### EMERGENCY CONTACT:
If restore fails, check git history for last known working state

package eu.kanade.tachiyomi.ui.novel.reader

import android.text.Editable
import android.text.Html
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import org.xml.sax.XMLReader

/**
 * Custom HTML tag handler that adds proper paragraph spacing.
 * 
 * Html.fromHtml() doesn't add spacing between paragraphs by default.
 * This handler adds line breaks after each </p> tag to create
 * visual separation between paragraphs, matching QuickNovel's formatting.
 * 
 * Uses multiple newlines to create visible spacing since TextView
 * respects newline characters for vertical spacing.
 */
class ParagraphTagHandler : Html.TagHandler {
    
    override fun handleTag(
        opening: Boolean,
        tag: String,
        output: Editable,
        xmlReader: XMLReader
    ) {
        android.util.Log.d("ParagraphTagHandler", "handleTag called: tag=$tag, opening=$opening, outputLength=${output.length}")
        
        if (tag.equals("p", ignoreCase = true)) {
            if (opening) {
                // Add newline before paragraph if not at start
                if (output.isNotEmpty() && !output.endsWith("\n")) {
                    android.util.Log.d("ParagraphTagHandler", "Adding newline before opening <p>")
                    output.append("\n")
                }
            } else {
                // When closing a </p> tag, add double newlines for paragraph spacing
                // This creates visible separation between paragraphs
                android.util.Log.d("ParagraphTagHandler", "Adding double newlines after closing </p>")
                output.append("\n\n")
            }
        }
    }
}

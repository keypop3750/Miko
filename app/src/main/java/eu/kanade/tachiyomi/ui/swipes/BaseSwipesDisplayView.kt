package eu.kanade.tachiyomi.ui.swipes

import android.content.Context
import android.util.AttributeSet
import androidx.viewbinding.ViewBinding
import eu.kanade.tachiyomi.widget.BaseTabbedScrollView

abstract class BaseSwipesDisplayView<VB : ViewBinding> @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    BaseTabbedScrollView<VB>(context, attrs)
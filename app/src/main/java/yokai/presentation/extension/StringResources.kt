package yokai.presentation.extension

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import yokai.util.lang.getString

/**
 * Composable-friendly way to get string resources from MOKO Resources.
 * 
 * Usage:
 *   Text(text = stringResource(MR.strings.hello))
 */
@Composable
fun stringResource(resource: StringResource): String {
    val context = LocalContext.current
    return context.getString(resource)
}

@Composable
fun stringResource(resource: StringResource, vararg args: Any): String {
    val context = LocalContext.current
    return context.getString(resource, *args)
}

@Composable
fun pluralStringResource(resource: PluralsResource, quantity: Int): String {
    val context = LocalContext.current
    return context.getString(resource, quantity)
}

@Composable
fun pluralStringResource(resource: PluralsResource, quantity: Int, vararg args: Any): String {
    val context = LocalContext.current
    return context.getString(resource, quantity, *args)
}

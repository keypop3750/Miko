package eu.kanade.tachiyomi.extension.novel.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager

/**
 * Broadcast receiver for novel extension installation events.
 * Parallel to ExtensionInstallReceiver for manga extensions.
 */
internal class NovelExtensionInstallReceiver(private val listener: Listener) : BroadcastReceiver() {

    /**
     * Interface for notification of extension changes.
     */
    interface Listener {
        fun onExtensionInstalled(pkgName: String)
        fun onExtensionUpdated(pkgName: String)
        fun onExtensionUntrusted(pkgName: String)
        fun onPackageUninstalled(pkgName: String)
        fun onInstallationError(pkgName: String) {}  // Default empty implementation
    }

    /**
     * Registers the broadcast receiver.
     */
    fun register(context: Context) {
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        
        // Register for private extension notifications
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter().apply {
                addAction(ACTION_NOVEL_EXTENSION_ADDED)
                addAction(ACTION_NOVEL_EXTENSION_REPLACED)
                addAction(ACTION_NOVEL_EXTENSION_REMOVED)
                addAction(ACTION_NOVEL_EXTENSION_ERROR)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                if (isNovelExtensionPackage(context, intent)) {
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        listener.onExtensionUpdated(getPackageNameFromIntent(intent)!!)
                    } else {
                        listener.onExtensionInstalled(getPackageNameFromIntent(intent)!!)
                    }
                }
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (isNovelExtensionPackage(context, intent)) {
                    listener.onExtensionUpdated(getPackageNameFromIntent(intent)!!)
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    listener.onPackageUninstalled(getPackageNameFromIntent(intent)!!)
                }
            }
            ACTION_NOVEL_EXTENSION_ADDED -> {
                val pkgName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
                listener.onExtensionInstalled(pkgName)
            }
            ACTION_NOVEL_EXTENSION_REPLACED -> {
                val pkgName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
                listener.onExtensionUpdated(pkgName)
            }
            ACTION_NOVEL_EXTENSION_REMOVED -> {
                val pkgName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
                listener.onPackageUninstalled(pkgName)
            }
            ACTION_NOVEL_EXTENSION_ERROR -> {
                val pkgName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
                listener.onInstallationError(pkgName)
            }
        }
    }

    private fun getPackageNameFromIntent(intent: Intent): String? {
        return intent.data?.encodedSchemeSpecificPart
    }

    private fun isNovelExtensionPackage(context: Context, intent: Intent): Boolean {
        val pkgName = getPackageNameFromIntent(intent) ?: return false
        return try {
            // Check if the package has the novel extension feature
            val pkgInfo = context.packageManager.getPackageInfo(pkgName, android.content.pm.PackageManager.GET_CONFIGURATIONS)
            pkgInfo.reqFeatures?.any { it.name == NOVEL_EXTENSION_FEATURE } == true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val NOVEL_EXTENSION_FEATURE = "yokai.novel.extension"
        
        private const val ACTION_NOVEL_EXTENSION_ADDED = "yokai.novel.extension.ADDED"
        private const val ACTION_NOVEL_EXTENSION_REPLACED = "yokai.novel.extension.REPLACED"
        private const val ACTION_NOVEL_EXTENSION_REMOVED = "yokai.novel.extension.REMOVED"
        private const val ACTION_NOVEL_EXTENSION_ERROR = "yokai.novel.extension.ERROR"
        private const val EXTRA_PACKAGE_NAME = "yokai.novel.extension.PACKAGE_NAME"

        /**
         * Notify that a novel extension was added.
         */
        fun notifyAdded(context: Context, pkgName: String) {
            context.sendBroadcast(
                Intent(ACTION_NOVEL_EXTENSION_ADDED).apply {
                    putExtra(EXTRA_PACKAGE_NAME, pkgName)
                    setPackage(context.packageName)
                }
            )
        }

        /**
         * Notify that a novel extension was replaced/updated.
         */
        fun notifyReplaced(context: Context, pkgName: String) {
            context.sendBroadcast(
                Intent(ACTION_NOVEL_EXTENSION_REPLACED).apply {
                    putExtra(EXTRA_PACKAGE_NAME, pkgName)
                    setPackage(context.packageName)
                }
            )
        }

        /**
         * Notify that a novel extension was removed.
         */
        fun notifyRemoved(context: Context, pkgName: String) {
            context.sendBroadcast(
                Intent(ACTION_NOVEL_EXTENSION_REMOVED).apply {
                    putExtra(EXTRA_PACKAGE_NAME, pkgName)
                    setPackage(context.packageName)
                }
            )
        }

        /**
         * Notify that a novel extension installation failed.
         */
        fun notifyInstallationError(context: Context, pkgName: String) {
            context.sendBroadcast(
                Intent(ACTION_NOVEL_EXTENSION_ERROR).apply {
                    putExtra(EXTRA_PACKAGE_NAME, pkgName)
                    setPackage(context.packageName)
                }
            )
        }
    }
}

package eu.kanade.tachiyomi.extension.novel.util

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Novel-specific install info type that can hold download progress as Int.
 */
typealias NovelExtensionInstallInfo = Pair<InstallStep, Any?>

/**
 * The installer which installs, updates and uninstalls novel extensions.
 * Uses direct OkHttp downloads for faster installation with progress tracking.
 * Supports both private (silent) installation and system PackageInstaller (with user confirmation dialog).
 */
internal class NovelExtensionInstaller(private val context: Context) {

    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val activeDownloads = hashMapOf<String, Boolean>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Map of download id to installer session id
    val downloadInstallerMap = hashMapOf<String, Int>()
    
    // Shared flow with NovelExtensionInstallInfo (Any? allows Int or SessionInfo or null)
    val _downloadsSharedFlow = MutableSharedFlow<Pair<String, NovelExtensionInstallInfo>>()
    val downloadSharedFlow = _downloadsSharedFlow.asSharedFlow()

    /**
     * Downloads and installs a novel extension using direct OkHttp download.
     * Uses system PackageInstaller to show Android's install confirmation dialog.
     */
    suspend fun downloadAndInstall(
        url: String,
        extension: NovelExtensionManager.NovelExtensionInfo,
        scope: CoroutineScope,
    ): Flow<NovelExtensionInstallInfo> {
        val pkgName = extension.pkgName
        Logger.d { "[NOVEL_EXT] Starting direct download for $pkgName from $url" }

        // Cancel any existing download for this package
        if (activeDownloads[pkgName] == true) {
            Logger.d { "[NOVEL_EXT] Cancelling existing download for $pkgName" }
            activeDownloads.remove(pkgName)
        }

        // Clean up any existing install session
        val oldInstall = downloadInstallerMap[pkgName]
        if (oldInstall != null) {
            try {
                context.packageManager.packageInstaller.abandonSession(oldInstall)
            } catch (_: Exception) {}
            downloadInstallerMap.remove(pkgName)
        }

        activeDownloads[pkgName] = true

        val downloadFlow = flow<NovelExtensionInstallInfo> {
            emit(InstallStep.Pending to null)
            
            try {
                // Start download
                emit(InstallStep.Downloading to 0)
                
                val request = GET(url)
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    Logger.e { "[NOVEL_EXT] Download failed: ${response.code}" }
                    activeDownloads.remove(pkgName)
                    emit(InstallStep.Error to null)
                    ioScope.launch {
                        _downloadsSharedFlow.emit(pkgName to (InstallStep.Error to null))
                    }
                    return@flow
                }
                
                val body = response.body
                if (body == null) {
                    Logger.e { "[NOVEL_EXT] Response body is null" }
                    activeDownloads.remove(pkgName)
                    emit(InstallStep.Error to null)
                    ioScope.launch {
                        _downloadsSharedFlow.emit(pkgName to (InstallStep.Error to null))
                    }
                    return@flow
                }
                
                val contentLength = body.contentLength()
                Logger.d { "[NOVEL_EXT] Content length: $contentLength bytes" }
                
                // Create temp file for download
                val tempFile = File(context.cacheDir, "novel_ext_${extension.apkName}")
                if (tempFile.exists()) tempFile.delete()
                
                // Download with progress tracking
                var downloadedBytes = 0L
                var lastProgress = 0
                
                body.byteStream().use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            // Check if download was cancelled
                            if (activeDownloads[pkgName] != true) {
                                Logger.d { "[NOVEL_EXT] Download cancelled for $pkgName" }
                                tempFile.delete()
                                emit(InstallStep.Error to null)
                                return@flow
                            }
                            
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            
                            // Calculate and emit progress (as Int in the Any? slot)
                            if (contentLength > 0) {
                                val progress = ((downloadedBytes * 100) / contentLength).toInt()
                                if (progress > lastProgress) {
                                    lastProgress = progress
                                    emit(InstallStep.Downloading to progress)
                                }
                            }
                        }
                    }
                }
                
                Logger.d { "[NOVEL_EXT] Download complete, downloaded $downloadedBytes bytes" }
                emit(InstallStep.Loading to 100)
                
                // Install using system PackageInstaller by launching an Activity first
                // This avoids Background Activity Launch (BAL) restrictions
                emit(InstallStep.Installing to null)
                Logger.d { "[NOVEL_EXT] Launching install activity for ${tempFile.absolutePath}" }
                
                launchInstallActivity(pkgName, tempFile)
                
                // Note: Don't emit InstallStep.Installed here - it will be emitted when the 
                // system PackageInstaller completes and NovelExtensionInstallReceiver receives the broadcast
                
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_EXT] Download/install failed for $pkgName" }
                activeDownloads.remove(pkgName)
                emit(InstallStep.Error to null)
                ioScope.launch {
                    _downloadsSharedFlow.emit(pkgName to (InstallStep.Error to null))
                }
            }
        }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Logger.e(e) { "[NOVEL_EXT] Flow error for $pkgName" }
                emit(InstallStep.Error to null)
            }

        return flowOf(
            downloadFlow,
            downloadSharedFlow.filter { it.first == pkgName }.map { it.second }
        ).flattenMerge()
    }

    /**
     * Launches an Activity to install the APK.
     * This avoids Background Activity Launch restrictions by starting the activity first,
     * then having the activity create the PackageInstaller session.
     */
    private fun launchInstallActivity(pkgName: String, apkFile: File) {
        try {
            // Move the file to a location where FileProvider can access it
            val installDir = File(context.cacheDir, "novel_extensions")
            if (!installDir.exists()) installDir.mkdirs()
            
            val destinationFile = File(installDir, "$pkgName.apk")
            if (destinationFile.exists()) destinationFile.delete()
            apkFile.copyTo(destinationFile, overwrite = true)
            apkFile.delete()
            
            // Get a content URI for the APK file via FileProvider
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.provider",
                destinationFile
            )
            
            Logger.d { "[NOVEL_EXT] Launching install activity for $pkgName with URI: $uri" }
            
            // Launch the install activity with the APK URI
            // The activity will handle creating the PackageInstaller session
            val intent = Intent(context, NovelExtensionInstallActivity::class.java)
                .setDataAndType(uri, APK_MIME)
                .putExtra(EXTRA_PACKAGE_NAME, pkgName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            context.startActivity(intent)
            
        } catch (e: Exception) {
            Logger.e(e) { "[NOVEL_EXT] Failed to launch install activity" }
            apkFile.delete()
            throw e
        }
    }

    /**
     * Sets the result of the installation of an extension.
     */
    fun setInstalling(pkgName: String, sessionId: Int) {
        ioScope.launch {
            _downloadsSharedFlow.emit(pkgName to (InstallStep.Installing to null))
        }
        downloadInstallerMap[pkgName] = sessionId
    }

    /**
     * Cancels an extension installation.
     */
    fun cancelInstallation(pkgName: String) {
        Logger.d { "[NOVEL_EXT] Cancelling download for $pkgName" }
        activeDownloads.remove(pkgName)
        
        val sessionId = downloadInstallerMap[pkgName]
        if (sessionId != null) {
            try {
                context.packageManager.packageInstaller.abandonSession(sessionId)
            } catch (_: Exception) {}
            downloadInstallerMap.remove(pkgName)
        }
    }

    /**
     * Sets the installation result for an extension.
     */
    fun setInstallationResult(pkgName: String, result: Boolean) {
        activeDownloads.remove(pkgName)
        downloadInstallerMap.remove(pkgName)
        
        ioScope.launch {
            if (result) {
                _downloadsSharedFlow.emit(pkgName to (InstallStep.Installed to null))
                _downloadsSharedFlow.emit("Finished" to (InstallStep.Installed to null))
            } else {
                _downloadsSharedFlow.emit(pkgName to (InstallStep.Error to null))
            }
        }
    }

    /**
     * Cleans up installation for an extension.
     */
    fun cleanUpInstallation(pkgName: String) {
        activeDownloads.remove(pkgName)
        val sessionId = downloadInstallerMap.remove(pkgName)
        if (sessionId != null) {
            try {
                context.packageManager.packageInstaller.abandonSession(sessionId)
            } catch (_: Exception) {}
        }
    }

    /**
     * Uninstalls the APK with the given package name.
     * Uses system uninstaller for system-installed packages, or deletes private extension file.
     */
    fun uninstallApk(pkgName: String) {
        Logger.d { "[NOVEL_EXT] Uninstalling extension: $pkgName" }
        
        // Check if it's installed as a system package
        if (context.isPackageInstalled(pkgName)) {
            Logger.d { "[NOVEL_EXT] Package is system-installed, using system uninstaller" }
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:$pkgName".toUri())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            // For private extensions, just delete the file
            Logger.d { "[NOVEL_EXT] Package is private extension, deleting file" }
            NovelExtensionLoader.uninstallPrivateExtension(context, pkgName)
            NovelExtensionInstallReceiver.notifyRemoved(context, pkgName)
        }
    }

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val PACKAGE_INSTALLED_ACTION = "eu.kanade.tachiyomi.NOVEL_EXTENSION_INSTALLED"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}

/**
 * Activity that handles novel extension installation.
 * 
 * When launched with an APK URI, it creates a PackageInstaller session and commits it.
 * When receiving a callback from PackageInstaller, it handles the result (user action, success, failure).
 */
class NovelExtensionInstallActivity : Activity() {
    
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Logger.d { "[NOVEL_EXT_INSTALL] Activity started, action=${intent.action}, data=${intent.data}" }
            
            // Handle callback from PackageInstaller
            if (NovelExtensionInstaller.PACKAGE_INSTALLED_ACTION == intent.action) {
                handleInstallCallback()
                finish()
                return
            }
            
            // Handle initial APK installation request
            val uri = intent.data
            val pkgName = intent.getStringExtra(NovelExtensionInstaller.EXTRA_PACKAGE_NAME)
            
            if (uri == null || pkgName == null) {
                Logger.e { "[NOVEL_EXT_INSTALL] Missing URI or package name" }
                finish()
                return
            }
            
            Logger.d { "[NOVEL_EXT_INSTALL] Installing $pkgName from $uri" }
            installApk(pkgName, uri)
            
        } catch (e: Exception) {
            Logger.e(e) { "[NOVEL_EXT_INSTALL] Error in install activity" }
        }
        
        finish()
    }
    
    /**
     * Creates a PackageInstaller session and commits it for installation.
     */
    private fun installApk(pkgName: String, uri: android.net.Uri) {
        try {
            val packageInstaller = packageManager.packageInstaller
            val data = UniFile.fromUri(this, uri)?.openInputStream()
            
            if (data == null) {
                Logger.e { "[NOVEL_EXT_INSTALL] Failed to open APK input stream" }
                NovelExtensionInstallReceiver.notifyInstallationError(applicationContext, pkgName)
                return
            }
            
            val params = SessionParams(SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(USER_ACTION_NOT_REQUIRED)
            }
            
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            
            Logger.d { "[NOVEL_EXT_INSTALL] Created session $sessionId for $pkgName" }
            
            session.openWrite("package", 0, -1).use { packageInSession ->
                data.copyTo(packageInSession)
            }
            data.close()
            
            // Create intent for the callback
            val callbackIntent = Intent(this, NovelExtensionInstallActivity::class.java)
                .setAction(NovelExtensionInstaller.PACKAGE_INSTALLED_ACTION)
                .putExtra(NovelExtensionInstaller.EXTRA_PACKAGE_NAME, pkgName)
                .putExtra(NovelExtensionInstaller.EXTRA_SESSION_ID, sessionId)
            
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            
            val pendingIntent = PendingIntent.getActivity(
                this,
                sessionId,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            )
            
            session.commit(pendingIntent.intentSender)
            
            // Update the extension manager about the session
            val extensionManager = Injekt.get<NovelExtensionManager>()
            extensionManager.setInstalling(pkgName, sessionId)
            
            Logger.d { "[NOVEL_EXT_INSTALL] Committed session $sessionId, waiting for system dialog" }
            
        } catch (e: Exception) {
            Logger.e(e) { "[NOVEL_EXT_INSTALL] Failed to create install session" }
            NovelExtensionInstallReceiver.notifyInstallationError(applicationContext, pkgName)
        }
    }
    
    /**
     * Handles the callback from PackageInstaller after user action.
     */
    private fun handleInstallCallback() {
        val extras = intent.extras ?: return
        val pkgName = extras.getString(NovelExtensionInstaller.EXTRA_PACKAGE_NAME)
        val status = extras.getInt(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        
        Logger.d { "[NOVEL_EXT_INSTALL] Install callback for $pkgName, status=$status" }
        
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // System wants to show install confirmation - launch it
                @Suppress("DEPRECATION")
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    extras.getParcelable(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    Logger.d { "[NOVEL_EXT_INSTALL] Launching system install dialog" }
                    startActivity(confirmIntent)
                } else {
                    Logger.e { "[NOVEL_EXT_INSTALL] No confirm intent in extras" }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Logger.d { "[NOVEL_EXT_INSTALL] Installation succeeded for $pkgName" }
                // Explicitly notify to ensure extension manager refreshes,
                // since system broadcast can be missed or filtered out
                if (pkgName != null) {
                    NovelExtensionInstallReceiver.notifyReplaced(applicationContext, pkgName)
                }
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Logger.d { "[NOVEL_EXT_INSTALL] Installation aborted by user for $pkgName" }
                // Don't notify error for user cancellation
            }
            else -> {
                val message = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Logger.e { "[NOVEL_EXT_INSTALL] Installation failed for $pkgName: $message (status=$status)" }
                if (pkgName != null) {
                    NovelExtensionInstallReceiver.notifyInstallationError(applicationContext, pkgName)
                }
            }
        }
    }
}

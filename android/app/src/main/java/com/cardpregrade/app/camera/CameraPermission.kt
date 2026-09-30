package com.cardpregrade.app.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class PermissionStatus { GRANTED, NOT_REQUESTED, DENIED, PERMANENTLY_DENIED }

/** Camera permission boundary, so tests can simulate granted/denied without system dialogs. */
interface CameraPermission {
    fun isGranted(context: Context): Boolean

    /** Returns a function that launches the request and reports the resulting status. */
    @Composable
    fun rememberRequester(onResult: (PermissionStatus) -> Unit): () -> Unit
}

object AndroidCameraPermission : CameraPermission {
    override fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @Composable
    override fun rememberRequester(onResult: (PermissionStatus) -> Unit): () -> Unit {
        val activity = LocalContext.current.findActivity()
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val status = when {
                granted -> PermissionStatus.GRANTED
                // After a denial, no rationale means the system will not show the dialog again.
                activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) ->
                    PermissionStatus.PERMANENTLY_DENIED
                else -> PermissionStatus.DENIED
            }
            onResult(status)
        }
        return { launcher.launch(Manifest.permission.CAMERA) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

package com.cardpregrade.app

import android.app.Application
import android.content.Context
import com.cardpregrade.app.camera.AndroidCameraPermission
import com.cardpregrade.app.camera.CameraPermission
import com.cardpregrade.app.camera.CameraSource
import com.cardpregrade.app.camera.RealCameraSource
import com.cardpregrade.app.capture.AndroidCaptureInspector
import com.cardpregrade.app.capture.CaptureInspector
import com.cardpregrade.app.capture.CaptureStorage
import com.cardpregrade.core.data.LocalDataSources
import com.cardpregrade.core.data.repository.InspectionRepository
import com.cardpregrade.core.data.repository.ScanRepository
import com.cardpregrade.core.model.ProcessingMode
import kotlinx.coroutines.flow.MutableStateFlow

class CardPregradeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Manual dependency container. Small enough not to need a DI framework yet; can be replaced
 * with Hilt when the graph grows (CameraX, OpenCV, pipeline implementations).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val data by lazy { LocalDataSources.create(appContext) }

    val scanRepository: ScanRepository get() = data.scanRepository
    val inspectionRepository: InspectionRepository get() = data.inspectionRepository

    /** The only mode this build supports. There is no network permission. */
    val processingMode: ProcessingMode = ProcessingMode.LOCAL_ONLY

    /** App-private photo storage: <filesDir>/scans/<sessionId>/. */
    val captureStorage = CaptureStorage(appContext.filesDir)

    val captureInspector: CaptureInspector = AndroidCaptureInspector()

    /** Test seam: instrumented tests swap in a fake camera so they never need camera hardware. */
    var cameraSourceFactory: (Context) -> CameraSource = { RealCameraSource(it) }

    /** Test seam: instrumented tests simulate granted/denied without system dialogs. */
    var cameraPermission: CameraPermission = AndroidCameraPermission

    /** In-memory for now; will move to DataStore with the calibration work in Phase 4. */
    val developerMode = MutableStateFlow(false)
}

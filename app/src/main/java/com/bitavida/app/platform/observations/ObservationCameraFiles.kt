package com.bitavida.app.platform.observations

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

internal data class ObservationCameraCapture(val token: String, val uri: Uri)

internal object ObservationCameraFiles {
    private const val DIRECTORY = "observation_camera"
    private val tokenPattern = Regex("[0-9a-fA-F-]{36}")

    fun create(context: Context): ObservationCameraCapture {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val token = UUID.randomUUID().toString()
        val file = File(directory, "$token.jpg")
        check(file.createNewFile())
        return ObservationCameraCapture(
            token,
            FileProvider.getUriForFile(
                context,
                context.packageName + ".observation.photos",
                file,
            ),
        )
    }

    fun uri(context: Context, token: String): Uri? {
        if (!tokenPattern.matches(token)) return null
        val file = File(File(context.cacheDir, DIRECTORY), "$token.jpg")
        if (!file.isFile) return null
        return FileProvider.getUriForFile(
            context,
            context.packageName + ".observation.photos",
            file,
        )
    }

    fun delete(context: Context, token: String?) {
        if (token == null || !tokenPattern.matches(token)) return
        File(File(context.cacheDir, DIRECTORY), "$token.jpg").delete()
    }
}

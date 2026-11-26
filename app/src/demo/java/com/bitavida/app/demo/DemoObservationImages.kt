package com.bitavida.app.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.bitavida.app.data.local.secure.ObservationPhotoEntity
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.domain.repository.GLUCOSE_SENSITIVE_CONSENT_VERSION
import com.bitavida.app.domain.repository.GlucoseActor
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A locally generated illustration, never a photograph of a real person. */
internal suspend fun ensureDemoObservationImage(context: Context, actor: GlucoseActor, id: String) =
    withContext(Dispatchers.IO) {
        val dao = SecureDatabaseProvider.open(context).observationDao()
        if (dao.photo(id, actor.profileId, actor.personUnderCareId) == null) {
            val vertical = id.endsWith("-vertical")
            val image = Bitmap.createBitmap(if (vertical) 720 else 1200, if (vertical) 1200 else 720, Bitmap.Config.ARGB_8888)
            val bytes = try {
                val canvas = Canvas(image)
                canvas.drawColor(Color.rgb(235, 231, 219))
                val width = image.width.toFloat()
                val height = image.height.toFloat()
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = Color.rgb(206, 190, 164)
                canvas.drawRoundRect(width * .05f, height * .06f, width * .95f, height * .94f, 36f, 36f, paint)
                paint.color = Color.rgb(247, 244, 234)
                canvas.drawRoundRect(width * .14f, height * .30f, width * .86f, height * .70f, 24f, 24f, paint)
                paint.color = Color.rgb(92, 103, 90)
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 32f
                canvas.drawText("IMAGEN DE EJEMPLO", width / 2f, height * .48f, paint)
                paint.textSize = 25f
                canvas.drawText("Ilustración ficticia", width / 2f, height * .55f, paint)
                ByteArrayOutputStream().use { output ->
                    check(image.compress(Bitmap.CompressFormat.JPEG, 88, output))
                    output.toByteArray()
                }
            } finally {
                image.recycle()
            }
            try {
                dao.storePhoto(ObservationPhotoEntity(id, actor.profileId, actor.personUnderCareId, bytes),
                    actor.caregiverId, GLUCOSE_SENSITIVE_CONSENT_VERSION)
            } finally {
                bytes.fill(0)
            }
        }
        val stored = requireNotNull(dao.photo(id, actor.profileId, actor.personUnderCareId))
        check(stored.jpeg.isNotEmpty())
        stored.jpeg.fill(0)
    }

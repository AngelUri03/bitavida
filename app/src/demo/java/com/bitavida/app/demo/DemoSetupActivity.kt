package com.bitavida.app.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bitavida.app.MainActivity
import com.bitavida.app.ui.theme.BitavidaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SCENARIO_VERSION = 1

/** Solo existe en el paquete de demostración, con almacenamiento independiente. */
class DemoSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BitavidaTheme {
                var attempt by remember { mutableIntStateOf(0) }
                var error by remember { mutableStateOf(false) }
                LaunchedEffect(attempt) {
                    error = false
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            val initialization = DemoInitialization(applicationContext)
                            val reference = initialization.reference()
                            if (!initialization.isComplete(SCENARIO_VERSION)) {
                                seedDemoScenario(applicationContext, reference)
                                initialization.markComplete(SCENARIO_VERSION)
                            }
                        }
                    }
                    if (result.isSuccess) {
                        startActivity(Intent(this@DemoSetupActivity, MainActivity::class.java))
                        finish()
                    } else {
                        error = true
                    }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Bitavida Demo", style = MaterialTheme.typography.headlineSmall)
                        if (error) {
                            Text("No se pudo preparar el escenario. Puedes reintentar sin duplicar los datos ya creados.")
                            Button(onClick = { attempt++ }) { Text("Reintentar") }
                        } else {
                            Text("Preparando datos ficticios para la demostración…")
                        }
                    }
                }
            }
        }
    }
}

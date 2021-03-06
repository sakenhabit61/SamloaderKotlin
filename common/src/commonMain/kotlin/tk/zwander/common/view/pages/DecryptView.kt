package tk.zwander.common.view.pages

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import io.ktor.utils.io.core.internal.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tk.zwander.common.data.DecryptFileInfo
import tk.zwander.common.model.DecryptModel
import tk.zwander.common.tools.Crypt
import tk.zwander.common.util.imageResource
import tk.zwander.common.view.HybridButton
import tk.zwander.common.view.MRFLayout
import tk.zwander.common.view.ProgressInfo
import kotlin.time.ExperimentalTime

expect object PlatformDecryptView {
    suspend fun getInput(callback: suspend CoroutineScope.(DecryptFileInfo?) -> Unit)
}

@DangerousInternalIoApi
@ExperimentalTime
@Composable
fun DecryptView(model: DecryptModel, scrollState: ScrollState) {
    val canDecrypt = model.fileToDecrypt != null && model.job == null
            && model.fw.isNotBlank() && model.model.isNotBlank() && model.region.isNotBlank()

    val canChangeOption = model.job == null

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        val rowSize = remember { mutableStateOf(0.dp) }
        Row(
            modifier = Modifier.fillMaxWidth()
                .onSizeChanged { rowSize.value = it.width.dp }
        ) {
            HybridButton(
                onClick = {
                    model.job = model.scope.launch(Dispatchers.Main) {
                        val info = model.fileToDecrypt!!

                        val key = if (info.fileName.endsWith(".enc2")) Crypt.getV2Key(
                            model.fw,
                            model.model,
                            model.region
                        ) else
                            Crypt.getV4Key(model.fw, model.model, model.region)

                        Crypt.decryptProgress(info.input, info.output, key, info.inputSize) { current, max, bps ->
                            model.progress = current to max
                            model.speed = bps
                        }

                        model.endJob("Done")
                    }
                },
                enabled = canDecrypt,
                text = "Decrypt",
                description = "Decrypt Firmware",
                icon = imageResource("decrypt.png"),
                parentSize = rowSize.value
            )
            Spacer(Modifier.width(8.dp))
            HybridButton(
                onClick = {
                    model.scope.launch {
                        PlatformDecryptView.getInput { info ->
                            if (info != null) {
                                if (!info.fileName.endsWith(".enc2") && !info.fileName.endsWith(".enc4")) {
                                    model.endJob("Please select an encrypted firmware file ending in enc2 or enc4.")
                                } else {
                                    model.fileToDecrypt = info
                                    model.endJob("")
                                }
                            }
                        }
                    }
                },
                enabled = canChangeOption,
                text = "Open File",
                description = "Open File to Decrypt",
                icon = imageResource("open.png"),
                parentSize = rowSize.value
            )
            Spacer(Modifier.weight(1f))
            HybridButton(
                onClick = {
                    model.endJob("")
                },
                enabled = model.job != null,
                text = "Cancel",
                description = "Cancel",
                icon = imageResource("cancel.png"),
                parentSize = rowSize.value
            )
        }

        Spacer(Modifier.height(8.dp))

        MRFLayout(model, canChangeOption, canChangeOption)

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = model.fileToDecrypt?.inputPath ?: "",
                onValueChange = {},
                label = { Text("File") },
                modifier = Modifier.weight(1f),
                readOnly = true,
                singleLine = true,
            )
        }

        Spacer(Modifier.height(16.dp))

        ProgressInfo(model)
    }
}
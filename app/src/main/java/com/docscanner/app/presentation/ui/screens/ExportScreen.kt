package com.docscanner.app.presentation.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docscanner.app.presentation.ui.components.AppAssets
import com.docscanner.app.presentation.ui.components.PngIconButton
import com.docscanner.app.presentation.ui.components.PngIconRow
import com.docscanner.app.presentation.viewmodel.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: ScannerViewModel,
    onNewScan: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Export Document") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            state.message?.let { Text(text = it, modifier = Modifier.padding(bottom = 12.dp)) }
            state.lastSavedPath?.let {
                Text(text = "File saved to: $it", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(24.dp))

            PngIconRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                PngIconButton(
                    iconRes = AppAssets.save,
                    contentDescription = "Save JPEG",
                    onClick = { viewModel.saveJpeg() },
                    enabled = !state.isProcessing,
                    label = "JPEG",
                    size = 56.dp,
                    modifier = Modifier.weight(1f)
                )
                PngIconButton(
                    iconRes = AppAssets.exportPdf,
                    contentDescription = "Save PDF",
                    onClick = { viewModel.savePdf() },
                    enabled = !state.isProcessing,
                    label = "PDF",
                    size = 56.dp,
                    modifier = Modifier.weight(1f)
                )
                PngIconButton(
                    iconRes = AppAssets.share,
                    contentDescription = "Share",
                    onClick = {
                        val uri = viewModel.getShareUri() ?: return@PngIconButton
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = if (state.lastSavedPath?.endsWith(".pdf") == true) "application/pdf" else "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share document"))
                    },
                    enabled = state.lastSavedPath != null,
                    label = "Share",
                    size = 56.dp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onNewScan,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isProcessing
            ) {
                Text("Start New Scan")
            }
        }
    }
}

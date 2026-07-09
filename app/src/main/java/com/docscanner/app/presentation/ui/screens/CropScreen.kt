package com.docscanner.app.presentation.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.docscanner.app.presentation.ui.components.AppAssets
import com.docscanner.app.presentation.ui.components.CropOverlay
import com.docscanner.app.presentation.ui.components.PngIconButton
import com.docscanner.app.presentation.ui.components.PngIconRow
import com.docscanner.app.presentation.viewmodel.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    viewModel: ScannerViewModel,
    onApply: () -> Unit,
    onRetake: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text(if (state.isAdjustingEdges) "Adjust Edges" else "Crop & Perspective") }
            ) 
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp, top = 12.dp)
                ) {
                    if (state.isAdjustingEdges) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { viewModel.applyAdjustedEdges() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Done")
                            }
                            OutlinedButton(
                                onClick = { viewModel.discardAdjustedEdges() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Discard")
                            }
                        }
                    } else {
                        PngIconRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            PngIconButton(
                                iconRes = AppAssets.rotate,
                                contentDescription = "Rotate",
                                onClick = { viewModel.rotateCaptured() },
                                enabled = !state.isProcessing,
                                label = "Rotate",
                                modifier = Modifier.weight(1f)
                            )
                            PngIconButton(
                                iconRes = AppAssets.crop,
                                contentDescription = "Adjust Crop",
                                onClick = { viewModel.startAdjustingEdges() },
                                enabled = !state.isProcessing && state.currentCapturedBitmap != null,
                                label = "Adjust Edges",
                                modifier = Modifier.weight(1f)
                            )
                            PngIconButton(
                                iconRes = AppAssets.save, // reusing save icon for "Continue"
                                contentDescription = "Continue",
                                onClick = onApply,
                                enabled = !state.isProcessing && state.croppedBitmap != null,
                                label = "Continue",
                                modifier = Modifier.weight(1f)
                            )
                            PngIconButton(
                                iconRes = AppAssets.delete,
                                contentDescription = "Retake",
                                onClick = onRetake,
                                label = "Retake",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            val originalBitmap = state.currentCapturedBitmap
            val croppedBitmap = state.croppedBitmap
            
            if (state.isAdjustingEdges && originalBitmap != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = originalBitmap.asImageBitmap(),
                        contentDescription = "Original",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    CropOverlay(
                        corners = state.tempCorners,
                        onCornersUpdated = { viewModel.updateTempCorners(it) },
                        imageWidth = originalBitmap.width,
                        imageHeight = originalBitmap.height
                    )
                }
            } else if (croppedBitmap != null) {
                Image(
                    bitmap = croppedBitmap.asImageBitmap(),
                    contentDescription = "Cropped Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            if (state.isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
            }
        }
    }
}

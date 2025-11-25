package com.example.mymouse.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.wear.compose.material.Text
import java.util.Calendar
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.example.mymouse.bluetooth.BluetoothHidHelper
import com.example.mymouse.presentation.theme.MyMouseTheme
import com.example.mymouse.sensor.SensorHelper

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothHidHelper: BluetoothHidHelper
    private lateinit var sensorHelper: SensorHelper
    
    // State to track mouse buttons
    private var isLeftBtnDown = false
    private var isRightBtnDown = false
    private var isMouseEnabled = false
    
    // Mutable state for tilt to share with Composable
    private val tiltState = mutableStateOf(Pair(0f, 0f))
    
    // Mutable state for movement status
    private val movementStatus = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        bluetoothHidHelper = BluetoothHidHelper(this) { status ->
            runOnUiThread {
            }
        }

        sensorHelper = SensorHelper(this, 
            onMove = { dx, dy ->
                if (isMouseEnabled) {
                    bluetoothHidHelper.sendMouseReport(dx, dy, isLeftBtnDown, isRightBtnDown)
                    
                    // Update movement status
                    val direction = StringBuilder()
                    if (dy < -2) direction.append("Moving Up ")
                    if (dy > 2) direction.append("Moving Down ")
                    if (dx < -2) direction.append("Moving Left ")
                    if (dx > 2) direction.append("Moving Right ")
                    
                    if (direction.isNotEmpty()) {
                        movementStatus.value = direction.toString()
                    } else {
                        movementStatus.value = ""
                    }
                }
            },
            onTilt = { x, y ->
                tiltState.value = Pair(x, y)
            }
        )

        setContent {
            WearApp(
                bluetoothHidHelper = bluetoothHidHelper,
                tiltState = tiltState,
                movementStatusState = movementStatus,
                onEnableMouse = { enabled ->
                    isMouseEnabled = enabled
                    if (enabled) sensorHelper.start() else sensorHelper.stop()
                },
                onLeftClick = { down ->
                    isLeftBtnDown = down
                    if (isMouseEnabled) bluetoothHidHelper.sendMouseReport(0, 0, isLeftBtnDown, isRightBtnDown)
                },
                onRightClick = { down ->
                    isRightBtnDown = down
                    if (isMouseEnabled) bluetoothHidHelper.sendMouseReport(0, 0, isLeftBtnDown, isRightBtnDown)
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothHidHelper.cleanup()
        sensorHelper.stop()
    }
}

@Composable
fun WearApp(
    bluetoothHidHelper: BluetoothHidHelper,
    tiltState: androidx.compose.runtime.State<Pair<Float, Float>>,
    movementStatusState: androidx.compose.runtime.State<String>,
    onEnableMouse: (Boolean) -> Unit,
    onLeftClick: (Boolean) -> Unit,
    onRightClick: (Boolean) -> Unit
) {
    var statusText by remember { mutableStateOf("Initializing...") }
    var isMouseActive by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }
    
    // Tilt state from Activity
    val tiltX = tiltState.value.first
    val tiltY = tiltState.value.second
    
    // Movement status
    val movementStatus = movementStatusState.value

    // Permission Launcher
    val permissions = mutableListOf<String>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        add(Manifest.permission.HIGH_SAMPLING_RATE_SENSORS)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        hasPermissions = allGranted
        if (allGranted) {
            bluetoothHidHelper.init()
        } else {
            statusText = "Permissions Missing"
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions.toTypedArray())
    }

    MyMouseTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (isMouseActive) {
                                onLeftClick(true)
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ onLeftClick(false) }, 50)
                            }
                        },
                        onLongPress = {
                            // Toggle Mouse on Long Press to avoid accidental toggles during clicking
                            isMouseActive = !isMouseActive
                            onEnableMouse(isMouseActive)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Compass / Watch Face
            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2
                
                // Draw Dial
                drawCircle(
                    color = if (isMouseActive) Color(0xFF00FF00) else Color.DarkGray,
                    style = Stroke(width = 4.dp.toPx()),
                    radius = radius
                )

                // Draw Ticks
                for (i in 0 until 12) {
                    val angle = (i * 30.0 * Math.PI / 180.0).toFloat()
                    val startRadius = radius - 10.dp.toPx()
                    val endRadius = radius
                    val start = center + Offset(
                        (startRadius * Math.sin(angle.toDouble())).toFloat(),
                        -(startRadius * Math.cos(angle.toDouble())).toFloat()
                    )
                    val end = center + Offset(
                        (endRadius * Math.sin(angle.toDouble())).toFloat(),
                        -(endRadius * Math.cos(angle.toDouble())).toFloat()
                    )
                    drawLine(
                        color = Color.White,
                        start = start,
                        end = end,
                        strokeWidth = 2.dp.toPx()
                    )
                }

                // Calculate Needle Angle from Tilt
                // atan2(y, x) gives angle. 
                // We want the needle to point in the direction of "down" (gravity).
                // Accelerometer values:
                // Holding watch flat: Z ~ 9.8, X ~ 0, Y ~ 0
                // Tilted forward (12 down): Y > 0
                // Tilted right (3 down): X < 0 (usually) - let's test or assume standard
                // Standard Android: X axis points right, Y axis points up (relative to screen).
                // If I tilt right side down, gravity has +X component? No, if right side is down, gravity vector (pointing down) has +X component in device frame.
                // Let's just use the raw vector (tiltX, tiltY) as the direction.
                
                // If tilt is significant, draw needle.
                val magnitude = Math.sqrt((tiltX * tiltX + tiltY * tiltY).toDouble())
                
                if (magnitude > 1.0) { // Threshold to avoid jitter when flat
                    val angleRad = Math.atan2(-tiltX.toDouble(), tiltY.toDouble()) // Swap/Negate to match screen coords
                    
                    // Draw Needle
                    val needleLength = radius * 0.7f
                    val needleEnd = center + Offset(
                        (needleLength * Math.sin(angleRad)).toFloat(),
                        -(needleLength * Math.cos(angleRad)).toFloat()
                    )
                    
                    drawLine(
                        color = Color.Red,
                        start = center,
                        end = needleEnd,
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    // Draw a dot if flat
                     drawCircle(
                        color = Color.Red,
                        radius = 8.dp.toPx(),
                        center = center
                    )
                }
                
                drawCircle(
                    color = Color.Red,
                    radius = 4.dp.toPx(),
                    center = center
                )
            }
            
            // Movement Status Text (Center)
            if (isMouseActive && movementStatus.isNotEmpty()) {
                Text(
                    text = movementStatus,
                    color = Color.Yellow,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            // Status Text (Small, at bottom)
            if (!hasPermissions || !isMouseActive) {
                Text(
                    text = if (!hasPermissions) "No Perms" else "Long Press to Start",
                    color = Color.Gray,
                    style = MaterialTheme.typography.caption2,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                )
            }
        }
    }
}
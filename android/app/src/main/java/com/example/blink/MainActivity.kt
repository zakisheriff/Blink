package com.example.blink

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.blink.data.Device
import com.example.blink.data.TransferItem
import com.example.blink.data.TransferProgress
import com.example.blink.ui.components.BlinkButton
import com.example.blink.ui.components.BlinkCard
import com.example.blink.ui.components.BlinkNavBar
import com.example.blink.ui.components.BlinkTextField
import com.example.blink.ui.theme.BlinkTheme
import com.example.blink.utils.*
import com.example.blink.viewmodel.BlinkViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
        private val viewModel: BlinkViewModel by viewModels()

        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                setContent { BlinkTheme { BlinkApp(viewModel) } }
        }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
        object Devices : Screen("devices", "Devices", Icons.Default.Phone)
        object Send : Screen("send", "Send", Icons.Default.Send)
        object Transfers : Screen("transfers", "Transfers", Icons.Default.List)
        object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BlinkApp(viewModel: BlinkViewModel) {
        val navController = rememberNavController()
        val showQRScanner by viewModel.showQRScanner.collectAsState()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // Request permissions
        val permissions =
                mutableListOf(
                        Manifest.permission.INTERNET,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.CHANGE_WIFI_STATE,
                        Manifest.permission.CAMERA
                )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val permissionsState = rememberMultiplePermissionsState(permissions)

        LaunchedEffect(Unit) {
                if (!permissionsState.allPermissionsGranted) {
                        permissionsState.launchMultiplePermissionRequest()
                }
        }

        Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                        BlinkNavBar(
                                items =
                                        listOf(
                                                Triple(
                                                        Screen.Devices.route,
                                                        "Devices",
                                                        Screen.Devices.icon
                                                ),
                                                Triple(Screen.Send.route, "Send", Screen.Send.icon),
                                                Triple(
                                                        Screen.Transfers.route,
                                                        "Transfers",
                                                        Screen.Transfers.icon
                                                ),
                                                Triple(
                                                        Screen.Settings.route,
                                                        "Settings",
                                                        Screen.Settings.icon
                                                )
                                        ),
                                currentRoute = currentRoute,
                                onNavigate = { route ->
                                        navController.navigate(route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                        saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                        }
                                }
                        )
                }
        ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        NavHost(
                                navController = navController,
                                startDestination = Screen.Devices.route,
                                modifier = Modifier.fillMaxSize()
                        ) {
                                composable(Screen.Devices.route) { DevicesScreen(viewModel) }
                                composable(Screen.Send.route) { SendScreen(viewModel) }
                                composable(Screen.Transfers.route) { TransfersScreen(viewModel) }
                                composable(Screen.Settings.route) { SettingsScreen(viewModel) }
                        }

                        // Show QR Scanner dialog overlay
                        if (showQRScanner && permissionsState.allPermissionsGranted) {
                                com.example.blink.ui.QRCodeScanner(
                                        onQRCodeScanned = { qrCode ->
                                                viewModel.handleQRCodeScanned(qrCode)
                                        },
                                        onDismiss = { viewModel.toggleQRScanner() }
                                )
                        }
                }
        }
}

// DEVICES SCREEN
@Composable
fun DevicesScreen(viewModel: BlinkViewModel) {
        val discoveredDevices by viewModel.discoveredDevices.collectAsState()
        val selectedDevice by viewModel.selectedDevice.collectAsState()
        val isDiscovering by viewModel.isDiscovering.collectAsState()

        var showManualDialog by remember { mutableStateOf(false) }
        var manualIp by remember { mutableStateOf("") }

        if (showManualDialog) {
                AlertDialog(
                        onDismissRequest = { showManualDialog = false },
                        title = {
                                Text(
                                        "Connect Manually",
                                        style = MaterialTheme.typography.titleLarge
                                )
                        },
                        text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                                "Enter Mac IP Address:",
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                        BlinkTextField(
                                                value = manualIp,
                                                onValueChange = { manualIp = it },
                                                placeholder = "192.168.1.xxx"
                                        )
                                }
                        },
                        confirmButton = {
                                BlinkButton(
                                        text = "Connect",
                                        onClick = {
                                                if (manualIp.isNotBlank()) {
                                                        viewModel.addManualDevice(manualIp)
                                                        showManualDialog = false
                                                }
                                        }
                                )
                        },
                        dismissButton = {
                                TextButton(onClick = { showManualDialog = false }) {
                                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                                }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
        }

        Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
                // Header
                Text(
                        text = "Devices",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 16.dp)
                )

                // Status Card
                BlinkCard(
                        backgroundColor =
                                if (isDiscovering) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surface
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Box(
                                        modifier =
                                                Modifier.size(48.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                if (isDiscovering)
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.1f
                                                                        )
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface.copy(
                                                                                alpha = 0.1f
                                                                        )
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector =
                                                        if (isDiscovering) Icons.Default.Refresh
                                                        else Icons.Default.Info,
                                                contentDescription = null,
                                                tint =
                                                        if (isDiscovering)
                                                                MaterialTheme.colorScheme.primary
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                        )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                text =
                                                        if (isDiscovering)
                                                                "Searching for devices..."
                                                        else "Discovery Paused",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                                text =
                                                        "${discoveredDevices.size} device(s) found nearby",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }

                                if (isDiscovering) {
                                        CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                }
                        }
                }

                // Action Buttons
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        // QR Code Scan Button
                        BlinkButton(
                                text = "Scan QR",
                                onClick = { viewModel.toggleQRScanner() },
                                icon = Icons.Default.Star,
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        // Code Connection Button
                        var showCodeDialog by remember { mutableStateOf(false) }
                        if (showCodeDialog) {
                                CodeConnectionDialog(
                                        onDismiss = { showCodeDialog = false },
                                        onConnect = { code -> viewModel.connectWithCode(code) },
                                        onHost = { code -> viewModel.startHosting(code) }
                                )
                        }

                        BlinkButton(
                                text = "Code",
                                onClick = { showCodeDialog = true },
                                icon = Icons.Default.Lock,
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                }

                // Manual Connection Link
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                                text = "Or connect via IP Address",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier =
                                        Modifier.clickable { showManualDialog = true }.padding(8.dp)
                        )
                }

                // Devices List
                Text(
                        text = "Available Devices",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                )

                if (discoveredDevices.isEmpty()) {
                        Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                        ) {
                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(64.dp),
                                                tint =
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.2f)
                                        )
                                        Text(
                                                text = "No devices found yet",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                        }
                } else {
                        LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 100.dp) // Space for nav bar
                        ) {
                                items(discoveredDevices) { device ->
                                        DeviceCard(
                                                device = device,
                                                isSelected = device.id == selectedDevice?.id,
                                                onClick = { viewModel.selectDevice(device) }
                                        )
                                }
                        }
                }
        }
}

@Composable
fun DeviceCard(device: Device, isSelected: Boolean, onClick: () -> Unit) {
        val backgroundColor by
                animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        label = "cardBg"
                )

        BlinkCard(onClick = onClick, backgroundColor = backgroundColor) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier =
                                        Modifier.size(56.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        if (isSelected)
                                                                MaterialTheme.colorScheme.primary
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector =
                                                if (device.deviceType == Device.DeviceType.ANDROID)
                                                        Icons.Default.Phone
                                                else Icons.Default.Home,
                                        contentDescription = null,
                                        tint =
                                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                        text = device.ipAddress,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }

                        if (isSelected) {
                                Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                )
                        }
                }
        }
}

// SEND SCREEN
@Composable
fun SendScreen(viewModel: BlinkViewModel) {
        val selectedFiles by viewModel.selectedFiles.collectAsState()
        val selectedDevice by viewModel.selectedDevice.collectAsState()

        val filePickerLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetMultipleContents()
                ) { uris ->
                        if (uris.isNotEmpty()) {
                                viewModel.addFiles(uris)
                        }
                }

        Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
                // Header
                Text(
                        text = "Send Files",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 16.dp)
                )

                // Selected Device Card
                if (selectedDevice != null) {
                        BlinkCard(backgroundColor = MaterialTheme.colorScheme.primaryContainer) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Box(
                                                modifier =
                                                        Modifier.size(48.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Phone,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = "Sending to",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .labelMedium,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer.copy(
                                                                        alpha = 0.7f
                                                                )
                                                )
                                                Text(
                                                        text = selectedDevice!!.name,
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer
                                                )
                                        }
                                }
                        }
                } else {
                        BlinkCard(backgroundColor = MaterialTheme.colorScheme.errorContainer) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                                text =
                                                        "Please select a device from the Devices tab first",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                }
                        }
                }

                // File Selection
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                text = "Selected Files",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                        )
                        if (selectedFiles.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearFiles() }) {
                                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                                }
                        }
                }

                if (selectedFiles.isEmpty()) {
                        Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                        ) {
                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.AddCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(64.dp),
                                                tint =
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.2f)
                                        )
                                        Text(
                                                text = "No files selected",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        BlinkButton(
                                                text = "Choose Files",
                                                onClick = { filePickerLauncher.launch("*/*") },
                                                icon = Icons.Default.Add,
                                                containerColor =
                                                        MaterialTheme.colorScheme.secondary,
                                                contentColor = MaterialTheme.colorScheme.onSecondary
                                        )
                                }
                        }
                } else {
                        LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                                items(selectedFiles) { item ->
                                        FileItemCard(
                                                item = item,
                                                onRemove = { viewModel.removeFile(item) }
                                        )
                                }
                        }

                        // Send Button
                        BlinkButton(
                                text = "Send ${selectedFiles.size} Files",
                                onClick = { viewModel.sendFiles() },
                                icon = Icons.Default.Send,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedDevice != null
                        )
                }
        }
}

@Composable
fun FileItemCard(item: TransferItem, onRemove: () -> Unit) {
        BlinkCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier =
                                        Modifier.size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.1f
                                                        )
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector =
                                                Icons.Default
                                                        .Star, // Placeholder for file type icon
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                        text = item.size.formatFileSize(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }

                        IconButton(onClick = onRemove) {
                                Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                        }
                }
        }
}

// TRANSFERS SCREEN
@Composable
fun TransfersScreen(viewModel: BlinkViewModel) {
        val activeTransfers by viewModel.activeTransfers.collectAsState()

        Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
                Text(
                        text = "Active Transfers",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 16.dp)
                )

                if (activeTransfers.isEmpty()) {
                        Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                        ) {
                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Done,
                                                contentDescription = null,
                                                modifier = Modifier.size(64.dp),
                                                tint =
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.2f)
                                        )
                                        Text(
                                                text = "No active transfers",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                        }
                } else {
                        LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                                items(activeTransfers) { transfer ->
                                        TransferCard(
                                                transfer = transfer,
                                                onCancel = { viewModel.cancelTransfer(transfer.id) }
                                        )
                                }
                        }
                }
        }
}

@Composable
fun TransferCard(transfer: TransferProgress, onCancel: () -> Unit) {
        BlinkCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface
        ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Box(
                                        modifier =
                                                Modifier.size(48.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.1f)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Star, // Placeholder
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                text = transfer.item.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                                text = transfer.item.size.formatFileSize(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }

                                IconButton(onClick = onCancel) {
                                        Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(
                                        progress = { transfer.progress },
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                        if (transfer.speed > 0) {
                                                Text(
                                                        text = transfer.speed.formatSpeed(),
                                                        style =
                                                                MaterialTheme.typography
                                                                        .labelMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                )
                                        }

                                        transfer.eta?.let { eta ->
                                                Text(
                                                        text = "ETA: ${eta.formatETA()}",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .labelMedium,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }

                                        Text(
                                                text = "${(transfer.progress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                }
                        }

                        transfer.error?.let { error ->
                                Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier =
                                                Modifier.background(
                                                                MaterialTheme.colorScheme.error
                                                                        .copy(alpha = 0.1f),
                                                                RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(8.dp)
                                                        .fillMaxWidth()
                                )
                        }
                }
        }
}

// SETTINGS SCREEN
@Composable
fun SettingsScreen(viewModel: BlinkViewModel) {
        val showQRCode by viewModel.showQRCode.collectAsState()

        Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
                Text(
                        text = "Settings",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 16.dp)
                )

                // QR Code Section
                BlinkCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colorScheme.surface
                ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Row(
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(40.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.1f
                                                                                        )
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Share,
                                                                contentDescription = null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                modifier = Modifier.size(20.dp)
                                                        )
                                                }
                                                Text(
                                                        text = "QR Code Pairing",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                )
                                        }
                                        Switch(
                                                checked = showQRCode,
                                                onCheckedChange = { viewModel.toggleQRCode() },
                                                colors =
                                                        SwitchDefaults.colors(
                                                                checkedThumbColor =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                checkedTrackColor =
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer,
                                                                uncheckedThumbColor =
                                                                        MaterialTheme.colorScheme
                                                                                .outline,
                                                                uncheckedTrackColor =
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                        )
                                        )
                                }

                                if (showQRCode) {
                                        HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant
                                        )

                                        val qrBitmap = remember {
                                                generateQRCode(viewModel.getQRCodeJson(), 400)
                                        }

                                        Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                                Text(
                                                        text =
                                                                "Scan this QR code from another device",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )

                                                qrBitmap?.let { bitmap ->
                                                        androidx.compose.foundation.Image(
                                                                bitmap = bitmap.asImageBitmap(),
                                                                contentDescription = "QR Code",
                                                                modifier =
                                                                        Modifier.size(250.dp)
                                                                                .clip(
                                                                                        RoundedCornerShape(
                                                                                                24.dp
                                                                                        )
                                                                                )
                                                                                .background(
                                                                                        Color.White
                                                                                )
                                                                                .padding(16.dp)
                                                        )
                                                }
                                                        ?: CircularProgressIndicator(
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                        }
                                }
                        }
                }

                // Network Diagnostics
                BlinkCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Box(
                                                modifier =
                                                        Modifier.size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .secondary.copy(
                                                                                alpha = 0.1f
                                                                        )
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                        }
                                        Text(
                                                text = "Network Diagnostics",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                }

                                Text(
                                        text = viewModel.getDiagnostics(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily =
                                                androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = 0.5f
                                                                ),
                                                                RoundedCornerShape(12.dp)
                                                        )
                                                        .padding(12.dp)
                                )
                        }
                }

                // App Info
                BlinkCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Box(
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                MaterialTheme.colorScheme.tertiary
                                                                        .copy(alpha = 0.1f)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(20.dp)
                                        )
                                }
                                Column {
                                        Text(
                                                text = "Blink",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                                text = "Version 1.0 • Ultra-fast file transfer",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                        }
                }
        }
}

@Composable
fun CodeConnectionDialog(
        onDismiss: () -> Unit,
        onConnect: (String) -> Unit,
        onHost: (String) -> Unit
) {
        var selectedTab by remember { mutableStateOf(0) }
        var enteredCode by remember { mutableStateOf("") }
        var generatedCode by remember { mutableStateOf("") }

        LaunchedEffect(selectedTab) {
                if (selectedTab == 0 && generatedCode.isEmpty()) {
                        generatedCode = String.format("%06d", (0..999999).random())
                        onHost(generatedCode)
                }
        }

        AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = { Text("Connect with Code") },
                text = {
                        Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                TabRow(
                                        selectedTabIndex = selectedTab,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        indicator = { tabPositions ->
                                                TabRowDefaults.Indicator(
                                                        modifier =
                                                                Modifier.tabIndicatorOffset(
                                                                        tabPositions[selectedTab]
                                                                ),
                                                        color = MaterialTheme.colorScheme.primary
                                                )
                                        }
                                ) {
                                        Tab(
                                                selected = selectedTab == 0,
                                                onClick = { selectedTab = 0 },
                                                text = { Text("Host") }
                                        )
                                        Tab(
                                                selected = selectedTab == 1,
                                                onClick = { selectedTab = 1 },
                                                text = { Text("Join") }
                                        )
                                }

                                if (selectedTab == 0) {
                                        // Host Mode
                                        Text(
                                                "Your Connection Code",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Text(
                                                text = generatedCode,
                                                style = MaterialTheme.typography.displayMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier =
                                                        Modifier.background(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant,
                                                                        RoundedCornerShape(8.dp)
                                                                )
                                                                .padding(16.dp)
                                        )

                                        Text(
                                                "Enter this code on the other device",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                } else {
                                        // Join Mode
                                        Text(
                                                "Enter Connection Code",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )

                                        BlinkTextField(
                                                value = enteredCode,
                                                onValueChange = {
                                                        if (it.length <= 6) enteredCode = it
                                                },
                                                placeholder = "000000",
                                                modifier = Modifier.width(200.dp)
                                        )
                                }
                        }
                },
                confirmButton = {
                        if (selectedTab == 1) {
                                BlinkButton(
                                        text = "Connect",
                                        onClick = {
                                                if (enteredCode.length == 6) {
                                                        onConnect(enteredCode)
                                                        onDismiss()
                                                }
                                        },
                                        enabled = enteredCode.length == 6,
                                        modifier = Modifier.height(40.dp)
                                )
                        } else {
                                TextButton(onClick = onDismiss) {
                                        Text("Done", color = MaterialTheme.colorScheme.primary)
                                }
                        }
                },
                dismissButton = {
                        TextButton(onClick = onDismiss) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                }
        )
}

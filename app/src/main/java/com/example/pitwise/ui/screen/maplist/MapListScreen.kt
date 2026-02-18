package com.example.pitwise.ui.screen.maplist

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pitwise.data.local.entity.MapEntry
import com.example.pitwise.ui.theme.PitwiseBorder
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapListScreen(
    onOpenMap: (Long) -> Unit,
    onOpenBaseMap: () -> Unit = {},
    viewModel: MapListViewModel = hiltViewModel()
) {
    val maps by viewModel.maps.collectAsState()
    val context = LocalContext.current
    var showAddSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MapEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<MapEntry?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=PDF, 1=DXF

    val tabTitles = listOf("Peta PDF", "Peta DXF")
    val filteredMaps = maps.filter { map ->
        when (selectedTab) {
            0 -> map.type == "PDF"
            1 -> map.type == "DXF"
            else -> true
        }
    }

    // DXF file picker
    val dxfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "DXF Map"
                viewModel.addMap(name, "DXF", uri.toString())
            }
        }
    }

    // PDF file picker
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "PDF Map"
                viewModel.addMap(name, "PDF", uri.toString())
            }
        }
    }

    // ── Add Map Bottom Sheet ──
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = PitwiseSurface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    "Tambah Peta",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Pilih tipe file peta yang ingin diimpor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PitwiseGray400
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // DXF option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .background(
                                Color(0xFF4CAF50).copy(alpha = 0.08f),
                                RoundedCornerShape(16.dp)
                            )
                            .border(
                                1.dp,
                                Color(0xFF4CAF50).copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                showAddSheet = false
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                dxfPicker.launch(intent)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.InsertDriveFile, null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "DXF File",
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // PDF option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .background(
                                Color(0xFF2196F3).copy(alpha = 0.08f),
                                RoundedCornerShape(16.dp)
                            )
                            .border(
                                1.dp,
                                Color(0xFF2196F3).copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                showAddSheet = false
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/pdf"
                                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                pdfPicker.launch(intent)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Description, null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "PDF File",
                                color = Color(0xFF2196F3),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // ── Rename Dialog ──
    renameTarget?.let { map ->
        var newName by remember { mutableStateOf(map.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameMap(map.id, newName)
                    renameTarget = null
                }) { Text("Rename", color = PitwisePrimary) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Batal", color = PitwiseGray400)
                }
            },
            title = { Text("Rename Peta", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PitwisePrimary,
                        unfocusedBorderColor = PitwiseBorder,
                        cursorColor = PitwisePrimary
                    )
                )
            },
            containerColor = PitwiseSurface
        )
    }

    // ── Delete Confirmation ──
    deleteTarget?.let { map ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMap(map)
                    deleteTarget = null
                }) { Text("Hapus", color = Color(0xFFCF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Batal", color = PitwiseGray400)
                }
            },
            title = { Text("Hapus Peta?", color = Color.White) },
            text = { Text("\"${map.name}\" akan dihapus permanen.", color = PitwiseGray400) },
            containerColor = PitwiseSurface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PUSTAKA PETA",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    // Base Map Preview Box
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(width = 100.dp, height = 44.dp)
                            .background(
                                Color(0xFF2196F3).copy(alpha = 0.12f),
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                Color(0xFF2196F3).copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenBaseMap() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Satellite,
                                contentDescription = "Base Map",
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Base Map",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF2196F3),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = PitwisePrimary,
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, "Tambah peta")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Tab Row: PDF | DXF ──
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = PitwiseSurface,
                contentColor = PitwisePrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        height = 3.dp,
                        color = PitwisePrimary
                    )
                },
                divider = {}
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) PitwisePrimary else PitwiseGray400
                            )
                        }
                    )
                }
            }

            if (filteredMaps.isEmpty()) {
                // ── Empty State ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (selectedTab == 0) Icons.Default.Description
                        else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = PitwiseGray400,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Belum ada peta ${tabTitles[selectedTab]}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PitwiseGray400
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tekan + untuk mengimpor file peta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PitwiseGray400.copy(alpha = 0.7f)
                    )
                }
            } else {
                // ── Map List ──
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredMaps, key = { it.id }) { map ->
                        MapCardItem(
                            map = map,
                            onOpenMap = { onOpenMap(map.id) },
                            onRename = { renameTarget = map },
                            onDelete = { deleteTarget = map }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════
// ── Map Card Item (Avenza-style) ──
// ════════════════════════════════════════════════════

@Composable
private fun MapCardItem(
    map: MapEntry,
    onOpenMap: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    var showMenu by remember { mutableStateOf(false) }

    val typeColor = if (map.type == "DXF") Color(0xFF4CAF50) else Color(0xFF2196F3)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PitwiseSurface, RoundedCornerShape(14.dp))
            .border(1.dp, PitwiseBorder, RoundedCornerShape(14.dp))
            .clickable { onOpenMap() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Type icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(typeColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (map.type == "DXF") Icons.Default.InsertDriveFile
                else Icons.Default.Description,
                contentDescription = null,
                tint = typeColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Name and meta
        Column(modifier = Modifier.weight(1f)) {
            Text(
                map.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type badge
                Box(
                    modifier = Modifier
                        .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        map.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    dateFormat.format(Date(map.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = PitwiseGray400
                )
            }
        }

        // More menu
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.MoreVert, "Menu",
                    tint = PitwiseGray400,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = PitwiseSurface
            ) {
                DropdownMenuItem(
                    text = { Text("Rename", color = Color.White) },
                    onClick = {
                        showMenu = false
                        onRename()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, null, tint = PitwiseGray400, modifier = Modifier.size(18.dp))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Hapus", color = Color(0xFFCF4444)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFCF4444), modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}

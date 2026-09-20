package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.FlowRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTransactionsSearchSheet(
    flowRecords: List<FlowRecord>,
    isPrivacyMode: Boolean,
    isProUser: Boolean,
    onDismiss: () -> Unit,
    onEditRecord: (FlowRecord) -> Unit,
    onExportCsv: () -> Unit,
    onExportPdfDossier: () -> Unit
) {
    val theme = LocalThemeColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    // Fixes the irritating second pull by skipping partial expansion completely
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val categories = remember(flowRecords) {
        listOf("All") + flowRecords.map { it.category }.distinct().filter { it.isNotBlank() }
    }

    val filteredRecords = remember(flowRecords, searchQuery, selectedCategoryFilter) {
        flowRecords.filter { flow ->
            val matchesQuery = searchQuery.isBlank() ||
                    flow.note.contains(searchQuery, ignoreCase = true) ||
                    flow.category.contains(searchQuery, ignoreCase = true) ||
                    flow.amount.toString().contains(searchQuery)

            val matchesCategory = selectedCategoryFilter == null ||
                    selectedCategoryFilter == "All" ||
                    flow.category.equals(selectedCategoryFilter, ignoreCase = true)

            matchesQuery && matchesCategory
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "All Vault Records (${filteredRecords.size})",
                    color = theme.textBright,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textMuted)
                }
            }

            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by merchant, note, or amount...", color = theme.textMuted, fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = theme.accent) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = theme.surfaceAlt,
                    unfocusedContainerColor = theme.surfaceAlt,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = theme.textBright,
                    unfocusedTextColor = theme.textBright
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Category Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.take(5).forEach { cat ->
                    val isSel = (selectedCategoryFilter == null && cat == "All") || selectedCategoryFilter == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) theme.accent else theme.surfaceAlt)
                            .clickable { selectedCategoryFilter = if (cat == "All") null else cat }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(cat, color = if (isSel) theme.bg else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Record Stream with Tap-To-Edit
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                if (filteredRecords.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No matching records found", color = theme.textMuted, fontSize = 12.5.sp)
                        }
                    }
                } else {
                    items(filteredRecords, key = { it.id }) { flow ->
                        FlowRecordDisplayRow(
                            flow = flow,
                            isPrivacyMode = isPrivacyMode,
                            theme = theme,
                            onClick = { onEditRecord(flow) }
                        )
                        Divider(color = theme.surfaceAlt.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

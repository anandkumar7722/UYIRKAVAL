package com.hacksrm.nirbhay.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hacksrm.nirbhay.data.GuardianData
import com.hacksrm.nirbhay.data.GuardianRepository
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Design Tokens (matching app theme)
// ─────────────────────────────────────────────────────────────────────────────
private val BgDark = Color(0xFF221010)
private val BgCard = Color(0x99331919)
private val BorderDark = Color(0xFF482323)
private val AccentRed = Color(0xFFEC1313)
private val RedAlpha10 = Color(0x1AEC1313)
private val RedAlpha20 = Color(0x33EC1313)
private val TextWhite = Color(0xFFF1F5F9)
private val TextMuted = Color(0xFF94A3B8)
private val TextSubtle = Color(0xFF64748B)
private val TextLabel = Color(0xFFCBD5E1)
private val GreenText = Color(0xFF4ADE80)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageGuardiansScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var guardians by remember { mutableStateOf<List<GuardianData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Edit dialog state
    var editingGuardian by remember { mutableStateOf<GuardianData?>(null) }
    // Add dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    // Delete confirmation
    var deletingGuardian by remember { mutableStateOf<GuardianData?>(null) }

    // Load guardians on first composition
    LaunchedEffect(Unit) {
        isLoading = true
        val (_, list) = GuardianRepository.getGuardians(context)
        guardians = list
        isLoading = false
    }

    fun refreshGuardians() {
        scope.launch {
            isLoading = true
            val (_, list) = GuardianRepository.getGuardians(context)
            guardians = list
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgDark.copy(alpha = 0.95f))
                    .padding(horizontal = 16.dp)
                    .padding(top = 32.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextWhite
                    )
                }
                Text(
                    text = "Manage Guardians",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                // Add button
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Guardian",
                        tint = AccentRed
                    )
                }
            }

            // Status message
            statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = if (msg.startsWith("✅")) GreenText else Color(0xFFEF4444),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ── Content ────────────────────────────────────────────
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentRed)
                }
            } else if (guardians.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = TextSubtle,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No guardians added yet",
                            color = TextMuted,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tap + to add your first trusted contact",
                            color = TextSubtle,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(guardians, key = { it.id }) { guardian ->
                        GuardianCard(
                            guardian = guardian,
                            onEdit = { editingGuardian = guardian },
                            onDelete = { deletingGuardian = guardian }
                        )
                    }
                }
            }
        }

        // ── Add Guardian Dialog ─────────────────────────────────
        if (showAddDialog) {
            GuardianFormDialog(
                title = "Add Guardian",
                initialName = "",
                initialPhone = "",
                initialEmail = "",
                initialRelation = "",
                onDismiss = { showAddDialog = false },
                onSave = { name, phone, email, relation ->
                    scope.launch {
                        val (ok, msg) = GuardianRepository.addGuardian(
                            context = context,
                            contactName = name,
                            contactPhone = phone.takeIf { it.isNotBlank() },
                            contactEmail = email.takeIf { it.isNotBlank() },
                            relation = relation.takeIf { it.isNotBlank() }
                        )
                        statusMessage = if (ok) "✅ Guardian added!" else msg
                        showAddDialog = false
                        refreshGuardians()
                    }
                }
            )
        }

        // ── Edit Guardian Dialog ────────────────────────────────
        editingGuardian?.let { guardian ->
            GuardianFormDialog(
                title = "Edit Guardian",
                initialName = guardian.contact_name,
                initialPhone = guardian.contact_phone ?: "",
                initialEmail = guardian.contact_email ?: "",
                initialRelation = guardian.relation ?: "",
                onDismiss = { editingGuardian = null },
                onSave = { name, phone, email, relation ->
                    scope.launch {
                        val (ok, msg) = GuardianRepository.updateGuardian(
                            context = context,
                            guardianId = guardian.id,
                            contactName = name.takeIf { it.isNotBlank() },
                            contactPhone = phone.takeIf { it.isNotBlank() },
                            contactEmail = email.takeIf { it.isNotBlank() },
                            relation = relation.takeIf { it.isNotBlank() }
                        )
                        statusMessage = if (ok) "✅ Guardian updated!" else msg
                        editingGuardian = null
                        refreshGuardians()
                    }
                }
            )
        }

        // ── Delete Confirmation Dialog ──────────────────────────
        deletingGuardian?.let { guardian ->
            AlertDialog(
                onDismissRequest = { deletingGuardian = null },
                containerColor = Color(0xFF2D1010),
                titleContentColor = TextWhite,
                textContentColor = TextMuted,
                title = { Text("Delete Guardian") },
                text = { Text("Are you sure you want to remove ${guardian.contact_name}?") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            val (ok, msg) = GuardianRepository.deleteGuardian(
                                context = context,
                                guardianId = guardian.id
                            )
                            statusMessage = if (ok) "✅ Guardian deleted!" else msg
                            deletingGuardian = null
                            refreshGuardians()
                        }
                    }) {
                        Text("Delete", color = Color(0xFFEF4444))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingGuardian = null }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Guardian Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GuardianCard(
    guardian: GuardianData,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, RedAlpha20)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Name + relation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = guardian.contact_name,
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!guardian.relation.isNullOrBlank()) {
                        Text(
                            text = guardian.relation,
                            color = AccentRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Phone
            if (!guardian.contact_phone.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                        tint = TextSubtle,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = guardian.contact_phone,
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }

            // Email
            if (!guardian.contact_email.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null,
                        tint = TextSubtle,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = guardian.contact_email,
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Guardian Form Dialog (used for both Add and Edit)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GuardianFormDialog(
    title: String,
    initialName: String,
    initialPhone: String,
    initialEmail: String,
    initialRelation: String,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, email: String, relation: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var email by remember { mutableStateOf(initialEmail) }
    var relation by remember { mutableStateOf(initialRelation) }
    var error by remember { mutableStateOf<String?>(null) }

    val relationOptions = listOf(
        "Mother", "Father", "Sister", "Brother",
        "Spouse", "Partner", "Friend", "Colleague", "Other"
    )
    var showRelationMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2D1010),
        titleContentColor = TextWhite,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Name
                DialogTextField(
                    label = "Name",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Full name"
                )
                // Phone
                DialogTextField(
                    label = "Phone",
                    value = phone,
                    onValueChange = { phone = it },
                    placeholder = "+919876500000",
                    keyboardType = KeyboardType.Phone
                )
                // Email
                DialogTextField(
                    label = "Email",
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "guardian@example.com",
                    keyboardType = KeyboardType.Email
                )
                // Relation dropdown
                Column {
                    Text("Relation", color = TextLabel, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { showRelationMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextWhite
                            ),
                            border = BorderStroke(1.dp, RedAlpha20),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = relation.ifBlank { "Select relation" },
                                color = if (relation.isBlank()) TextSubtle else TextWhite,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        DropdownMenu(
                            expanded = showRelationMenu,
                            onDismissRequest = { showRelationMenu = false },
                            containerColor = Color(0xFF3D1515)
                        ) {
                            relationOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            opt,
                                            color = if (opt == relation) AccentRed else TextWhite
                                        )
                                    },
                                    onClick = {
                                        relation = opt
                                        showRelationMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                // Error
                error?.let {
                    Text(it, color = Color(0xFFEF4444), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "Name is required"
                    return@TextButton
                }
                if (phone.isBlank() && email.isBlank()) {
                    error = "Phone or email is required"
                    return@TextButton
                }
                onSave(name, phone, email, relation)
            }) {
                Text("Save", color = AccentRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun DialogTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column {
        Text(label, color = TextLabel, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextSubtle) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = AccentRed.copy(alpha = 0.6f),
                unfocusedBorderColor = RedAlpha20,
                cursorColor = AccentRed,
                focusedContainerColor = Color(0xFF2D1010),
                unfocusedContainerColor = Color(0xFF2D1010)
            )
        )
    }
}


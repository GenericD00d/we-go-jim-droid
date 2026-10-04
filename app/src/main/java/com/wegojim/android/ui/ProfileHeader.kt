package com.wegojim.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wegojim.android.app
import com.wegojim.android.data.AthleteType
import com.wegojim.android.data.Birthday

/** Avatar, name, athlete type badge and (on the day) a birthday banner. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProfileHeader() {
    val context = LocalContext.current
    val store = context.app.settings
    val settings by store.state.collectAsStateWithLifecycle()
    val avatar = remember(settings.avatarVersion) { Avatar.load(context) }
    var pickingType by remember { mutableStateOf(false) }
    var birthdayDismissed by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && Avatar.save(context, uri)) store.update { it.copy(avatarVersion = it.avatarVersion + 1) }
    }
    val type = AthleteType.of(settings.athleteType)

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                    .combinedClickable(
                        onClickLabel = "Change photo",
                        onLongClickLabel = "Remove photo",
                        onLongClick = {
                            if (avatar != null && Avatar.clear(context)) store.update { it.copy(avatarVersion = it.avatarVersion + 1) }
                        },
                    ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    Image(avatar.asImageBitmap(), "Profile photo", Modifier.size(64.dp), contentScale = ContentScale.Crop)
                } else {
                    Text(
                        settings.displayName.trim().firstOrNull()?.uppercase() ?: "W",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(settings.displayName.ifBlank { "Athlete" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AssistChip(onClick = { pickingType = true }, label = { Text(type?.title ?: "Pick your athlete type") })
            }
        }
        type?.let { Text(it.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

    if (Birthday.isBirthday(settings.birthDate) && !birthdayDismissed) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cake, null, tint = Wgj.colors.gold)
                Text("  ${Birthday.greeting(settings.displayName)}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { birthdayDismissed = true }) { Icon(Icons.Default.Close, "Dismiss") }
            }
            Text("Birthday gains count double. (They don't, but go lift anyway.)", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (pickingType) {
        AlertDialog(
            onDismissRequest = { pickingType = false },
            title = { Text("Athlete type") },
            text = {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(AthleteType.entries) { t ->
                        ListItem(
                            headlineContent = { Text(t.title, fontWeight = if (t == type) FontWeight.Bold else FontWeight.Normal) },
                            supportingContent = { Text(t.subtitle) },
                            modifier = Modifier.fillMaxWidth().clickable {
                                store.update { it.copy(athleteType = t.name) }
                                pickingType = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickingType = false }) { Text("Close") } },
        )
    }
}

package com.example.edmotor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Category

@Composable
fun CategoryBadge(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (isSelected) colors.primaryContainer else colors.surface,
        contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
        border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outline)
    ) {
        Row(
            modifier = Modifier
                .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            } else if (category.emoji.isNotEmpty()) {
                Text(
                    text = category.emoji,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
            Text(
                text = category.nombre,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

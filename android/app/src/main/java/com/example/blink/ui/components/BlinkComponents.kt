package com.example.blink.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BlinkCard(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        backgroundColor: Color = MaterialTheme.colorScheme.surface,
        content: @Composable ColumnScope.() -> Unit
) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")

        Card(
                modifier =
                        modifier.scale(scale)
                                .clip(
                                        RoundedCornerShape(12.dp)
                                ) // Reduced corner radius for cleaner look
                                .then(
                                        if (onClick != null) {
                                                Modifier.clickable(
                                                        interactionSource = interactionSource,
                                                        indication = null,
                                                        onClick = onClick
                                                )
                                        } else Modifier
                                ),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat design
        ) { Column(modifier = Modifier.padding(16.dp), content = content) }
}

@Composable
fun BlinkButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        icon: ImageVector? = null,
        enabled: Boolean = true,
        containerColor: Color = MaterialTheme.colorScheme.primary,
        contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
        val haptic = LocalHapticFeedback.current
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "scale")

        Button(
                onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                },
                modifier = modifier.height(56.dp).scale(scale),
                enabled = enabled,
                shape = RoundedCornerShape(8.dp), // Less rounded, more square
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = containerColor,
                                contentColor = contentColor,
                                disabledContainerColor = containerColor.copy(alpha = 0.5f),
                                disabledContentColor = contentColor.copy(alpha = 0.5f)
                        ),
                contentPadding = PaddingValues(horizontal = 24.dp),
                interactionSource = interactionSource
        ) {
                if (icon != null) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                        text = text,
                        style =
                                MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                )
                )
        }
}

@Composable
fun BlinkNavBar(
        items: List<Triple<String, String, ImageVector>>,
        currentRoute: String?,
        onNavigate: (String) -> Unit,
        modifier: Modifier = Modifier
) {
        Surface(
                modifier = modifier.fillMaxWidth(),
                color = Color.Black, // Pure black background
                tonalElevation = 0.dp
        ) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                        .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        items.forEach { (route, title, icon) ->
                                val isSelected = currentRoute == route
                                val haptic = LocalHapticFeedback.current

                                val contentColor by
                                        animateColorAsState(
                                                if (isSelected) Color.White else Color.Gray,
                                                label = "contentColor"
                                        )

                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier =
                                                Modifier.clickable(
                                                        interactionSource =
                                                                remember {
                                                                        MutableInteractionSource()
                                                                },
                                                        indication = null
                                                ) {
                                                        haptic.performHapticFeedback(
                                                                HapticFeedbackType.TextHandleMove
                                                        )
                                                        onNavigate(route)
                                                }
                                ) {
                                        Icon(
                                                imageVector = icon,
                                                contentDescription = title,
                                                tint = contentColor,
                                                modifier = Modifier.size(28.dp)
                                        )
                                        if (isSelected) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(
                                                        modifier =
                                                                Modifier.size(4.dp)
                                                                        .clip(CircleShape)
                                                                        .background(Color.White)
                                                )
                                        }
                                }
                        }
                }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkTextField(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        modifier: Modifier = Modifier,
        singleLine: Boolean = true
) {
        TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                        Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                        )
                },
                modifier = modifier.fillMaxWidth(),
                colors =
                        TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1E1E1E), // Dark gray
                                unfocusedContainerColor = Color(0xFF1E1E1E),
                                disabledContainerColor = Color(0xFF1E1E1E),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                        ),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = singleLine,
                shape = RoundedCornerShape(8.dp)
        )
}

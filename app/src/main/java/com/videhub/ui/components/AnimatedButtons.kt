package com.videhub.ui.components
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedSubscribeButton(
    isSubscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.inverseSurface,
        label = "containerColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.inverseOnSurface,
        label = "contentColor"
    )
    Box(
        contentAlignment = Alignment.Center, 
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            contentColor = contentColor,
            modifier = Modifier.height(36.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isSubscribed) "Subscribed" else "Subscribe",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

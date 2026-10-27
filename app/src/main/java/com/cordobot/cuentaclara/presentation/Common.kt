package com.cordobot.cuentaclara.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

val Positive = Color(0xFF34D399)
val Negative = Color(0xFFF87171)

private val colors = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF032018),
    secondary = Color(0xFF60A5FA),
    background = Color(0xFF0A1210),
    onBackground = Color.White,
    surface = Color(0xFF13201C),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C2D28),
    onSurfaceVariant = Color(0xFF9FB8B0),
    surfaceContainerHigh = Color(0xFF1A2925)
)

@Composable
fun CuentaClaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 30.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        ),
        content = content
    )
}

private val pesos = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"))

/** "$ 45.000" or "$ 12.345,50" when there are cents. */
fun formatMoney(cents: Long): String = pesos.apply {
    val decimals = if (cents % 100 == 0L) 0 else 2
    minimumFractionDigits = decimals
    maximumFractionDigits = decimals
}.format(cents / 100.0)

@Composable
fun OfflineBanner(offline: Boolean) {
    AnimatedVisibility(visible = offline, enter = expandVertically(), exit = shrinkVertically()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF7C2D12))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Sin conexión · los cambios se guardan y se sincronizan al volver",
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

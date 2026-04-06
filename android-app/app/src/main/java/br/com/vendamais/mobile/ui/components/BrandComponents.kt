package br.com.vendamais.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.ui.theme.BrandDarkGreen
import br.com.vendamais.mobile.ui.theme.BrandGreen
import br.com.vendamais.mobile.ui.theme.BrandLime
import br.com.vendamais.mobile.ui.theme.White

@Composable
fun VendaBrandIcon(
    modifier: Modifier = Modifier,
    showPlusBubble: Boolean = true,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(BrandGreen, BrandLime),
                    ),
                )
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ShowChart,
                contentDescription = null,
                tint = White,
                modifier = Modifier.size(30.dp),
            )
        }

        if (showPlusBubble) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(BrandLime),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
fun VendaBrandWordmark(
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VendaBrandIcon(modifier = Modifier.size(96.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Venda",
                color = White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(BrandLime),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun OdontoartBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(BrandLime, BrandGreen),
                ),
            )
            .defaultMinSize(minWidth = 190.dp)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = White, fontWeight = FontWeight.ExtraBold)) {
                        append("odonto")
                    }
                    withStyle(SpanStyle(color = BrandDarkGreen, fontWeight = FontWeight.ExtraBold)) {
                        append("art")
                    }
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Planos Odontologicos",
                color = White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

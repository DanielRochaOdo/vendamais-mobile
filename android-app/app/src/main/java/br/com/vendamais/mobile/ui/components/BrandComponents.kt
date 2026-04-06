package br.com.vendamais.mobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.R
import br.com.vendamais.mobile.ui.theme.White

@Composable
fun VendaBrandIcon(
    modifier: Modifier = Modifier,
    showPlusBubble: Boolean = true,
) {
    val logoModifier = if (modifier == Modifier) Modifier.size(72.dp) else modifier
    Surface(
        modifier = logoModifier,
        shape = CircleShape,
        color = White.copy(alpha = 0.12f),
    ) {
        Image(
            painter = painterResource(id = R.drawable.vendamais_logo_odontoart),
            contentDescription = "Logo Venda+ Odontoart",
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Fit,
        )
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
        VendaBrandIcon(
            modifier = Modifier.size(180.dp),
            showPlusBubble = false,
        )

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
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = White.copy(alpha = 0.55f),
                spotColor = White.copy(alpha = 0.45f),
            ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(4.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color.Transparent,
        ) {
            Image(
                painter = painterResource(id = R.drawable.odontoart_badge),
                contentDescription = "Logo Odontoart Planos Odontologicos",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

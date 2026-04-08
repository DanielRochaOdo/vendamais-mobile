package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.data.models.VendedorStats
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.DashboardMetricType
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.theme.Amber100
import br.com.vendamais.mobile.ui.theme.Amber500
import br.com.vendamais.mobile.ui.theme.Blue100
import br.com.vendamais.mobile.ui.theme.Blue500
import br.com.vendamais.mobile.ui.theme.Emerald
import br.com.vendamais.mobile.ui.theme.EmeraldSoft

@Composable
fun DashboardScreen(
    state: AppUiState,
    onOpenDrilldown: (String, DashboardMetricType) -> Unit,
    onCloseDrilldown: () -> Unit,
) {
    val profile = state.profile ?: return
    val canViewSystemOverview = profile.role in setOf("ADMINISTRADOR", "ADMIN", "GERENTE", "GESTOR")
    val canOpenDrilldown = profile.role in setOf("ADMINISTRADOR", "ADMIN", "GERENTE", "GESTOR", "SUPERVISOR")

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            ScreenHeading(
                title = "Dashboard",
                subtitle = "Bem-vindo ao VENDA+",
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Estati­sticas - Mes Atual",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                MetricSection(
                    title = "Cadastro",
                    total = state.cadastroStats.cadastro_total,
                    pendentes = state.cadastroStats.cadastro_incompletos,
                    enviados = state.cadastroStats.cadastro_enviados,
                    totalDetail = "${state.cadastroStats.cadastro_cadastros} cadastros + ${state.cadastroStats.cadastro_dependentes} dependentes",
                    pendentesDetail = "${state.cadastroStats.cadastro_incompletos_cadastros} cadastros + ${state.cadastroStats.cadastro_incompletos_dependentes} dependentes",
                    enviadosDetail = "${state.cadastroStats.cadastro_enviados_cadastros} cadastros + ${state.cadastroStats.cadastro_enviados_dependentes} dependentes",
                    clickable = canOpenDrilldown,
                    onMetricClick = { metricType -> onOpenDrilldown("cadastro", metricType) },
                )
                MetricSection(
                    title = "Inclusao de Dependente",
                    total = state.cadastroStats.inclusao_total,
                    pendentes = state.cadastroStats.inclusao_incompletos,
                    enviados = state.cadastroStats.inclusao_enviados,
                    totalDetail = "${state.cadastroStats.inclusao_cadastros} cadastros + ${state.cadastroStats.inclusao_dependentes} dependentes",
                    pendentesDetail = "${state.cadastroStats.inclusao_incompletos_cadastros} cadastros + ${state.cadastroStats.inclusao_incompletos_dependentes} dependentes",
                    enviadosDetail = "${state.cadastroStats.inclusao_enviados_cadastros} cadastros + ${state.cadastroStats.inclusao_enviados_dependentes} dependentes",
                    clickable = canOpenDrilldown,
                    onMetricClick = { metricType -> onOpenDrilldown("inclusao_dependente", metricType) },
                )
            }
        }

        if (state.dashboardDrilldownLoading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            Text(
                text = "Visao Geral",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            WebCard {
                OverviewLine(
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = Emerald,
                        )
                    },
                    title = "Seu Perfil",
                    value = profile.role,
                    detail = roleDescription(profile.role),
                    background = EmeraldSoft,
                )
            }
        }

        state.team?.let { team ->
            item {
                WebCard {
                    OverviewLine(
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = Blue500,
                            )
                        },
                        title = "Sua Equipe",
                        value = team.name,
                        detail = if (team.isActive) "Equipe ativa" else "Equipe inativa",
                        background = Blue100,
                    )
                }
            }
        }

        if (canViewSystemOverview) {
            item {
                WebCard(title = "Estati­sticas do Sistema") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SystemRow("Total de Usuarios", state.systemOverview.totalUsers.toString())
                        SystemRow("Usuarios Ativos", state.systemOverview.activeUsers.toString())
                        SystemRow("Total de Equipes", state.systemOverview.totalTeams.toString())
                    }
                }
            }
        }
    }

    state.dashboardDrilldown?.let { drilldown ->
        StatsByVendedorDialog(
            title = drilldown.title,
            metricType = drilldown.metricType,
            stats = drilldown.items,
            onDismiss = onCloseDrilldown,
        )
    }
}

@Composable
private fun MetricSection(
    title: String,
    total: Int,
    pendentes: Int,
    enviados: Int,
    totalDetail: String,
    pendentesDetail: String,
    enviadosDetail: String,
    clickable: Boolean,
    onMetricClick: (DashboardMetricType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        MetricCard(
            label = "Total",
            value = total.toString(),
            detail = totalDetail,
            clickable = clickable,
            background = Blue100,
            textColor = Blue500,
            icon = Icons.Rounded.Description,
            onClick = { onMetricClick(DashboardMetricType.TOTAL) },
        )
        MetricCard(
            label = "Pendentes",
            value = pendentes.toString(),
            detail = pendentesDetail,
            clickable = clickable,
            background = Amber100,
            textColor = Amber500,
            icon = Icons.Rounded.HourglassEmpty,
            onClick = { onMetricClick(DashboardMetricType.PENDENTES) },
        )
        MetricCard(
            label = "Cadastrados",
            value = enviados.toString(),
            detail = enviadosDetail,
            clickable = clickable,
            background = EmeraldSoft,
            textColor = Emerald,
            icon = Icons.Rounded.CheckCircle,
            onClick = { onMetricClick(DashboardMetricType.CADASTRADOS) },
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    detail: String,
    clickable: Boolean,
    background: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val readableOnCard = if (background.luminance() > 0.6f) Color(0xFF172235) else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(textColor)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                color = readableOnCard,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
            )
        }
    }
}

@Composable
private fun OverviewLine(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    detail: String,
    background: androidx.compose.ui.graphics.Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(background)
                .padding(12.dp),
        ) { icon() }
        Column {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SystemRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Assessment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatsByVendedorDialog(
    title: String,
    metricType: DashboardMetricType,
    stats: List<VendedorStats>,
    onDismiss: () -> Unit,
) {
    val sortedStats = stats.sortedByDescending {
        when (metricType) {
            DashboardMetricType.TOTAL -> it.total
            DashboardMetricType.PENDENTES -> it.incompletos
            DashboardMetricType.CADASTRADOS -> it.enviados
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sortedStats.isEmpty()) {
                    Text("Nenhum dado disponÃ­vel.")
                } else {
                    sortedStats.forEach { stat ->
                        WebCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(stat.vendedorNome, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Total ${stat.total} â€¢ Pendentes ${stat.incompletos} â€¢ Enviados ${stat.enviados}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text(
                                    text = when (metricType) {
                                        DashboardMetricType.TOTAL -> stat.total.toString()
                                        DashboardMetricType.PENDENTES -> stat.incompletos.toString()
                                        DashboardMetricType.CADASTRADOS -> stat.enviados.toString()
                                    },
                                    color = Emerald,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

private fun roleDescription(role: String): String {
    return when (role) {
        "ADMINISTRADOR", "ADMIN" -> "Acesso total ao sistema"
        "GERENTE", "GESTOR" -> "Gerenciamento de equipes e usuarios"
        "SUPERVISOR" -> "Supervisao de equipe"
        "VENDEDOR" -> "Execucaoo de vendas"
        "ADESIONISTA" -> "Processos de adesao"
        else -> ""
    }
}

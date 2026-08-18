package br.com.vendamais.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import br.com.vendamais.mobile.data.models.CadastroLinkItem
import br.com.vendamais.mobile.data.models.EmpresaResumo
import br.com.vendamais.mobile.data.models.EmpresaSearchType
import br.com.vendamais.mobile.ui.LinkWorkspaceState
import br.com.vendamais.mobile.ui.components.VendaButton
import br.com.vendamais.mobile.ui.components.VendaButtonStyle
import br.com.vendamais.mobile.ui.components.VendaEmptyState
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.components.bringIntoViewOnFocus
import br.com.vendamais.mobile.ui.theme.Emerald
import br.com.vendamais.mobile.ui.theme.EmeraldDark
import br.com.vendamais.mobile.ui.theme.EmeraldSoft
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun CadastroLinksCard(
    workspace: LinkWorkspaceState,
    onSearchTypeChange: (EmpresaSearchType) -> Unit,
    onSearchValueChange: (String) -> Unit,
    onSearchEmpresa: () -> Unit,
    onSelectEmpresa: (EmpresaResumo) -> Unit,
    onClearEmpresa: () -> Unit,
    onGenerateLink: () -> Unit,
    onRegenerateLink: (String) -> Unit,
    onDeleteLink: (String) -> Unit,
) {
    val context = LocalContext.current
    var qrDialogLink by remember { mutableStateOf<CadastroLinkItem?>(null) }
    val expandedLinks = remember { mutableStateMapOf<String, Boolean>() }

    WebCard {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Links de adesao",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Selecione uma empresa para gerar um link publico e acompanhe os links ativos.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = if (workspace.selectedEmpresa == null) "1. Localize a empresa" else "Empresa selecionada",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (workspace.selectedEmpresa == null) {
                LinkSearchTypeRow(
                    selected = workspace.empresaSearchType,
                    onSelected = onSearchTypeChange,
                )

                OutlinedTextField(
                    value = workspace.empresaSearchValue,
                    onValueChange = onSearchValueChange,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                    label = {
                        Text(
                            when (workspace.empresaSearchType) {
                                EmpresaSearchType.CODIGO -> "Codigo da empresa"
                                EmpresaSearchType.CNPJ -> "CNPJ"
                                EmpresaSearchType.NOME -> "Nome da empresa"
                            },
                        )
                    },
                    placeholder = {
                        Text(
                            when (workspace.empresaSearchType) {
                                EmpresaSearchType.CODIGO -> "Digite o codigo"
                                EmpresaSearchType.CNPJ -> "00.000.000/0000-00"
                                EmpresaSearchType.NOME -> "Digite o nome da empresa"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (workspace.empresaSearchType) {
                            EmpresaSearchType.CODIGO -> KeyboardType.Number
                            EmpresaSearchType.CNPJ -> KeyboardType.Number
                            EmpresaSearchType.NOME -> KeyboardType.Text
                        },
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearchEmpresa() },
                        onDone = { onSearchEmpresa() },
                    ),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )

                VendaButton(
                    label = "Buscar empresa",
                    onClick = onSearchEmpresa,
                    enabled = workspace.empresaSearchValue.isNotBlank(),
                    loading = workspace.operationLoading,
                    leadingIcon = Icons.Rounded.Search,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (workspace.empresaSearchResults.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Resultados",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        workspace.empresaSearchResults.forEach { empresa ->
                            LinkEmpresaResultCard(
                                empresa = empresa,
                                onSelect = { onSelectEmpresa(empresa) },
                            )
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = EmeraldSoft.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, Emerald.copy(alpha = 0.20f)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = workspace.selectedEmpresa.nomeFantasia.ifBlank {
                                    workspace.selectedEmpresa.razaoSocial.ifBlank { "Empresa sem nome" }
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (workspace.selectedEmpresa.razaoSocial.isNotBlank()) {
                                Text(
                                    text = workspace.selectedEmpresa.razaoSocial,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                text = "Codigo ${workspace.selectedEmpresa.id} · ${workspace.selectedEmpresa.cnpj}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        VendaButton(
                            label = "Gerar link publico",
                            onClick = onGenerateLink,
                            loading = workspace.operationLoading,
                            leadingIcon = Icons.Rounded.Share,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        VendaButton(
                            label = "Alterar empresa",
                            onClick = onClearEmpresa,
                            leadingIcon = Icons.Rounded.Edit,
                            style = VendaButtonStyle.SECONDARY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Links ativos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = workspace.links.size.toString(),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (workspace.links.isEmpty()) {
                VendaEmptyState(
                    title = "Nenhum link criado",
                    message = "Selecione uma empresa e gere um link publico de adesao.",
                )
            } else {
                workspace.links.forEach { link ->
                    LinkListItem(
                        context = context,
                        link = link,
                        loading = workspace.operationLoading,
                        expanded = expandedLinks[link.id] == true,
                        onToggleExpanded = {
                            expandedLinks[link.id] = !(expandedLinks[link.id] ?: false)
                        },
                        onShowQrCode = { qrDialogLink = link },
                        onRegenerate = { onRegenerateLink(link.id) },
                        onDelete = { onDeleteLink(link.id) },
                    )
                }
            }
        }
    }

    qrDialogLink?.let { link ->
        LinkQrCodeDialog(
            context = context,
            link = link,
            onDismiss = { qrDialogLink = null },
        )
    }
}

@Composable
private fun LinkSearchTypeRow(
    selected: EmpresaSearchType,
    onSelected: (EmpresaSearchType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinkSearchTypePill(
            modifier = Modifier.weight(1f),
            label = "Codigo",
            selected = selected == EmpresaSearchType.CODIGO,
            onClick = { onSelected(EmpresaSearchType.CODIGO) },
        )
        LinkSearchTypePill(
            modifier = Modifier.weight(1f),
            label = "CNPJ",
            selected = selected == EmpresaSearchType.CNPJ,
            onClick = { onSelected(EmpresaSearchType.CNPJ) },
        )
        LinkSearchTypePill(
            modifier = Modifier.weight(1f),
            label = "Nome",
            selected = selected == EmpresaSearchType.NOME,
            onClick = { onSelected(EmpresaSearchType.NOME) },
        )
    }
}

@Composable
private fun LinkSearchTypePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (selected) EmeraldSoft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) Emerald.copy(alpha = 0.28f) else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            color = if (selected) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun LinkEmpresaResultCard(
    empresa: EmpresaResumo,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = empresa.nomeFantasia.ifBlank { empresa.razaoSocial.ifBlank { "Empresa sem nome" } },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (empresa.razaoSocial.isNotBlank()) {
                Text(
                    text = empresa.razaoSocial,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "Codigo ${empresa.id} · ${empresa.cnpj}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkListItem(
    context: Context,
    link: CadastroLinkItem,
    loading: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onShowQrCode: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
) {
    val linkUrl = link.linkUrl.orEmpty().trim()
    Surface(
        onClick = onToggleExpanded,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${link.empresaCodigo} · ${link.empresaNome}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${link.clickCount ?: 0} cliques · criado ${formatDateTime(link.createdAt)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = EmeraldSoft,
                ) {
                    Text(
                        text = "Ativo",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = EmeraldDark,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Recolher link" else "Expandir link",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                Text(
                    text = "Vendedor: ${link.vendedorNome ?: "-"} · Codigo ${link.vendedorCodigo ?: "-"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = linkUrl.ifBlank { "Link indisponivel" },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinkActionChip(
                        icon = Icons.Rounded.ContentCopy,
                        label = "Copiar",
                        enabled = linkUrl.isNotBlank(),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("cadastro-link", linkUrl))
                            Toast.makeText(context, "Link copiado.", Toast.LENGTH_SHORT).show()
                        },
                    )
                    LinkActionChip(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        label = "Abrir",
                        enabled = linkUrl.isNotBlank(),
                        onClick = {
                            if (linkUrl.isNotBlank()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl)))
                            }
                        },
                    )
                    LinkActionChip(
                        icon = Icons.Rounded.QrCode,
                        label = "QR Code",
                        enabled = linkUrl.isNotBlank(),
                        onClick = onShowQrCode,
                    )
                    LinkActionChip(
                        icon = Icons.Rounded.Refresh,
                        label = "Regerar",
                        enabled = !loading,
                        onClick = onRegenerate,
                    )
                    LinkActionChip(
                        icon = Icons.Rounded.Delete,
                        label = "Excluir",
                        enabled = !loading,
                        danger = true,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkActionChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = if (danger) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Text(label, color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LinkQrCodeDialog(
    context: Context,
    link: CadastroLinkItem,
    onDismiss: () -> Unit,
) {
    val linkUrl = link.linkUrl.orEmpty().trim()
    val qrBitmap = remember(linkUrl) {
        if (linkUrl.isBlank()) null else runCatching { generateQrCodeBitmap(linkUrl, 1024) }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QR Code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = link.empresaNome ?: "Link sem empresa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code do link",
                        modifier = Modifier.size(240.dp).padding(2.dp),
                    )
                } else {
                    Text(
                        text = "Nao foi possivel gerar o QR Code para este link.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = linkUrl.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        val bitmap = qrBitmap ?: run {
                            Toast.makeText(context, "QR Code indisponivel para compartilhar.", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        val uri = saveQrToCacheForShare(context, bitmap, "qrcode_${link.id}.png")
                        if (uri == null) {
                            Toast.makeText(context, "Falha ao preparar compartilhamento.", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, linkUrl)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar QR Code"))
                    },
                    enabled = qrBitmap != null && linkUrl.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Compartilhar QR Code",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = {
                        val bitmap = qrBitmap ?: run {
                            Toast.makeText(context, "QR Code indisponivel para download.", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        val uri = saveQrToDownloads(context, bitmap, "qrcode_${link.id}.png")
                        if (uri == null) {
                            Toast.makeText(context, "Falha ao salvar QR Code.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "QR Code salvo com sucesso.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = qrBitmap != null,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = "Baixar QR Code",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        dismissButton = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Fechar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

private fun generateQrCodeBitmap(content: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bitmap
}

private fun saveQrToCacheForShare(context: Context, bitmap: Bitmap, fileName: String): Uri? {
    return runCatching {
        val dir = File(context.cacheDir, "shared_qrcodes").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}

private fun saveQrToDownloads(context: Context, bitmap: Bitmap, fileName: String): Uri? {
    return runCatching {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VendaMais")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
            } ?: return@runCatching null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val file = File(dir, fileName)
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
            }
            Uri.fromFile(file)
        }
    }.getOrNull()
}

private fun formatDateTime(value: String): String {
    return runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrDefault(value)
}

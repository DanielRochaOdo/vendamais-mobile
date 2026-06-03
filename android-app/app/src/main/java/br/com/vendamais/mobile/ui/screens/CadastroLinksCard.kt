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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.CircularProgressIndicator
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
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.theme.Emerald
import br.com.vendamais.mobile.ui.theme.EmeraldSoft
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import br.com.vendamais.mobile.ui.components.bringIntoViewOnFocus

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
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Link",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Gere e gerencie links publicos usando a mesma tabela cadastro_links da versao web.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
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
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = onSearchEmpresa,
                        enabled = !workspace.operationLoading && workspace.empresaSearchValue.isNotBlank(),
                    ) {
                        if (workspace.operationLoading) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Buscar empresa",
                            )
                        }
                    }
                }

                workspace.empresaSearchResults.forEach { empresa ->
                    LinkEmpresaResultCard(
                        empresa = empresa,
                        onSelect = { onSelectEmpresa(empresa) },
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = workspace.selectedEmpresa.nomeFantasia,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = workspace.selectedEmpresa.razaoSocial,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Codigo ${workspace.selectedEmpresa.id} • ${workspace.selectedEmpresa.cnpj}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconButton(
                                onClick = onGenerateLink,
                                enabled = !workspace.operationLoading,
                            ) {
                                if (workspace.operationLoading) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = "Gerar link",
                                    )
                                }
                            }
                            IconButton(onClick = onClearEmpresa) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Alterar empresa",
                                )
                            }
                        }
                    }
                }
            }

            if (workspace.links.isEmpty()) {
                Text(
                    text = "Nenhum link ativo encontrado.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
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
            label = "Codigo",
            selected = selected == EmpresaSearchType.CODIGO,
            onClick = { onSelected(EmpresaSearchType.CODIGO) },
        )
        LinkSearchTypePill(
            label = "CNPJ",
            selected = selected == EmpresaSearchType.CNPJ,
            onClick = { onSelected(EmpresaSearchType.CNPJ) },
        )
        LinkSearchTypePill(
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
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) EmeraldSoft else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (selected) Emerald else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = empresa.nomeFantasia,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = empresa.razaoSocial,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Codigo ${empresa.id} • ${empresa.cnpj}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${link.empresaCodigo} - ${link.empresaNome}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Recolher link" else "Expandir link",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                Text(
                    text = "Vendedor: ${link.vendedorNome ?: "-"} • Codigo ${link.vendedorCodigo ?: "-"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = linkUrl.ifBlank { "-" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Criado em ${formatDateTime(link.createdAt)} • Cliques ${link.clickCount ?: 0}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    LinkActionIconButton(
                        icon = Icons.Rounded.ContentCopy,
                        contentDescription = "Copiar link",
                        enabled = linkUrl.isNotBlank(),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("cadastro-link", linkUrl))
                        },
                    )
                    LinkActionIconButton(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Abrir link",
                        enabled = linkUrl.isNotBlank(),
                        onClick = {
                            if (linkUrl.isNotBlank()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl)))
                            }
                        },
                    )
                    LinkActionIconButton(
                        icon = Icons.Rounded.QrCode,
                        contentDescription = "Abrir QRCode",
                        enabled = linkUrl.isNotBlank(),
                        onClick = onShowQrCode,
                    )
                    LinkActionIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Regerar link",
                        enabled = !loading,
                        onClick = onRegenerate,
                    )
                    LinkActionIconButton(
                        icon = Icons.Rounded.Delete,
                        contentDescription = "Excluir link",
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
private fun LinkActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
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
        title = { Text("QRCode") },
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
                        contentDescription = "QRCode do link",
                        modifier = Modifier
                            .size(240.dp)
                            .padding(2.dp),
                    )
                } else {
                    Text(
                        text = "Nao foi possivel gerar o QRCode para este link.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                            Toast.makeText(context, "QRCode indisponivel para compartilhar.", Toast.LENGTH_SHORT).show()
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
                        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar QRCode"))
                    },
                    enabled = qrBitmap != null && linkUrl.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Compartilhar QRCode",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = {
                        val bitmap = qrBitmap ?: run {
                            Toast.makeText(context, "QRCode indisponivel para download.", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        val uri = saveQrToDownloads(context, bitmap, "qrcode_${link.id}.png")
                        if (uri == null) {
                            Toast.makeText(context, "Falha ao salvar QRCode.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "QRCode salvo com sucesso.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = qrBitmap != null,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = "Baixar QRCode",
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
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
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

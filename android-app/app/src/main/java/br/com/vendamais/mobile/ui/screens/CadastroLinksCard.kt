package br.com.vendamais.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import br.com.vendamais.mobile.data.models.CadastroLinkItem
import br.com.vendamais.mobile.data.models.EmpresaResumo
import br.com.vendamais.mobile.data.models.EmpresaSearchType
import br.com.vendamais.mobile.ui.LinkWorkspaceState
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.theme.Emerald
import br.com.vendamais.mobile.ui.theme.EmeraldSoft
import br.com.vendamais.mobile.ui.theme.Slate100
import br.com.vendamais.mobile.ui.theme.Slate500
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

    WebCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Link",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Gere e gerencie links públicos usando a mesma tabela cadastro_links da versão web.",
                color = Slate500,
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
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            when (workspace.empresaSearchType) {
                                EmpresaSearchType.CODIGO -> "Código da empresa"
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
                    singleLine = true,
                )

                Button(
                    onClick = onSearchEmpresa,
                    enabled = !workspace.operationLoading && workspace.empresaSearchValue.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (workspace.operationLoading) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Text("Buscar empresa")
                    }
                }

                workspace.empresaSearchResults.forEach { empresa ->
                    LinkEmpresaResultCard(
                        empresa = empresa,
                        onSelect = { onSelectEmpresa(empresa) },
                    )
                }
            } else {
                Surface(shape = RoundedCornerShape(16.dp), color = Slate100) {
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
                            color = Slate500,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Código ${workspace.selectedEmpresa.id} • ${workspace.selectedEmpresa.cnpj}",
                            color = Slate500,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onGenerateLink,
                                enabled = !workspace.operationLoading,
                            ) {
                                if (workspace.operationLoading) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                } else {
                                    Text("Gerar link")
                                }
                            }
                            TextButton(onClick = onClearEmpresa) {
                                Text("Alterar empresa")
                            }
                        }
                    }
                }
            }

            if (workspace.links.isEmpty()) {
                Text(
                    text = "Nenhum link ativo encontrado.",
                    color = Slate500,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                workspace.links.forEach { link ->
                    LinkListItem(
                        context = context,
                        link = link,
                        loading = workspace.operationLoading,
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
            label = "Código",
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
        color = if (selected) EmeraldSoft else Slate100,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (selected) Emerald else Slate500,
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
        color = Slate100,
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
                color = Slate500,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Código ${empresa.id} • ${empresa.cnpj}",
                color = Slate500,
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
    onShowQrCode: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = Slate100) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${link.empresaCodigo} - ${link.empresaNome}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Vendedor: ${link.vendedorNome ?: "-"} • Código ${link.vendedorCodigo ?: "-"}",
                color = Slate500,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = link.linkUrl ?: "-",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Criado em ${formatDateTime(link.createdAt)} • Cliques ${link.clickCount ?: 0}",
                color = Slate500,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("cadastro-link", link.linkUrl.orEmpty()))
                    },
                ) {
                    Text("Copiar")
                }
                TextButton(
                    onClick = {
                        link.linkUrl?.let {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                        }
                    },
                ) {
                    Text("Abrir")
                }
                TextButton(
                    onClick = onShowQrCode,
                    enabled = !link.linkUrl.isNullOrBlank(),
                ) {
                    Text("QRCode")
                }
                TextButton(onClick = onRegenerate, enabled = !loading) {
                    Text("Regerar")
                }
                TextButton(onClick = onDelete, enabled = !loading) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            }
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
        title = { Text("QRCode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = link.empresaNome ?: "Link sem empresa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
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
                Text(
                    text = linkUrl.ifBlank { "-" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val bitmap = qrBitmap ?: run {
                            Toast.makeText(context, "QRCode indisponivel para compartilhar.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val uri = saveQrToCacheForShare(context, bitmap, "qrcode_${link.id}.png")
                        if (uri == null) {
                            Toast.makeText(context, "Falha ao preparar compartilhamento.", Toast.LENGTH_SHORT).show()
                            return@TextButton
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
                    Text("Compartilhar")
                }
                TextButton(
                    onClick = {
                        val bitmap = qrBitmap ?: run {
                            Toast.makeText(context, "QRCode indisponivel para download.", Toast.LENGTH_SHORT).show()
                            return@TextButton
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
                    Text("Download")
                }
                TextButton(onClick = onDismiss) {
                    Text("Fechar")
                }
            }
        },
    )
}

private fun generateQrCodeBitmap(content: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
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

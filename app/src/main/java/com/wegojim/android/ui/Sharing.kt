package com.wegojim.android.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.wegojim.android.app
import com.wegojim.android.data.TemplateTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Template" }

private fun shareFile(context: Context, file: File, mime: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, title))
}

private fun sharedDir(context: Context) = File(context.cacheDir, "shared").apply { mkdirs() }

/** Shares text as a file named "<name>.<ext>" through the system share sheet. */
fun shareTextFile(context: Context, name: String, extension: String, content: String, title: String) {
    val file = File(sharedDir(context), "${safeName(name)}.$extension")
    file.writeText(content)
    shareFile(context, file, "application/octet-stream", title)
}

fun shareImage(context: Context, name: String, bitmap: Bitmap, title: String = "Share workout") {
    val file = File(sharedDir(context), "${safeName(name)}.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    shareFile(context, file, "image/png", title)
}

/** Reads a .wgjtemplate (or JSON) from a content Uri and saves it; reports the result as a toast. */
suspend fun importTemplateUri(context: Context, uri: Uri) {
    val message = runCatching {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } ?: error("Couldn't read the file.")
        val result = TemplateTransfer.import(text)
        val count = context.app.repository.importTemplates(result)
        if (result.folderName != null) "Imported folder \"${result.folderName}\" ($count templates)"
        else "Imported \"${result.templates.first().name}\""
    }.getOrElse { it.message ?: "Import failed" }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

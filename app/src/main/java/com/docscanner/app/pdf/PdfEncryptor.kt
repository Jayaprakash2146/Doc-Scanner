package com.docscanner.app.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File

class PdfEncryptor(context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun encrypt(source: File, password: String, output: File): File {
        PDDocument.load(source).use { document ->
            val perms = AccessPermission()
            val policy = StandardProtectionPolicy(password, password, perms)
            policy.encryptionKeyLength = 128
            document.protect(policy)
            document.save(output)
        }
        return output
    }
}

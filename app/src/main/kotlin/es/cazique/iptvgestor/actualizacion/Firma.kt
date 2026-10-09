package es.cazique.iptvgestor.actualizacion

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/** Huellas SHA-256 de los certificados de firma (de la app instalada o de un APK descargado). */
object Firma {
    fun instalada(context: Context): Set<String> {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        return huellas(info)
    }

    fun deApk(context: Context, ruta: String): Pair<String?, Set<String>> {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageArchiveInfo(ruta, PackageManager.GET_SIGNING_CERTIFICATES)
            val h = info?.let(::huellas).orEmpty()
            if (info != null && h.isNotEmpty()) return info.packageName to h
        }
        // Respaldo: en algunas versiones signingInfo de un archivo viene vacío; GET_SIGNATURES sí lo lee.
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(ruta, PackageManager.GET_SIGNATURES) ?: return null to emptySet()
        @Suppress("DEPRECATION")
        val firmas = info.signatures ?: emptyArray()
        return info.packageName to firmas.map { sha(it) }.toSet()
    }

    private fun sha(s: Signature): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun huellas(info: PackageInfo): Set<String> {
        val firmas: Array<Signature> = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: emptyArray()
        }
        return firmas.map(::sha).toSet()
    }
}

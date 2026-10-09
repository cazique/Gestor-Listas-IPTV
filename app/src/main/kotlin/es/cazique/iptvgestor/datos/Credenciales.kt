package es.cazique.iptvgestor.datos

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.cazique.iptvgestor.core.Cuenta
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.secretos: DataStore<Preferences> by preferencesDataStore(name = "credenciales")

/**
 * Credenciales del proveedor cifradas con AES-GCM y una clave de Android Keystore (sección 9).
 * No se usa EncryptedSharedPreferences (obsoleta desde security-crypto 1.1.0).
 */
class Credenciales(private val context: Context) {
    private val alias = "iptvgestor_credenciales"
    private val kHost = stringPreferencesKey("host")
    private val kUsuario = stringPreferencesKey("usuario")
    private val kContrasena = stringPreferencesKey("contrasena")

    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun cifrar(texto: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, clave())
        val datos = c.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + datos, Base64.NO_WRAP)
    }

    private fun descifrar(b64: String): String? = try {
        val todo = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, todo, 0, 12))
        String(c.doFinal(todo, 12, todo.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    suspend fun guardar(cuenta: Cuenta) {
        context.secretos.edit {
            it[kHost] = cifrar(cuenta.host)
            it[kUsuario] = cifrar(cuenta.usuario)
            it[kContrasena] = cifrar(cuenta.contrasena)
        }
    }

    suspend fun leer(): Cuenta? {
        val p = context.secretos.data.first()
        val h = p[kHost]?.let(::descifrar) ?: return null
        val u = p[kUsuario]?.let(::descifrar) ?: return null
        val c = p[kContrasena]?.let(::descifrar) ?: return null
        return Cuenta(h, u, c)
    }

    suspend fun borrar() { context.secretos.edit { it.clear() } }
}

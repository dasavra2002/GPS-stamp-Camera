package com.geostamp.camera.stamp

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.geostamp.camera.R
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec

/**
 * The device's photo-signing key, kept in the Android Keystore (TEE or StrongBox).
 * The private key can't be read out, so a proof can only be made on this phone, by this app.
 */
object DeviceKey {
    private const val ALIAS = "geostamp_capture_proof_v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private val CHALLENGE = "GeoStamp capture proof v1".toByteArray(Charsets.UTF_8)

    @Volatile private var cachedRoots: List<X509Certificate>? = null

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    @Synchronized
    private fun ensureKey(ks: KeyStore) {
        if (ks.containsAlias(ALIAS)) return
        // StrongBox first where the phone has one, then the TEE; attestation is dropped only as a last resort.
        val attempts = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(true to true)
            add(false to true)
            add(false to false)
        }
        for ((strongBox, attest) in attempts) {
            try {
                val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .apply {
                        if (attest) setAttestationChallenge(CHALLENGE)
                        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true)
                    }
                    .build()
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).run {
                    initialize(spec)
                    generateKeyPair()
                }
                return
            } catch (e: Exception) {
                if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
            }
        }
        throw IllegalStateException("Could not create a signing key")
    }

    /** Signs [payload] and returns the full proof, or null if the Keystore is unavailable. */
    fun sign(payload: ProofPayload): ProofRecord? = try {
        val ks = keyStore()
        ensureKey(ks)
        val key = ks.getKey(ALIAS, null) as PrivateKey
        val sig = Signature.getInstance(CaptureProof.SIGNATURE_ALGORITHM).run {
            initSign(key)
            update(payload.canonical().toByteArray(Charsets.UTF_8))
            sign()
        }
        val chain = ks.getCertificateChain(ALIAS).orEmpty().map { it.encoded }
        ProofRecord(payload, sig, chain)
    } catch (e: Exception) {
        null
    }

    /** Google's hardware attestation roots, bundled from https://android.googleapis.com/attestation/root */
    fun attestationRoots(context: Context): List<X509Certificate> = cachedRoots ?: run {
        val pem = context.resources.openRawResource(R.raw.attestation_roots).use { String(it.readBytes(), Charsets.US_ASCII) }
        CaptureProof.parsePemCertificates(pem).also { cachedRoots = it }
    }

    /** SHA-256 digests of this app's signing certificates, as they appear in key attestation. */
    @Suppress("DEPRECATION")
    fun appSigningDigests(context: Context): Set<String> = try {
        val pm = context.packageManager
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            if (info == null) emptyArray() else if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        sigs.orEmpty().map { CaptureProof.sha256Hex(it.toByteArray()) }.toSet()
    } catch (e: Exception) {
        emptySet()
    }
}

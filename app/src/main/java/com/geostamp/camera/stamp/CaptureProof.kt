package com.geostamp.camera.stamp

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

/**
 * Signed proof of where and when a photo was taken.
 *
 * The proof covers the photo's compressed image data (everything from the JPEG
 * start-of-scan marker on) plus the capture metadata. EXIF can be rewritten
 * without touching the image data, so the proof is stored in the photo's EXIF
 * (XMP) after it is computed and still verifies.
 *
 * The signing key lives in the Android Keystore and never leaves the device.
 * Its certificate chain (key attestation) is stored with the proof, so a
 * verifier can check that the key was made inside genuine Android hardware
 * for this app, not by someone forging photos on a PC.
 */
data class ProofPayload(
    val version: Int = VERSION,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val accuracyM: Float?,
    val time: String,
    val number: Int,
    /** Where the location came from: "phone-gps", "bluetooth-gnss:rtk-fixed", "gallery-exif", ... */
    val locationSource: String,
    val imageSha256: String,
    /** Seconds between the location fix and saving the photo (v2+). */
    val fixAgeS: Long? = null,
    /** Location came from a mock-location app (v2+). */
    val mock: Boolean? = null,
) {
    /** Exact bytes that are signed. Field order and number formatting are fixed. */
    fun canonical(): String = buildString {
        append("geostamp-proof:v").append(version).append('\n')
        append("lat=").append(latitude?.let { fmt("%.7f", it) } ?: "").append('\n')
        append("lon=").append(longitude?.let { fmt("%.7f", it) } ?: "").append('\n')
        append("alt=").append(altitude?.let { fmt("%.2f", it) } ?: "").append('\n')
        append("acc=").append(accuracyM?.let { fmt("%.2f", it.toDouble()) } ?: "").append('\n')
        append("time=").append(time).append('\n')
        append("number=").append(number).append('\n')
        append("source=").append(locationSource).append('\n')
        append("image=").append(imageSha256)
        if (version >= 2) {
            append('\n').append("fixAge=").append(fixAgeS?.toString() ?: "")
            append('\n').append("mock=").append(mock?.toString() ?: "")
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", version)
        latitude?.let { put("lat", it) }
        longitude?.let { put("lon", it) }
        altitude?.let { put("alt", it) }
        accuracyM?.let { put("acc", it.toDouble()) }
        put("time", time)
        put("number", number)
        put("source", locationSource)
        put("image", imageSha256)
        fixAgeS?.let { put("fixAge", it) }
        mock?.let { put("mock", it) }
    }

    companion object {
        const val VERSION = 2

        fun fromJson(o: JSONObject) = ProofPayload(
            version = o.getInt("v"),
            latitude = o.optDoubleOrNull("lat"),
            longitude = o.optDoubleOrNull("lon"),
            altitude = o.optDoubleOrNull("alt"),
            accuracyM = o.optDoubleOrNull("acc")?.toFloat(),
            time = o.getString("time"),
            number = o.getInt("number"),
            locationSource = o.getString("source"),
            imageSha256 = o.getString("image"),
            fixAgeS = if (o.has("fixAge")) o.getLong("fixAge") else null,
            mock = if (o.has("mock")) o.getBoolean("mock") else null,
        )

        fun timeString(time: ZonedDateTime): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time)

        private fun fmt(pattern: String, v: Double) = String.format(Locale.US, pattern, v)

        private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key)) getDouble(key) else null
    }
}

/** A payload, its signature, and the certificate chain of the key that signed it. */
data class ProofRecord(
    val payload: ProofPayload,
    val signature: ByteArray,
    /** DER certificates, leaf (the signing key) first. */
    val certChain: List<ByteArray>,
) {
    fun encode(): String {
        val o = JSONObject().apply {
            put("payload", payload.toJson())
            put("sig", b64(signature))
            put("chain", JSONArray().apply { certChain.forEach { put(b64(it)) } })
        }
        return Base64.getEncoder().encodeToString(o.toString().toByteArray(Charsets.UTF_8))
    }

    /** Short id printed on the stamp so a photo can be matched to its proof. */
    val proofId: String get() = CaptureProof.shortId(payload.canonical())

    companion object {
        fun decode(text: String): ProofRecord? = try {
            val o = JSONObject(String(Base64.getDecoder().decode(text.trim()), Charsets.UTF_8))
            val chain = o.getJSONArray("chain")
            ProofRecord(
                payload = ProofPayload.fromJson(o.getJSONObject("payload")),
                signature = Base64.getDecoder().decode(o.getString("sig")),
                certChain = (0 until chain.length()).map { Base64.getDecoder().decode(chain.getString(it)) },
            )
        } catch (e: Exception) {
            null
        }

        private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    }
}

enum class KeyTrust {
    /** Chain ends in a Google attestation root, key is in TEE/StrongBox, made for this app. */
    HARDWARE_THIS_APP,
    /** Chain ends in a Google root and the key is in hardware, but another app (or app signing key) made it. */
    HARDWARE_OTHER_APP,
    /** Key is not attested by Google hardware (software key, emulator, or a forged chain). */
    NOT_ATTESTED,
}

data class ProofCheck(
    val signatureValid: Boolean,
    val imageUnchanged: Boolean,
    val keyTrust: KeyTrust,
    /** Bootloader locked and verified boot passed, when the attestation says so. */
    val verifiedBoot: Boolean?,
    val payload: ProofPayload,
) {
    val authentic: Boolean get() = signatureValid && imageUnchanged && keyTrust == KeyTrust.HARDWARE_THIS_APP && payload.mock != true
}

object CaptureProof {
    const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val XMP_NS = "https://github.com/dasavra2002/GPS-stamp-Camera/ns/proof/1.0/"

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun shortId(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8)).take(12).uppercase(Locale.US)

    /**
     * Offset of the first start-of-scan (0xFFDA) marker, found by walking the
     * JPEG segment headers (a plain byte search can hit 0xFFDA inside EXIF data).
     * Returns -1 if the bytes are not a JPEG.
     */
    fun scanDataOffset(jpeg: ByteArray): Int {
        if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return -1
        var i = 2
        while (i + 3 < jpeg.size) {
            if (jpeg[i] != 0xFF.toByte()) return -1
            val marker = jpeg[i + 1].toInt() and 0xFF
            when {
                marker == 0xFF -> i++ // fill byte
                marker == 0xDA -> return i
                marker == 0x01 || marker in 0xD0..0xD7 -> i += 2 // markers without a length
                else -> {
                    val len = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
                    if (len < 2) return -1
                    i += 2 + len
                }
            }
        }
        return -1
    }

    /** SHA-256 of the JPEG image data, which stays the same when only EXIF is rewritten. */
    fun imageHash(jpeg: ByteArray): String? {
        val start = scanDataOffset(jpeg)
        if (start < 0) return null
        val md = MessageDigest.getInstance("SHA-256")
        md.update(jpeg, start, jpeg.size - start)
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifySignature(payload: ProofPayload, signature: ByteArray, key: PublicKey): Boolean = try {
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initVerify(key)
            update(payload.canonical().toByteArray(Charsets.UTF_8))
            verify(signature)
        }
    } catch (e: Exception) {
        false
    }

    fun parseCertificates(chain: List<ByteArray>): List<X509Certificate>? = try {
        val cf = CertificateFactory.getInstance("X.509")
        chain.map { cf.generateCertificate(ByteArrayInputStream(it)) as X509Certificate }
    } catch (e: Exception) {
        null
    }

    fun parsePemCertificates(pem: String): List<X509Certificate> {
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificates(ByteArrayInputStream(pem.toByteArray(Charsets.US_ASCII)))
            .map { it as X509Certificate }
    }

    /** True if each certificate is signed by the next and the last one is signed by a trusted root key. */
    fun chainEndsInRoot(chain: List<X509Certificate>, roots: List<X509Certificate>): Boolean {
        if (chain.isEmpty()) return false
        for (i in 0 until chain.size - 1) {
            if (!verifiedBy(chain[i], chain[i + 1].publicKey)) return false
        }
        val last = chain.last()
        return roots.any { root ->
            last.publicKey.encoded.contentEquals(root.publicKey.encoded) || verifiedBy(last, root.publicKey)
        }
    }

    private fun verifiedBy(cert: X509Certificate, key: PublicKey): Boolean = try {
        cert.verify(key); true
    } catch (e: Exception) {
        false
    }

    /**
     * Checks a proof against the image data it was read from.
     *
     * @param jpeg the whole photo file.
     * @param roots Google hardware attestation root certificates.
     * @param appSigningDigests SHA-256 digests of this app's signing certificates.
     */
    fun verify(
        record: ProofRecord,
        jpeg: ByteArray,
        roots: List<X509Certificate>,
        appSigningDigests: Set<String>,
    ): ProofCheck {
        val certs = parseCertificates(record.certChain).orEmpty()
        val leaf = certs.firstOrNull()
        val sigOk = leaf != null && verifySignature(record.payload, record.signature, leaf.publicKey)
        val imageOk = imageHash(jpeg) == record.payload.imageSha256

        var trust = KeyTrust.NOT_ATTESTED
        var boot: Boolean? = null
        val attestation = leaf?.getExtensionValue(KeyAttestation.OID)?.let { KeyAttestation.parse(it) }
        if (leaf != null && attestation != null && chainEndsInRoot(certs, roots) &&
            attestation.securityLevel >= KeyAttestation.LEVEL_TEE
        ) {
            trust = if (attestation.appSigningDigests.any { it in appSigningDigests }) {
                KeyTrust.HARDWARE_THIS_APP
            } else {
                KeyTrust.HARDWARE_OTHER_APP
            }
            boot = attestation.verifiedBoot
        }
        return ProofCheck(sigOk, imageOk, trust, boot, record.payload)
    }

    /** XMP packet holding an encoded [ProofRecord]. */
    fun toXmp(record: ProofRecord): String =
        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description rdf:about=\"\" xmlns:geostamp=\"$XMP_NS\" geostamp:Proof=\"${record.encode()}\"/>" +
            "</rdf:RDF></x:xmpmeta>"

    fun fromXmp(xmp: String?): ProofRecord? {
        if (xmp == null) return null
        val m = Regex("geostamp:Proof=\"([A-Za-z0-9+/=]+)\"").find(xmp) ?: return null
        return ProofRecord.decode(m.groupValues[1])
    }
}

/**
 * Minimal reader for the Android key attestation extension (OID 1.3.6.1.4.1.11129.2.1.17).
 * Only reads what the verifier needs: security level, app signing digests and verified boot.
 */
object KeyAttestation {
    const val OID = "1.3.6.1.4.1.11129.2.1.17"
    const val LEVEL_SOFTWARE = 0
    const val LEVEL_TEE = 1
    const val LEVEL_STRONGBOX = 2

    private const val TAG_ROOT_OF_TRUST = 704
    private const val TAG_APPLICATION_ID = 709

    data class Info(
        val securityLevel: Int,
        val appSigningDigests: Set<String>,
        val verifiedBoot: Boolean?,
    )

    /** [extensionValue] is what X509Certificate.getExtensionValue returns (an OCTET STRING wrapper). */
    fun parse(extensionValue: ByteArray): Info? = try {
        val outer = Der.read(extensionValue, 0)
        val desc = Der.children(Der.read(outer.content, 0))
        val securityLevel = Der.int(desc[1])
        val software = Der.children(desc[6])
        val hardware = Der.children(desc[7])

        val appId = (software + hardware).firstOrNull { it.tag == TAG_APPLICATION_ID && it.contextSpecific }
        val digests = appId?.let { tagged ->
            val octets = Der.read(tagged.content, 0) // EXPLICIT [709] OCTET STRING
            val seq = Der.children(Der.read(octets.content, 0))
            Der.children(seq[1]).map { d -> d.content.joinToString("") { "%02x".format(it) } }.toSet()
        }.orEmpty()

        val rootOfTrust = hardware.firstOrNull { it.tag == TAG_ROOT_OF_TRUST && it.contextSpecific }
        val boot = rootOfTrust?.let { tagged ->
            val rot = Der.children(Der.read(tagged.content, 0))
            val locked = rot[1].content.firstOrNull()?.toInt() != 0
            val state = Der.int(rot[2]) // 0 = Verified
            locked && state == 0
        }
        Info(securityLevel, digests, boot)
    } catch (e: Exception) {
        null
    }

    /** Tiny DER decoder supporting high tag numbers and definite lengths. */
    internal object Der {
        class Node(val tag: Int, val contextSpecific: Boolean, val constructed: Boolean, val content: ByteArray, val end: Int)

        fun read(b: ByteArray, start: Int): Node {
            var i = start
            val first = b[i++].toInt() and 0xFF
            val cls = first shr 6
            val constructed = first and 0x20 != 0
            var tag = first and 0x1F
            if (tag == 0x1F) {
                tag = 0
                do {
                    val x = b[i++].toInt() and 0xFF
                    tag = (tag shl 7) or (x and 0x7F)
                } while (x and 0x80 != 0)
            }
            var len = b[i++].toInt() and 0xFF
            if (len and 0x80 != 0) {
                val n = len and 0x7F
                require(n in 1..4)
                len = 0
                repeat(n) { len = (len shl 8) or (b[i++].toInt() and 0xFF) }
            }
            require(len >= 0 && i + len <= b.size)
            return Node(tag, cls == 2, constructed, b.copyOfRange(i, i + len), i + len)
        }

        fun children(n: Node): List<Node> {
            val out = mutableListOf<Node>()
            var i = 0
            while (i < n.content.size) {
                val c = read(n.content, i)
                out += c
                i = c.end
            }
            return out
        }

        fun int(n: Node): Int = n.content.fold(0) { acc, x -> (acc shl 8) or (x.toInt() and 0xFF) }
    }
}

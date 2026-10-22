package com.geostamp.camera

import com.geostamp.camera.stamp.CaptureProof
import com.geostamp.camera.stamp.KeyAttestation
import com.geostamp.camera.stamp.KeyTrust
import com.geostamp.camera.stamp.ProofPayload
import com.geostamp.camera.stamp.ProofRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class CaptureProofTest {

    /** A fake JPEG: SOI, an APP1 segment whose data contains FF DA, then SOS and scan bytes. */
    private fun jpeg(app1: ByteArray, scan: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        write(byteArrayOf(0xFF.toByte(), 0xE1.toByte()))
        val len = app1.size + 2
        write(byteArrayOf((len shr 8).toByte(), len.toByte()))
        write(app1)
        write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x04, 0x01, 0x02))
        write(scan)
        write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
    }.toByteArray()

    private val exifA = byteArrayOf(0x45, 0x78, 0xFF.toByte(), 0xDA.toByte(), 0x10, 0x20)
    private val exifB = "Exif with a different, longer address".toByteArray()

    private fun payload(hash: String, lat: Double = 22.5726) = ProofPayload(
        latitude = lat, longitude = 88.3639, altitude = 9.0, accuracyM = 3.5f,
        time = "2026-10-21T10:00:00+05:30", number = 7, locationSource = "phone-gps", imageSha256 = hash,
    )

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(p: ProofPayload, kp: KeyPair): ByteArray = Signature.getInstance(CaptureProof.SIGNATURE_ALGORITHM).run {
        initSign(kp.private)
        update(p.canonical().toByteArray(Charsets.UTF_8))
        sign()
    }

    @Test
    fun scanOffsetSkipsMarkerBytesInsideExif() {
        val bytes = jpeg(exifA, byteArrayOf(1, 2, 3))
        assertEquals(2 + 2 + 2 + exifA.size, CaptureProof.scanDataOffset(bytes))
    }

    @Test
    fun imageHashIgnoresExifChanges() {
        val scan = byteArrayOf(9, 8, 7, 6, 5)
        assertEquals(CaptureProof.imageHash(jpeg(exifA, scan)), CaptureProof.imageHash(jpeg(exifB, scan)))
    }

    @Test
    fun imageHashChangesWhenPixelsChange() {
        assertNotEquals(
            CaptureProof.imageHash(jpeg(exifA, byteArrayOf(1, 2, 3))),
            CaptureProof.imageHash(jpeg(exifA, byteArrayOf(1, 2, 4))),
        )
    }

    @Test
    fun imageHashRejectsNonJpeg() {
        assertNull(CaptureProof.imageHash("not a jpeg".toByteArray()))
    }

    @Test
    fun signatureVerifiesAndDetectsTampering() {
        val kp = keyPair()
        val p = payload("ab".repeat(32))
        val sig = sign(p, kp)
        assertTrue(CaptureProof.verifySignature(p, sig, kp.public))
        assertFalse(CaptureProof.verifySignature(p.copy(latitude = 22.5727), sig, kp.public))
        assertFalse(CaptureProof.verifySignature(p.copy(locationSource = "bluetooth-gnss"), sig, kp.public))
        assertFalse(CaptureProof.verifySignature(p, sig, keyPair().public))
    }

    @Test
    fun recordRoundTripsThroughXmp() {
        val p = payload("cd".repeat(32))
        val record = ProofRecord(p, byteArrayOf(1, 2, 3), listOf(byteArrayOf(4, 5), byteArrayOf(6)))
        val back = CaptureProof.fromXmp(CaptureProof.toXmp(record))
        assertNotNull(back)
        assertEquals(p, back!!.payload)
        assertTrue(back.signature.contentEquals(record.signature))
        assertEquals(2, back.certChain.size)
        assertTrue(back.certChain[0].contentEquals(byteArrayOf(4, 5)))
    }

    @Test
    fun canonicalFormIsStable() {
        val p = payload("ef".repeat(32))
        assertEquals(p.canonical(), ProofPayload.fromJson(p.toJson()).canonical())
        assertTrue(p.canonical().startsWith("geostamp-proof:v${ProofPayload.VERSION}\nlat=22.5726000\n"))
    }

    @Test
    fun proofWithoutCertificatesIsNotTrusted() {
        val scan = byteArrayOf(1, 1, 2, 3, 5, 8)
        val photo = jpeg(exifA, scan)
        val p = payload(CaptureProof.imageHash(photo)!!)
        val check = CaptureProof.verify(ProofRecord(p, sign(p, keyPair()), emptyList()), photo, emptyList(), emptySet())
        assertFalse(check.signatureValid)
        assertTrue(check.imageUnchanged)
        assertEquals(KeyTrust.NOT_ATTESTED, check.keyTrust)
        assertFalse(check.authentic)
    }

    // --- Key attestation extension ---

    private fun der(tag: Int, content: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(tag); writeLen(this, content.size); write(content)
    }.toByteArray()

    /** Context-specific constructed tag with a high tag number (e.g. [709]). */
    private fun tagged(number: Int, content: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(0xBF)
        write(0x80 or (number shr 7)); write(number and 0x7F)
        writeLen(this, content.size); write(content)
    }.toByteArray()

    private fun writeLen(out: ByteArrayOutputStream, n: Int) {
        if (n < 128) out.write(n) else { out.write(0x82); out.write(n shr 8); out.write(n and 0xFF) }
    }

    private fun seq(vararg parts: ByteArray) = der(0x30, parts.fold(ByteArray(0)) { a, b -> a + b })
    private fun set(vararg parts: ByteArray) = der(0x31, parts.fold(ByteArray(0)) { a, b -> a + b })
    private fun int(v: Int) = der(0x02, byteArrayOf(v.toByte()))
    private fun enum(v: Int) = der(0x0A, byteArrayOf(v.toByte()))
    private fun octets(b: ByteArray) = der(0x04, b)

    @Test
    fun parsesAttestationExtension() {
        val digest = ByteArray(32) { it.toByte() }
        val appId = seq(set(seq(octets("com.geostamp.camera".toByteArray()), int(4))), set(octets(digest)))
        val software = seq(tagged(709, octets(appId)))
        val rootOfTrust = seq(octets(ByteArray(32)), der(0x01, byteArrayOf(0xFF.toByte())), enum(0))
        val hardware = seq(tagged(704, rootOfTrust))
        val keyDescription = seq(int(200), enum(2), int(200), enum(2), octets(ByteArray(4)), octets(ByteArray(0)), software, hardware)
        val info = KeyAttestation.parse(octets(keyDescription))
        assertNotNull(info)
        assertEquals(KeyAttestation.LEVEL_STRONGBOX, info!!.securityLevel)
        assertEquals(setOf(digest.joinToString("") { "%02x".format(it) }), info.appSigningDigests)
        assertEquals(true, info.verifiedBoot)
    }

    @Test
    fun unlockedBootloaderIsReported() {
        val rootOfTrust = seq(octets(ByteArray(32)), der(0x01, byteArrayOf(0)), enum(2))
        val keyDescription = seq(int(3), enum(1), int(4), enum(1), octets(ByteArray(4)), octets(ByteArray(0)), seq(), seq(tagged(704, rootOfTrust)))
        val info = KeyAttestation.parse(octets(keyDescription))!!
        assertEquals(KeyAttestation.LEVEL_TEE, info.securityLevel)
        assertEquals(false, info.verifiedBoot)
        assertTrue(info.appSigningDigests.isEmpty())
    }
}

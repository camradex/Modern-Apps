package com.vayunmathur.messages.signal.contacts

import android.util.Base64
import android.util.Log
import com.vayunmathur.messages.signal.proto.ContactRecord
import com.vayunmathur.messages.signal.proto.GroupV2Record
import com.vayunmathur.messages.signal.proto.ManifestRecord
import com.vayunmathur.messages.signal.proto.ReadOperation
import com.vayunmathur.messages.signal.proto.StorageItems
import com.vayunmathur.messages.signal.proto.StorageManifest
import com.vayunmathur.messages.signal.proto.StorageRecord
import com.vayunmathur.messages.signal.store.SignalGroupEntity
import com.vayunmathur.messages.signal.store.SignalGroupStore
import com.vayunmathur.messages.signal.store.SignalRecipientEntity
import com.vayunmathur.messages.signal.store.SignalRecipientStore
import com.vayunmathur.messages.signal.web.SignalHttpClient
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class StorageServiceManager(
    private val recipientStore: SignalRecipientStore,
    private val groupStore: SignalGroupStore,
    private val selfAci: String,
    private val password: String,
) {
    companion object {
        private const val TAG = "StorageService"
        private const val MAX_READ_STORAGE_RECORDS = 2500
    }

    suspend fun syncStorage(masterKey: ByteArray) {
        try {
            val storageKey = deriveStorageServiceKey(masterKey)
            val manifest = fetchManifest(storageKey) ?: return
            val records = fetchRecords(storageKey, manifest)
            processRecords(records)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync storage", e)
        }
    }

    private suspend fun fetchManifest(storageKey: ByteArray): ManifestRecord? {
        val resp = SignalHttpClient.request(
            host = SignalHttpClient.STORAGE_HOST, method = "GET",
            path = "/v1/storage/manifest",
            username = selfAci, password = password,
            contentType = "application/x-protobuf",
        )
        if (resp.code == 204 || !resp.isSuccessful) return null
        val body = resp.body?.bytes() ?: return null
        val enc = StorageManifest.parseFrom(body)
        val mKey = deriveManifestKey(storageKey, enc.version)
        val dec = decryptAESGCM(mKey, enc.value.toByteArray())
        return ManifestRecord.parseFrom(dec)
    }

    private suspend fun fetchRecords(
        storageKey: ByteArray,
        manifest: ManifestRecord,
    ): List<StorageRecord> {
        val allKeys = manifest.identifiersList.map { it.raw.toByteArray() }
        val results = mutableListOf<StorageRecord>()
        for (chunk in allKeys.chunked(MAX_READ_STORAGE_RECORDS)) {
            val readOp = ReadOperation.newBuilder()
            chunk.forEach { readOp.addReadKey(com.google.protobuf.ByteString.copyFrom(it)) }
            val resp = SignalHttpClient.request(
                host = SignalHttpClient.STORAGE_HOST, method = "PUT",
                path = "/v1/storage/read",
                body = readOp.build().toByteArray(),
                username = selfAci, password = password,
                contentType = "application/x-protobuf",
            )
            if (!resp.isSuccessful) continue
            val items = StorageItems.parseFrom(resp.body?.bytes() ?: continue)
            items.itemsList.mapNotNull { item ->
                try {
                    val b64 = Base64.encodeToString(item.key.toByteArray(), Base64.NO_WRAP)
                    val iKey = deriveItemKey(storageKey, b64)
                    StorageRecord.parseFrom(decryptAESGCM(iKey, item.value.toByteArray()))
                } catch (e: Exception) { null }
            }.let { results.addAll(it) }
        }
        return results
    }

    private suspend fun processRecords(records: List<StorageRecord>) {
        for (r in records) {
            try {
                if (r.hasContact()) processContact(r.contact)
                else if (r.hasGroupV2()) processGroup(r.groupV2)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to process record", e)
            }
        }
    }

    private suspend fun processContact(c: ContactRecord) {
        val aci = c.aci.takeIf { it.isNotBlank() } ?: return
        val pni = if (c.hasPni() && c.pni.isNotBlank()) c.pni else null
        val name = listOf(c.givenName, c.familyName)
            .filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank {
                listOf(c.systemGivenName, c.systemFamilyName)
                    .filter { it.isNotBlank() }.joinToString(" ")
            }
        val existing = recipientStore.getRecipient(aci)
        val pk = if (c.profileKey.size() == 32)
            c.profileKey.toByteArray()
        else existing?.profileKey
        recipientStore.storeRecipient(SignalRecipientEntity(
            aci = aci,
            pni = pni ?: existing?.pni,
            e164 = c.e164.takeIf { it.isNotBlank() } ?: existing?.e164,
            profileName = name.takeIf { it.isNotBlank() } ?: existing?.profileName,
            profileKey = pk,
        ))
    }

    private suspend fun processGroup(g: GroupV2Record) {
        if (g.masterKey.size() != 32) return
        val b64 = Base64.encodeToString(g.masterKey.toByteArray(), Base64.NO_WRAP)
        groupStore.storeGroup(SignalGroupEntity(groupId = b64, masterKey = g.masterKey.toByteArray(), revision = 0))
    }

    private fun deriveStorageServiceKey(masterKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
        return mac.doFinal("Storage Service Encryption".toByteArray())
    }

    private fun deriveManifestKey(storageKey: ByteArray, version: Long): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(storageKey, "HmacSHA256"))
        return mac.doFinal("Manifest_$version".toByteArray())
    }

    private fun deriveItemKey(storageKey: ByteArray, b64ItemId: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(storageKey, "HmacSHA256"))
        return mac.doFinal("Item_$b64ItemId".toByteArray())
    }

    private fun decryptAESGCM(key: ByteArray, data: ByteArray): ByteArray {
        val nonce = data.copyOfRange(0, 12)
        val ciphertext = data.copyOfRange(12, data.size)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, nonce))
        return cipher.doFinal(ciphertext)
    }
}
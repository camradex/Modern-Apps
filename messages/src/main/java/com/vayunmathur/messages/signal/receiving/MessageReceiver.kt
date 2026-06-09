package com.vayunmathur.messages.signal.receiving

import android.util.Log
import com.vayunmathur.messages.signal.proto.WebSocketProtos
import com.vayunmathur.messages.signal.proto.SignalServiceProtos
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyStore
import org.signal.libsignal.protocol.groups.state.SenderKeyStore
import org.signal.libsignal.protocol.groups.GroupSessionBuilder
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage
import org.signal.libsignal.protocol.SignalProtocolAddress

class MessageReceiver(
    private val sessionStore: SessionStore,
    private val identityKeyStore: IdentityKeyStore,
    private val preKeyStore: PreKeyStore,
    private val signedPreKeyStore: SignedPreKeyStore,
    private val kyberPreKeyStore: KyberPreKeyStore,
    private val senderKeyStore: SenderKeyStore,
    private val selfAci: String,
    private val deviceId: Int,
    private val onDecrypted: (DecryptedMessage) -> Unit,
    private val recipientStore: com.vayunmathur.messages.signal.store.SignalRecipientStore? = null,
) {
    fun handleRequest(request: WebSocketProtos.WebSocketRequestMessage) {
        if (request.verb == "PUT" && request.path == "/api/v1/queue/empty") {
            Log.d(TAG, "Received queue empty notice")
            return
        }
        if (request.verb != "PUT" || request.path != "/api/v1/message") {
            Log.d(TAG, "Ignoring request: ${request.verb} ${request.path}")
            return
        }

        val envelope = try {
            SignalServiceProtos.Envelope.parseFrom(request.body)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse envelope", e)
            return
        }

        val result = EnvelopeDecryptor.decrypt(
            envelope = envelope,
            sessionStore = sessionStore,
            identityKeyStore = identityKeyStore,
            preKeyStore = preKeyStore,
            signedPreKeyStore = signedPreKeyStore,
            kyberPreKeyStore = kyberPreKeyStore,
            senderKeyStore = senderKeyStore,
            certificateValidator = null,
            selfAci = selfAci,
            selfDeviceId = deviceId,
        )

        if (result.error != null) {
            Log.e(TAG, "Decryption failed from ${result.senderAci}:${result.senderDeviceId}", result.error)
            return
        }

        val content = result.content

        if (content != null && content.hasSenderKeyDistributionMessage()) {
            try {
                val skdmBytes = content.senderKeyDistributionMessage.toByteArray()
                val skdm = SenderKeyDistributionMessage(skdmBytes)
                val senderAddress = SignalProtocolAddress(result.senderAci, result.senderDeviceId)
                val groupBuilder = GroupSessionBuilder(senderKeyStore)
                groupBuilder.process(senderAddress, skdm)
                Log.d(TAG, "Processed SKDM from ${result.senderAci}:${result.senderDeviceId}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to process SKDM from ${result.senderAci}", e)
            }
        }

        if (content != null && content.hasDataMessage() && content.dataMessage.profileKey.size() == 32) {
            try {
                recipientStore?.storeProfileKey(
                    result.senderAci,
                    content.dataMessage.profileKey.toByteArray()
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to store profile key from ${result.senderAci}", e)
            }
        }

        if (content == null) {
            Log.d(TAG, "No content (server delivery receipt) from ${result.senderAci}")
            onDecrypted(
                DecryptedMessage(
                    senderAci = result.senderAci,
                    senderDeviceId = result.senderDeviceId,
                    timestamp = result.timestamp,
                    serverTimestamp = result.serverTimestamp,
                    content = MessageContent.DeliveryReceipt(timestamps = listOf(result.timestamp)),
                )
            )
            return
        }

        val message = ContentDispatcher.dispatch(
            senderAci = result.senderAci,
            senderDeviceId = result.senderDeviceId,
            content = content,
            timestamp = result.timestamp,
            serverTimestamp = result.serverTimestamp,
        )
        onDecrypted(message)
    }

    companion object {
        private const val TAG = "SignalReceiver"
    }
}

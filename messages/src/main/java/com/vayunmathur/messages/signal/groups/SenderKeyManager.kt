package com.vayunmathur.messages.signal.groups

import android.util.Log
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.GroupCipher
import org.signal.libsignal.protocol.groups.GroupSessionBuilder
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage
import org.signal.libsignal.protocol.groups.state.SenderKeyStore
import java.util.UUID

class SenderKeyManager(
    private val senderKeyStore: SenderKeyStore,
    private val selfAci: String,
    private val selfDeviceId: Int,
) {
    companion object {
        private const val TAG = "SenderKeyManager"
        private const val SENDER_KEY_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000 // 14 days
    }

    private val distributedTo = mutableMapOf<String, MutableSet<String>>()
    private val distributionIds = mutableMapOf<String, UUID>()
    private val distributionCreatedAt = mutableMapOf<String, Long>()

    fun createDistributionMessage(groupId: String): SenderKeyDistributionMessage {
        val selfAddress = SignalProtocolAddress(selfAci, selfDeviceId)
        val now = System.currentTimeMillis()
        val createdAt = distributionCreatedAt[groupId]
        if (createdAt != null && (now - createdAt) > SENDER_KEY_MAX_AGE_MS) {
            resetForGroup(groupId)
        }
        val distributionId = distributionIds.getOrPut(groupId) {
            distributionCreatedAt[groupId] = now
            UUID.randomUUID()
        }
        val builder = GroupSessionBuilder(senderKeyStore)
        return builder.create(selfAddress, distributionId)
    }

    fun needsDistribution(groupId: String, memberAci: String): Boolean {
        val distributed = distributedTo[groupId] ?: return true
        return memberAci !in distributed
    }

    fun markDistributed(groupId: String, memberAci: String) {
        distributedTo.getOrPut(groupId) { mutableSetOf() }.add(memberAci)
    }

    fun resetForGroup(groupId: String) {
        distributedTo.remove(groupId)
        distributionIds.remove(groupId)
        distributionCreatedAt.remove(groupId)
        Log.d(TAG, "Reset sender key distribution for group $groupId")
    }
}

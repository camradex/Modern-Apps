package com.vayunmathur.messages.signal.groups

import android.util.Base64
import android.util.Log
import com.vayunmathur.messages.signal.proto.Group
import com.vayunmathur.messages.signal.store.SignalGroupEntity
import com.vayunmathur.messages.signal.store.SignalGroupStore
import com.vayunmathur.messages.signal.web.SignalHttpClient
import com.vayunmathur.messages.signal.web.SignalWebSocket
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject
import org.signal.libsignal.zkgroup.ServerPublicParams
import org.signal.libsignal.zkgroup.auth.AuthCredentialWithPniResponse
import org.signal.libsignal.zkgroup.auth.ClientZkAuthOperations
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.groups.GroupSecretParams

class GroupManager(
    private val ws: SignalWebSocket,
    private val groupStore: SignalGroupStore,
    private val aci: String,
    private val pni: String,
    private val password: String,
) {
    enum class AccessControl(val value: Int) {
        UNKNOWN(0), ANY(1), MEMBER(2), ADMINISTRATOR(3), UNSATISFIABLE(4)
    }

    enum class MemberRole(val value: Int) {
        UNKNOWN(0), DEFAULT(1), ADMINISTRATOR(2)
    }

    data class SignalGroup(
        val groupId: String,
        val title: String,
        val memberAcis: List<String>,
        val avatarUrl: String?,
        val revision: Int,
        val description: String? = null,
        val disappearingMessagesTimer: Int = 0,
        val announcementsOnly: Boolean = false,
    )

    private val cache = ConcurrentHashMap<String, SignalGroup>()
    private val credentialCache = ConcurrentHashMap<Long, ByteArray>()

    suspend fun fetchGroup(groupId: String, masterKey: ByteArray): SignalGroup? {
        return try {
            val auth = getGroupAuth(masterKey) ?: return null

            val response = SignalHttpClient.request(
                host = SignalHttpClient.STORAGE_HOST,
                method = "GET",
                path = "/v2/groups",
                contentType = "application/x-protobuf",
                username = auth.username,
                password = auth.password,
            )
            if (response.code !in 200..299) return null

            val groupProto = Group.parseFrom(response.body?.bytes())
            val group = SignalGroup(
                groupId = groupId,
                title = groupProto.title.toStringUtf8(),
                memberAcis = groupProto.membersList.map {
                    val bb = ByteBuffer.wrap(it.userId.toByteArray())
                    UUID(bb.getLong(), bb.getLong()).toString()
                },
                avatarUrl = groupProto.avatarUrl.ifEmpty { null },
                revision = groupProto.version,
            )

            cache[groupId] = group
            groupStore.storeGroup(
                SignalGroupEntity(
                    groupId = groupId,
                    masterKey = masterKey,
                    title = group.title,
                    avatarUrl = group.avatarUrl,
                    revision = group.revision,
                )
            )
            group
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch group $groupId", e)
            null
        }
    }

    suspend fun getOrFetchGroup(groupId: String, masterKey: ByteArray): SignalGroup? {
        cache[groupId]?.let { return it }
        return fetchGroup(groupId, masterKey)
    }

    fun getCachedGroup(groupId: String): SignalGroup? = cache[groupId]

    fun deriveGroupId(masterKey: ByteArray): String {
        val groupMasterKey = GroupMasterKey(masterKey)
        val groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey)
        val groupId = groupSecretParams.publicParams.groupIdentifier.serialize()
        return Base64.encodeToString(groupId, Base64.NO_WRAP)
    }

    private data class GroupAuthResult(val username: String, val password: String)

    private suspend fun getGroupAuth(masterKey: ByteArray): GroupAuthResult? {
        return try {
            val groupMasterKey = GroupMasterKey(masterKey)
            val groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey)
            val groupPublicParams = groupSecretParams.publicParams

            val todaySeconds = (System.currentTimeMillis() / 1000 / 86400) * 86400
            val credential = getOrFetchCredential(todaySeconds) ?: return null

            val serverPublicParams = ServerPublicParams(SERVER_PUBLIC_PARAMS)
            val clientZkAuth = ClientZkAuthOperations(serverPublicParams)

            val authCredResponse = AuthCredentialWithPniResponse(credential)
            val authCred = clientZkAuth.receiveAuthCredentialWithPniAsServiceId(
                java.util.UUID.fromString(aci),
                java.util.UUID.fromString(pni),
                todaySeconds,
                authCredResponse,
            )
            val presentation = clientZkAuth.createAuthCredentialPresentation(
                java.security.SecureRandom(),
                groupSecretParams,
                authCred,
            )

            GroupAuthResult(
                username = hexEncode(groupPublicParams.serialize()),
                password = hexEncode(presentation.serialize()),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get group auth", e)
            null
        }
    }

    private suspend fun getOrFetchCredential(todaySeconds: Long): ByteArray? {
        credentialCache[todaySeconds]?.let { return it }
        return try {
            val sevenDays = todaySeconds + 7 * 86400
            val path = "/v1/certificate/auth/group?redemptionStartSeconds=$todaySeconds&redemptionEndSeconds=$sevenDays&pniAsServiceId=true"
            val response = ws.sendRequest("GET", path)
            if (response.status !in 200..299) return null

            val json = JSONObject(response.body)
            val credentials = json.getJSONArray("credentials")
            for (i in 0 until credentials.length()) {
                val cred = credentials.getJSONObject(i)
                val redemptionTime = cred.getLong("redemptionTime")
                val credBytes = Base64.decode(cred.getString("credential"), Base64.NO_WRAP)
                credentialCache[redemptionTime] = credBytes
            }
            credentialCache[todaySeconds]
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch group credentials", e)
            null
        }
    }

    companion object {
        private const val TAG = "GroupManager"

        private val SERVER_PUBLIC_PARAMS = Base64.decode(
            "AMhf5ywVwITZMsff/eCyudZx9JDmkkkbV6PInzG4p8x3VqVJSFiMvnvlEKWuRob/1eaIetR31IYeAbm0NdOuHH8Qi+Rexi1wLlpzIo1gstHWBfZzy1+qHRV5A4TqPp15YzBPm0WSggW6PbSn+F4lf57VCnHF7p8SvzAA2ZZJPYJURt8X7bbg+H3i+PEjH9DXItNEqs2sNcug37xZQDLm7X36nOoGPs54XsEGzPdEV+itQNGUFEjY6X9Uv+Acuks7NpyGvCoKxGwgKgE5XyJ+nNKlyHHOLb6N1NuHyBrZrgtY/JYJHRooo5CEqYKBqdFnmbTVGEkCvJKxLnjwKWf+fEPoWeQFj5ObDjcKMZf2Jm2Ae69x+ikU5gBXsRmoF94GXTLfN0/vLt98KDPnxwAQL9j5V1jGOY8jQl6MLxEs56cwXN0dqCnImzVH3TZT1cJ8SW1BRX6qIVxEzjsSGx3yxF3suAilPMqGRp4ffyopjMD1JXiKR2RwLKzizUe5e8XyGOy9fplzhw3jVzTRyUZTRSZKkMLWcQ/gv0E4aONNqs4P+NameAZYOD12qRkxosQQP5uux6B2nRyZ7sAV54DgFyLiRcq1FvwKw2EPQdk4HDoePrO/RNUbyNddnM/mMgj4FW65xCoT1LmjrIjsv/Ggdlx46ueczhMgtBunx1/w8k8V+l8LVZ8gAT6wkU5J+DPQalQguMg12Jzug3q4TbdHiGCmD9EunCwOmsLuLJkz6EcSYXtrlDEnAM+hicw7iergYLLlMXpfTdGxJCWJmP4zqUFeTTmsmhsjGBt7NiEB/9pFFEB3pSbf4iiUukw63Eo8Aqnf4iwob6X1QviCWuc8t0LUlT9vALgh/f2DPVOOmR0RW6bgRvc7DSF20V/omg+YBw==",
            Base64.NO_WRAP,
        )

        private fun hexEncode(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

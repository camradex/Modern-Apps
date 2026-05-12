package com.vayunmathur.contacts.util
import android.app.Application
import android.provider.ContactsContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vayunmathur.contacts.data.Contact
import com.vayunmathur.contacts.data.ContactDatabase
import com.vayunmathur.contacts.data.ContactEntity
import com.vayunmathur.contacts.data.ContactSearchEntity
import com.vayunmathur.library.util.DataStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactAccount(val name: String, val type: String)

class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = DataStoreUtils.getInstance(application)

    private val database = ContactDatabase.getInstance(application)
    private val contactDao = database.contactDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val hiddenAccounts: StateFlow<Set<String>> = dataStore.stringSetFlow("hidden_accounts")
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val contacts: StateFlow<List<com.vayunmathur.contacts.data.Contact>> = combine(
        _searchQuery.flatMapLatest { query ->
            if (query.isBlank()) {
                contactDao.getContactsFlow()
            } else {
                contactDao.search("*$query*")
            }
        },
        hiddenAccounts
    ) { entities, hidden ->
        entities.map { it.toContact() }.filter { it.accountName !in hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _accounts = MutableStateFlow<List<ContactAccount>>(emptyList())
    val accounts: StateFlow<List<ContactAccount>> = _accounts.asStateFlow()

    val isCalendarSyncEnabled: StateFlow<Boolean> = dataStore.booleanFlow("calendar_sync_enabled")
        .stateIn(viewModelScope, SharingStarted.Eagerly, dataStore.getBoolean("calendar_sync_enabled", false))

    val showAccountLabels: StateFlow<Boolean> = dataStore.booleanFlow("show_account_labels")
        .stateIn(viewModelScope, SharingStarted.Eagerly, dataStore.getBoolean("show_account_labels", true))

    init {
        syncWithSystemContacts()
        loadAccounts()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCalendarSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setBoolean("calendar_sync_enabled", enabled)
            if (enabled) {
                withContext(Dispatchers.IO) {
                    CalendarSyncHelper.syncAll(getApplication())
                }
                CalendarWorker.schedule(getApplication())
            } else {
                withContext(Dispatchers.IO) {
                    CalendarSyncHelper.removeCalendar(getApplication())
                }
                CalendarWorker.cancel(getApplication())
            }
        }
    }

    fun setShowAccountLabels(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setBoolean("show_account_labels", enabled)
        }
    }

    fun syncWithSystemContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val systemContacts = com.vayunmathur.contacts.data.Contact.getAllContacts(getApplication())
                val localContacts = contacts.value
                
                val toUpsert = systemContacts.map { ContactEntity.fromContact(it) }
                val searchEntities = systemContacts.map { contact ->
                    ContactSearchEntity(
                        rowid = contact.id,
                        displayName = contact.name.value,
                        nickname = contact.nickname.value,
                        phoneNumbers = contact.details.phoneNumbers.joinToString(" ") { it.number },
                        emails = contact.details.emails.joinToString(" ") { it.address },
                        notes = contact.details.notes.joinToString(" ") { it.content },
                        organization = contact.details.orgs.joinToString(" ") { it.company }
                    )
                }
                
                val systemIds = systemContacts.map { it.id }.toSet()
                val toDelete = localContacts.map { it.id }.filter { it !in systemIds }
                
                contactDao.syncContacts(toUpsert, toDelete, searchEntities)
                Log.d("ContactViewModel", "Sync complete: ${toUpsert.size} upserted, ${toDelete.size} deleted")
            } catch (e: Exception) {
                Log.e("ContactViewModel", "Error syncing contacts", e)
            }
        }
    }

    fun loadContacts() {
        syncWithSystemContacts()
    }

    fun loadAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val uri = ContactsContract.RawContacts.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE
            )
            val accountSet = mutableSetOf<ContactAccount>()
            try {
                val cursor = app.contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    while (it.moveToNext()) {
                        try {
                            val name = it.getString(0) ?: ""
                            val type = it.getString(1) ?: ""
                            accountSet.add(ContactAccount(name, type))
                        } catch (e: Exception) {
                            Log.e("ContactViewModel", "Error reading account from cursor", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ContactViewModel", "Error querying raw contacts for accounts", e)
            }
            // Also include any accounts from DataStore that might not have contacts yet
            val savedAccounts = dataStore.getString("extra_accounts")?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { 
                try {
                    val parts = it.split("|")
                    ContactAccount(parts[0], parts.getOrElse(1) { "com.vayunmathur.contacts.local" })
                } catch (e: Exception) {
                    Log.e("ContactViewModel", "Error parsing extra account: $it", e)
                    null
                }
            } ?: emptyList()
            
            _accounts.value = (accountSet + savedAccounts).toList().sortedBy { it.name }
        }
    }

    fun setAccountVisibility(accountName: String, visible: Boolean) {
        if (visible) {
            dataStore.removeStringFromSet("hidden_accounts", accountName)
        } else {
            dataStore.addStringToSet("hidden_accounts", accountName)
        }
    }

    fun createAccount(name: String, type: String = "com.vayunmathur.contacts.local") {
        viewModelScope.launch {
            val current = dataStore.getString("extra_accounts") ?: ""
            val entry = "$name|$type"
            if (!current.contains(entry)) {
                val newValue = if (current.isEmpty()) entry else "$current,$entry"
                dataStore.setString("extra_accounts", newValue)
                loadAccounts()
            }
        }
    }

    fun getContact(contactId: Long): Contact? {
        return contacts.value.find { it.id == contactId }
    }

    fun deleteContact(contact: com.vayunmathur.contacts.data.Contact) {
        viewModelScope.launch(Dispatchers.IO) {
            com.vayunmathur.contacts.data.Contact.delete(getApplication(), contact)
            if (isCalendarSyncEnabled.value) {
                CalendarSyncHelper.syncContact(getApplication(), contact.copy(details = contact.details.copy(dates = emptyList())))
            }
            contactDao.deleteContact(contact.id)
        }
    }

    fun getContactFlow(contactId: Long): Flow<Contact?> {
        return contacts.map { contacts -> contacts.find { it.id == contactId } }
    }

    fun loadContact(contactId: Long) {
        syncWithSystemContacts()
    }

    fun saveContact(contact: com.vayunmathur.contacts.data.Contact) {
        viewModelScope.launch(Dispatchers.IO) {
            val contactId = contact.id
            val details = contact.details
            val oldDetails = contacts.value.find { it.id == contactId }?.details ?: com.vayunmathur.contacts.data.ContactDetails.empty()
            contact.save(getApplication(), details, oldDetails)

            // Reload from system to ensure we have the latest (especially for new contacts)
            syncWithSystemContacts()
        }
    }
}
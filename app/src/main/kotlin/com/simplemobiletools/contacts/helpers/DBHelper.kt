package com.simplemobiletools.contacts.helpers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getBlobValue
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getLongValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.contacts.extensions.getByteArray
import com.simplemobiletools.contacts.extensions.getPhotoThumbnailSize
import com.simplemobiletools.contacts.models.*

class DBHelper private constructor(val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    private val CONTACTS_TABLE_NAME = "contacts"
    private val COL_ID = "id"
    private val COL_FIRST_NAME = "first_name"
    private val COL_MIDDLE_NAME = "middle_name"
    private val COL_SURNAME = "surname"
    private val COL_PHOTO = "photo"
    private val COL_PHONE_NUMBERS = "phone_numbers"
    private val COL_EMAILS = "emails"
    private val COL_EVENTS = "events"
    private val COL_STARRED = "starred"
    private val COL_ADDRESSES = "addresses"
    private val COL_NOTES = "notes"
    private val COL_GROUPS = "groups"

    private val GROUPS_TABLE_NAME = "groups"
    private val COL_TITLE = "title"

    private val FIRST_CONTACT_ID = 1000000

    private val mDb = writableDatabase

    companion object {
        private const val DB_VERSION = 3
        const val DB_NAME = "contacts.db"
        var dbInstance: DBHelper? = null

        fun newInstance(context: Context): DBHelper {
            if (dbInstance == null)
                dbInstance = DBHelper(context)

            return dbInstance!!
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $CONTACTS_TABLE_NAME ($COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COL_FIRST_NAME TEXT, $COL_MIDDLE_NAME TEXT, " +
                "$COL_SURNAME TEXT, $COL_PHOTO BLOB, $COL_PHONE_NUMBERS TEXT, $COL_EMAILS TEXT, $COL_EVENTS TEXT, $COL_STARRED INTEGER, " +
                "$COL_ADDRESSES TEXT, $COL_NOTES TEXT, $COL_GROUPS TEXT)")

        // start autoincrement ID from FIRST_CONTACT_ID to avoid conflicts
        db.execSQL("REPLACE INTO sqlite_sequence (name, seq) VALUES ('$CONTACTS_TABLE_NAME', $FIRST_CONTACT_ID)")

        createGroupsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1) {
            db.execSQL("ALTER TABLE $CONTACTS_TABLE_NAME ADD COLUMN $COL_ADDRESSES TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE $CONTACTS_TABLE_NAME ADD COLUMN $COL_NOTES TEXT DEFAULT ''")
        }

        if (oldVersion < 3) {
            createGroupsTable(db)
            db.execSQL("ALTER TABLE $CONTACTS_TABLE_NAME ADD COLUMN $COL_GROUPS TEXT DEFAULT ''")
        }
    }

    private fun createGroupsTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $GROUPS_TABLE_NAME ($COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COL_TITLE TEXT)")

        // start autoincrement ID from FIRST_GROUP_ID to avoid conflicts
        db.execSQL("REPLACE INTO sqlite_sequence (name, seq) VALUES ('$GROUPS_TABLE_NAME', $FIRST_GROUP_ID)")
    }

    fun insertContact(contact: Contact): Boolean {
        val contactValues = fillContactValues(contact)
        val id = mDb.insert(CONTACTS_TABLE_NAME, null, contactValues).toInt()
        return id != -1
    }

    fun updateContact(contact: Contact): Boolean {
        val contactValues = fillContactValues(contact)
        val selection = "$COL_ID = ?"
        val selectionArgs = arrayOf(contact.id.toString())
        return mDb.update(CONTACTS_TABLE_NAME, contactValues, selection, selectionArgs) == 1
    }

    fun deleteContact(id: Int) = deleteContacts(arrayOf(id.toString()))

    fun deleteContacts(ids: Array<String>) {
        val args = TextUtils.join(", ", ids)
        val selection = "$CONTACTS_TABLE_NAME.$COL_ID IN ($args)"
        mDb.delete(CONTACTS_TABLE_NAME, selection, null)
    }

    private fun fillContactValues(contact: Contact): ContentValues {
        return ContentValues().apply {
            put(COL_FIRST_NAME, contact.firstName)
            put(COL_MIDDLE_NAME, contact.middleName)
            put(COL_SURNAME, contact.surname)
            put(COL_PHONE_NUMBERS, Gson().toJson(contact.phoneNumbers))
            put(COL_EMAILS, Gson().toJson(contact.emails))
            put(COL_ADDRESSES, Gson().toJson(contact.addresses))
            put(COL_EVENTS, Gson().toJson(contact.events))
            put(COL_STARRED, contact.starred)
            put(COL_NOTES, contact.notes)
            put(COL_GROUPS, Gson().toJson(contact.groups.map { it.id }))

            if (contact.photoUri.isNotEmpty()) {
                put(COL_PHOTO, getPhotoByteArray(contact.photoUri))
            } else if (contact.photo == null) {
                putNull(COL_PHOTO)
            }
        }
    }

    private fun getPhotoByteArray(uri: String): ByteArray {
        val photoUri = Uri.parse(uri)
        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, photoUri)

        val thumbnailSize = context.getPhotoThumbnailSize()
        val scaledPhoto = Bitmap.createScaledBitmap(bitmap, thumbnailSize * 2, thumbnailSize * 2, false)
        val scaledSizePhotoData = scaledPhoto.getByteArray()
        scaledPhoto.recycle()
        return scaledSizePhotoData
    }

    fun toggleFavorites(ids: Array<String>, addToFavorites: Boolean) {
        val contactValues = ContentValues()
        contactValues.put(COL_STARRED, if (addToFavorites) 1 else 0)

        val args = TextUtils.join(", ", ids)
        val selection = "$COL_ID IN ($args)"
        mDb.update(CONTACTS_TABLE_NAME, contactValues, selection, null)
    }

    fun insertGroup(group: Group): Group? {
        val contactValues = fillGroupValues(group)
        val id = mDb.insert(GROUPS_TABLE_NAME, null, contactValues)
        return if (id == -1L) {
            null
        } else {
            Group(id, group.title)
        }
    }

    fun renameGroup(group: Group): Boolean {
        val contactValues = fillGroupValues(group)
        val selection = "$COL_ID = ?"
        val selectionArgs = arrayOf(group.id.toString())
        return mDb.update(GROUPS_TABLE_NAME, contactValues, selection, selectionArgs) == 1
    }

    fun deleteGroup(id: Long) = deleteGroups(arrayOf(id.toString()))

    fun deleteGroups(ids: Array<String>) {
        val args = TextUtils.join(", ", ids)
        val selection = "$GROUPS_TABLE_NAME.$COL_ID IN ($args)"
        mDb.delete(GROUPS_TABLE_NAME, selection, null)
    }

    fun getGroups(): ArrayList<Group> {
        val groups = ArrayList<Group>()
        val projection = arrayOf(COL_ID, COL_TITLE)
        val cursor = mDb.query(GROUPS_TABLE_NAME, projection, null, null, null, null, null)
        cursor.use {
            while (cursor.moveToNext()) {
                val id = cursor.getLongValue(COL_ID)
                val title = cursor.getStringValue(COL_TITLE)
                val group = Group(id, title)
                groups.add(group)
            }
        }
        return groups
    }

    private fun fillGroupValues(group: Group): ContentValues {
        return ContentValues().apply {
            put(COL_TITLE, group.title)
        }
    }

    fun addContactsToGroup(contacts: ArrayList<Contact>, groupId: Long) {
        contacts.forEach {
            val currentGroupIds = it.groups.map { it.id } as ArrayList<Long>
            currentGroupIds.add(groupId)
            updateContactGroups(it, currentGroupIds)
        }
    }

    fun removeContactsFromGroup(contacts: ArrayList<Contact>, groupId: Long) {
        contacts.forEach {
            val currentGroupIds = it.groups.map { it.id } as ArrayList<Long>
            currentGroupIds.remove(groupId)
            updateContactGroups(it, currentGroupIds)
        }
    }

    fun updateContactGroups(contact: Contact, groupIds: ArrayList<Long>) {
        val contactValues = fillContactGroupValues(groupIds)
        val selection = "$COL_ID = ?"
        val selectionArgs = arrayOf(contact.id.toString())
        mDb.update(CONTACTS_TABLE_NAME, contactValues, selection, selectionArgs)
    }

    private fun fillContactGroupValues(groupIds: ArrayList<Long>): ContentValues {
        return ContentValues().apply {
            put(COL_GROUPS, Gson().toJson(groupIds))
        }
    }


    fun getContacts(activity: BaseSimpleActivity, selection: String? = null, selectionArgs: Array<String>? = null): ArrayList<Contact> {
        val storedGroups = ContactsHelper(activity).getStoredGroups()
        val contacts = ArrayList<Contact>()
        val projection = arrayOf(COL_ID, COL_FIRST_NAME, COL_MIDDLE_NAME, COL_SURNAME, COL_PHONE_NUMBERS, COL_EMAILS, COL_EVENTS, COL_STARRED,
                COL_PHOTO, COL_ADDRESSES, COL_NOTES, COL_GROUPS)
        val cursor = mDb.query(CONTACTS_TABLE_NAME, projection, selection, selectionArgs, null, null, null)
        cursor.use {
            while (cursor.moveToNext()) {
                val id = cursor.getIntValue(COL_ID)
                val firstName = cursor.getStringValue(COL_FIRST_NAME)
                val middleName = cursor.getStringValue(COL_MIDDLE_NAME)
                val surname = cursor.getStringValue(COL_SURNAME)

                val phoneNumbersJson = cursor.getStringValue(COL_PHONE_NUMBERS)
                val phoneNumbersToken = object : TypeToken<List<PhoneNumber>>() {}.type
                val phoneNumbers = Gson().fromJson<ArrayList<PhoneNumber>>(phoneNumbersJson, phoneNumbersToken) ?: ArrayList(1)

                val emailsJson = cursor.getStringValue(COL_EMAILS)
                val emailsToken = object : TypeToken<List<Email>>() {}.type
                val emails = Gson().fromJson<ArrayList<Email>>(emailsJson, emailsToken) ?: ArrayList(1)

                val addressesJson = cursor.getStringValue(COL_ADDRESSES)
                val addressesToken = object : TypeToken<List<Address>>() {}.type
                val addresses = Gson().fromJson<ArrayList<Address>>(addressesJson, addressesToken) ?: ArrayList(1)

                val eventsJson = cursor.getStringValue(COL_EVENTS)
                val eventsToken = object : TypeToken<List<Event>>() {}.type
                val events = Gson().fromJson<ArrayList<Event>>(eventsJson, eventsToken) ?: ArrayList(1)

                val photoByteArray = cursor.getBlobValue(COL_PHOTO) ?: null
                val photo = if (photoByteArray?.isNotEmpty() == true) {
                    BitmapFactory.decodeByteArray(photoByteArray, 0, photoByteArray.size)
                } else {
                    null
                }

                val notes = cursor.getStringValue(COL_NOTES)
                val starred = cursor.getIntValue(COL_STARRED)

                val groupIdsJson = cursor.getStringValue(COL_GROUPS)
                val groupIdsToken = object : TypeToken<List<Long>>() {}.type
                val groupIds = Gson().fromJson<ArrayList<Long>>(groupIdsJson, groupIdsToken) ?: ArrayList(1)
                val groups = storedGroups.filter { groupIds.contains(it.id) } as ArrayList<Group>

                val contact = Contact(id, firstName, middleName, surname, "", phoneNumbers, emails, addresses, events, SMT_PRIVATE, starred, id, "", photo, notes, groups)
                contacts.add(contact)
            }
        }
        return contacts
    }

    fun getContactWithId(activity: BaseSimpleActivity, id: Int): Contact? {
        val selection = "$COL_ID = ?"
        val selectionArgs = arrayOf(id.toString())
        return getContacts(activity, selection, selectionArgs).firstOrNull()
    }
}

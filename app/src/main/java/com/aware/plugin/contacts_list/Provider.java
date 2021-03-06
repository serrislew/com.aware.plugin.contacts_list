package com.aware.plugin.contacts_list;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.Nullable;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;

/**
 * Created by denzil on 07/04/16.
 */
public class Provider extends ContentProvider {

    public static String AUTHORITY = "com.aware.plugin.contacts_list.provider.contacts"; //change to package.provider.your_plugin_name
    public static final int DATABASE_VERSION = 7; //increase this if you make changes to the database structure, i.e., rename columns, etc.

    public static final String DATABASE_NAME = "plugin_contacts.db"; //the database filename, use plugin_xxx for plugins.

    //Add here your database table names, as many as you need
    public static final String DB_TBL_CONTACTS = "plugin_contacts";

    //For each table, add two indexes: DIR and ITEM. The index needs to always increment. Next one is 3, and so on.
    private static final int CONTACTS_DIR = 1;
    private static final int CONTACTS_ITEM = 2;

    //Put tables names in this array so AWARE knows what you have on the database
    public static final String[] DATABASE_TABLES = {
            DB_TBL_CONTACTS
    };

    //These are columns that we need to sync data, don't change this!
    public interface AWAREColumns extends BaseColumns {
        String _ID = "_id";
        String TIMESTAMP = "timestamp";
        String DEVICE_ID = "device_id";
    }

    /**
     * Create one of these per database table
     * In this example, we are adding example columns
     */
    public static final class Contacts_Data implements AWAREColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + DB_TBL_CONTACTS);
        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.aware.plugin.contacts_list.provider.contacts"; //modify me
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.com.aware.plugin.contacts_list.provider.contacts"; //modify me

        public static String NAME = "name";
        public static String PHONE_NUMBERS = "phone_numbers";
        public static String EMAILS = "emails";
        public static String GROUPS = "groups";
        public static String SYNC_DATE = "sync_date";
    }

    //Define each database table fields
    private static final String DB_TBL_FIELDS =
            Contacts_Data._ID + " integer primary key autoincrement," +
            Contacts_Data.TIMESTAMP + " real default 0," +
            Contacts_Data.DEVICE_ID + " text default ''," +
            Contacts_Data.NAME + " text default ''," +
            Contacts_Data.PHONE_NUMBERS + " text default ''," +
            Contacts_Data.EMAILS + " text default ''," +
            Contacts_Data.GROUPS + " text default ''," +
            Contacts_Data.SYNC_DATE + " real default 0";

    /**
     * Share the fields with AWARE so we can replicate the table schema on the server
     */
    public static final String[] TABLES_FIELDS = {
            DB_TBL_FIELDS
    };

    //Helper variables for ContentProvider - don't change me
    private static UriMatcher sUriMatcher;
    private DatabaseHelper databaseHelper;
    private static SQLiteDatabase database;

    //For each table, create a hashmap needed for database queries
    private static HashMap<String, String> contactsHash;

    /**
     * Initialise database: create the database file, update if needed, etc. DO NOT CHANGE ME
     *
     * @return
     */
    private boolean initializeDB() {
        if (databaseHelper == null) {
            databaseHelper = new DatabaseHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS);
        }
        if (database == null || !database.isOpen()) {
            database = databaseHelper.getWritableDatabase();
        }
        return (database != null && databaseHelper != null);
    }

    @Override
    public boolean onCreate() {
        //This is a hack to allow providers to be reusable in any application/plugin by making the authority dynamic using the package name of the parent app
        AUTHORITY = getContext().getPackageName() + ".provider.contacts"; //make sure xxx matches the first string in this class

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        //For each table, add indexes DIR and ITEM
        sUriMatcher.addURI(AUTHORITY, DATABASE_TABLES[0], CONTACTS_DIR);
        sUriMatcher.addURI(AUTHORITY, DATABASE_TABLES[0] + "/#", CONTACTS_ITEM);

        //Create each table hashmap so Android knows how to insert data to the database. Put ALL table fields.
        contactsHash = new HashMap<>();
        contactsHash.put(Contacts_Data._ID, Contacts_Data._ID);
        contactsHash.put(Contacts_Data.TIMESTAMP, Contacts_Data.TIMESTAMP);
        contactsHash.put(Contacts_Data.DEVICE_ID, Contacts_Data.DEVICE_ID);
        contactsHash.put(Contacts_Data.NAME, Contacts_Data.NAME);
        contactsHash.put(Contacts_Data.PHONE_NUMBERS, Contacts_Data.PHONE_NUMBERS);
        contactsHash.put(Contacts_Data.EMAILS, Contacts_Data.EMAILS);
        contactsHash.put(Contacts_Data.GROUPS, Contacts_Data.GROUPS);
        contactsHash.put(Contacts_Data.SYNC_DATE, Contacts_Data.SYNC_DATE);

        return true;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!initializeDB()) {
            Log.w("", "Database unavailable...");
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (sUriMatcher.match(uri)) {

            //Add all tables' DIR entries, with the right table index
            case CONTACTS_DIR:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(contactsHash); //the hashmap of the table
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        //Don't change me
        try {
            Cursor c = qb.query(database, projection, selection, selectionArgs,
                    null, null, sortOrder);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        } catch (IllegalStateException e) {
            if (Aware.DEBUG)
                Log.e(Aware.TAG, e.getMessage());
            return null;
        }
    }

    @Nullable
    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {

            //Add each table indexes DIR and ITEM
            case CONTACTS_DIR:
                return Contacts_Data.CONTENT_TYPE;
            case CONTACTS_ITEM:
                return Contacts_Data.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues new_values) {
        if (!initializeDB()) {
            Log.w("", "Database unavailable to insert...");
            return null;
        }

        ContentValues values = (new_values != null) ? new ContentValues(new_values) : new ContentValues();
        long _id;

        switch (sUriMatcher.match(uri)) {

            //Add each table DIR case
            case CONTACTS_DIR:
                _id = database.insert(DATABASE_TABLES[0], Contacts_Data.DEVICE_ID, values);
                if (_id > 0) {
                    Uri dataUri = ContentUris.withAppendedId(Contacts_Data.CONTENT_URI, _id);
                    getContext().getContentResolver().notifyChange(dataUri, null);
                    return dataUri;
                }
                throw new SQLException("Failed to insert row into " + uri);

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (!initializeDB()) {
            Log.w("", "Database unavailable to delete...");
            return 0;
        }

        int count;
        switch (sUriMatcher.match(uri)) {

            //Add each table DIR case
            case CONTACTS_DIR:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (!initializeDB()) {
            Log.w("", "Database unavailable to update...");
            return 0;
        }

        int count;
        switch (sUriMatcher.match(uri)) {

            //Add each table DIR case
            case CONTACTS_DIR:
                count = database.update(DATABASE_TABLES[0], values, selection, selectionArgs);
                break;

            default:
                database.close();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}

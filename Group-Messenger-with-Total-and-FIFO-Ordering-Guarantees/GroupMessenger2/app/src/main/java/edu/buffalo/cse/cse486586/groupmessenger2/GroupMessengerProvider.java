package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.MatrixCursor;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.content.Context;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 *
 * Please read:
 *
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 *
 * before you start to get yourself familiarized with ContentProvider.
 *
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 *
 * @author stevko
 *
 */

// References: https://stackoverflow.com/questions/14376807/how-to-read-write-string-from-a-file-in-android

public class GroupMessengerProvider extends ContentProvider {

    static final String TAG = GroupMessengerProvider.class.getSimpleName();
    /**
     * Database specific constant declarations
     */


    /**
     * Helper class that actually creates and manages
     * the provider's underlying data repository.
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        String filename = values.get("key").toString();
        String string = values.get("value").toString();
        FileOutputStream outputStream;

        try {
            outputStream = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(string.getBytes());
            outputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "File write failed");
        }
        return null;
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        String str = "";
        Log.d(TAG,selection);
        try {
            File ipFile = new File(getContext().getFilesDir(),selection);
            BufferedReader br = new BufferedReader(new FileReader(ipFile));
            String st = "";
            while ((st = br.readLine()) != null) {
                str += st;
            }
            Log.d(TAG,str);
        }
        catch(Exception e){
            Log.e(TAG,"");
        }
        String[] columns = new String[] { "key", "value" };
        MatrixCursor matrixCursor= new MatrixCursor(columns);
        matrixCursor.addRow(new Object[]{selection, str});
        return matrixCursor;
    }
}

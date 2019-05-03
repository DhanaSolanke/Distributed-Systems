package edu.buffalo.cse.cse486586.simpledht;

/**
 * References:
 *
 */
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {

    static final String TAG =  SimpleDhtProvider.class.getSimpleName();
    private static final String KEY_VALUE_TABLE = "LocalDataTable";
    private static final String KEY = "key";
    private static final String VALUE = "value";
    String thisAVDPort;
    String thisAVDHashed;
    final String SERVER_PORT = "10000";
    String HEAD = "5554";
    TreeMap<String, String> chords_present = new TreeMap<String, String>();
    private SQLiteDatabase sqldb;

    private static class createDB extends SQLiteOpenHelper {
        //Class to initialize the database
        createDB(Context context){
            super(context, "PA3_Chord", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase sqldb) {
            sqldb.execSQL("CREATE TABLE " + KEY_VALUE_TABLE +
                    "( "+ KEY +" TEXT PRIMARY KEY NOT NULL, " + VALUE +" TEXT NOT NULL);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqldb, int oldVersion, int newVersion) {
            sqldb.execSQL("DROP TABLE IF EXISTS " + KEY_VALUE_TABLE);
            onCreate(sqldb);
        }
    }
    protected Object sendRequestToServer(SingleChordStructure msg){
        //Create socket to send Objects
        Object acknowledgement = null;
        try{
            Log.v(TAG,"Initializing socket connection ");
            int port = Integer.parseInt(msg.destinationAVDPort) *2;
            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),port);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            out.writeObject(msg);
            Log.v(TAG,"Sent request to server");
            out.flush();
            acknowledgement = in.readObject();
            Log.v(TAG, "Acknowledgement received " + acknowledgement);
            out.close();
            in.close();
            socket.close();
        }catch (Exception e){
            Log.e(TAG,"Exception occurred in sending server request");
        }
        return acknowledgement;
    }
    @Override
    public boolean onCreate() {

        try{
            Log.v(TAG,"Initialing Database");
            createDB database = new createDB(getContext());
            sqldb = database.getWritableDatabase();
            TelephonyManager tel = (TelephonyManager) this.getContext().getSystemService(Context.TELEPHONY_SERVICE);
            thisAVDPort = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        }catch(NullPointerException e){
            Log.v(TAG, "Error Getting current AVD Port Number" );
        }
        try {
            thisAVDHashed = genHash(thisAVDPort);
            Log.v(TAG, "Hash Generated for current AVD");
        }catch (NoSuchAlgorithmException e){
            Log.i(TAG,"No such Algo");
        }
        try {
            ServerSocket serverSocket = new ServerSocket(Integer.parseInt(SERVER_PORT));
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.v(TAG, "Unable create a ServerSocket on 10000 ");
        }
        chords_present.put(thisAVDHashed,thisAVDPort);
        if(!thisAVDPort.equals(HEAD)){
            //Create a JOIN Request to destination port which is HEAD always
            SingleChordStructure request = new SingleChordStructure();
            request.currentAVDPort = thisAVDPort;
            request.destinationAVDPort = HEAD;
            request.data = new TreeMap<String, String>();
            request.data.put(thisAVDHashed,thisAVDPort);
            request.requestType = "Join";
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, request);
        }
        if (sqldb == null){
            return false;
        }
        else{
            return true;
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.v(TAG,"Received requested to delete " + selection );
        SingleChordStructure formDeletemessage = new SingleChordStructure();
        formDeletemessage.currentAVDPort = thisAVDPort;
        formDeletemessage.data = new TreeMap<String, String>();
        formDeletemessage.data.put("Delete", selection);
        try{
            if(selection.equalsIgnoreCase("*")){
                //Delete all
                formDeletemessage.requestType = "AllDataDelete";
                formDeletemessage.destinationAVDPort = "";
                return (Integer) new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,formDeletemessage).get();
            }else if(selection.equalsIgnoreCase("@")){
                //Delete local all keys
                return sqldb.delete(KEY_VALUE_TABLE, null, null);
            }else{
                //Selection involves a single key
                String hashedval = genHash(selection);
                String max_node = chords_present.lastKey();
                String findkeytobedeleted = "";
                if(hashedval.compareTo(max_node) > 0){
                    //If key is greater than last node insert it in First Node
                    findkeytobedeleted = chords_present.firstKey();
                }else{
                    //Find key  just greater than our selection
                    findkeytobedeleted= chords_present.ceilingKey(hashedval);
                }

                if(findkeytobedeleted.equalsIgnoreCase(thisAVDHashed)){
                    return sqldb.delete(KEY_VALUE_TABLE, KEY + "=?", new String[]{selection});
                }else{
                    formDeletemessage.destinationAVDPort = chords_present.get(findkeytobedeleted);
                    formDeletemessage.requestType = "DeleteThisNode";
                    return (Integer) new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,formDeletemessage).get();
                }
            }
        }catch (Exception e){
            Log.v(TAG,"Deletion exception occurred ");
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.v(TAG,"Received Request to Insert");
        long rowID = -1;
        try{
            String key = values.getAsString(KEY);
            String hashedval = genHash(key);
            String max_node = chords_present.lastKey();
            String successor = "";
            if(hashedval.compareTo(max_node) > 0){
                successor = chords_present.firstKey();
            }else{
                //Find less key than that
                successor= chords_present.ceilingKey(hashedval);
            }
            if(successor.equalsIgnoreCase(thisAVDHashed)){
                rowID = sqldb.replace(KEY_VALUE_TABLE, "", values);
            }
            else{
                //Insert single key
                String value = values.getAsString(VALUE);
                SingleChordStructure insertMessage = new SingleChordStructure();
                insertMessage.currentAVDPort = thisAVDPort;
                insertMessage.destinationAVDPort = chords_present.get(successor);
                insertMessage.requestType = "Insert";
                insertMessage.data = new TreeMap<String, String>();
                insertMessage.data.put(key, value);
                Log.v(TAG, "Inserting "+ key);
                rowID = (Long) new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,insertMessage).get(2000, TimeUnit.MILLISECONDS);

            }
            uri = ContentUris.withAppendedId(Uri.parse("edu.buffalo.cse.cse486586.simpledht.SimpleDhtProvider"), rowID);
            Log.v(TAG,"Insert successful "+ key);
            return uri;
        } catch (Exception e) {
            Log.v(TAG, "Couldn't complete insertion ");
        }
        return null;
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Log.v(TAG,"Received request to query");
        SingleChordStructure sendQueryRequestObject = new SingleChordStructure();
        sendQueryRequestObject.currentAVDPort = thisAVDPort;
        sendQueryRequestObject.data = new TreeMap<String, String>();
        try{

            if(selection.equalsIgnoreCase("*")){
                //Return all key value pairs
                sendQueryRequestObject.destinationAVDPort = "";
                sendQueryRequestObject.currentAVDPort = thisAVDPort;
                sendQueryRequestObject.requestType = "QueryAllAVDS";
                sendQueryRequestObject.data.put("QueryAllAVDS", selection);
                return (Cursor) new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,sendQueryRequestObject).get();
            }else if(selection.equalsIgnoreCase("@")){
                // Select Local
                return sqldb.query(KEY_VALUE_TABLE, null, null, null, null, null, null);
            }else{
                //Return single key value pair
                String hashedval = genHash(selection);
                String max_node = chords_present.lastKey();
                String succ = "";
                if(hashedval.compareTo(max_node) > 0){
                    //If key is greater than last node insert it in First Node
                    succ = chords_present.firstKey();
                }else{
                    //Find key  just greater than our selection
                    succ= chords_present.ceilingKey(hashedval);
                }
                if(succ.equalsIgnoreCase(thisAVDHashed)){
                    return sqldb.query(KEY_VALUE_TABLE, null, KEY + "=?", new String[]{selection}, null, null, null);
                }else{
                    sendQueryRequestObject.currentAVDPort = thisAVDPort;
                    sendQueryRequestObject.data.put("Query", selection);
                    sendQueryRequestObject.destinationAVDPort = chords_present.get(succ);
                    sendQueryRequestObject.requestType = "Query";
                    return (Cursor) new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,sendQueryRequestObject).get();
                }
            }
        }catch (Exception e){
            Log.e(TAG,"Exception in querying");
        }
        Log.v(TAG,"Returned null");
        return null;
    }


    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private class ServerTask extends AsyncTask<ServerSocket, SingleChordStructure, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
             while(true){
                try {
                    Socket socket =  serverSocket.accept();
                    ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream inputStream  = new ObjectInputStream(socket.getInputStream());
                    SingleChordStructure requested = (SingleChordStructure) inputStream.readObject();
                    Log.v(TAG, "Received Request to " + requested.requestType);

                    if (requested.requestType.equals("Join")) {
                        chords_present.put(requested.data.firstKey(), requested.data.firstEntry().getValue());
                        SingleChordStructure returnMsg = new SingleChordStructure();
                        returnMsg.requestType = "UpdateAllAVD'sWithJoin";
                        returnMsg.currentAVDPort = thisAVDPort;
                        returnMsg.data = chords_present;
                        outputStream.flush();
                        publishProgress(returnMsg);
                    }
                    else if (requested.requestType.equals("UpdateAllAVD'sWithJoin")) {

                        chords_present = requested.data;
                        outputStream.flush();
                        Log.v(TAG, "Updated Chords: " + chords_present);
                    }
                    else if (requested.requestType.equals("Insert")) {

                        ContentValues values = new ContentValues();
                        values.put(KEY, requested.data.firstEntry().getKey());
                        values.put(VALUE, requested.data.firstEntry().getValue());
                        Long rowID = sqldb.replace(KEY_VALUE_TABLE, "", values);
                        outputStream.writeObject(rowID);
                        outputStream.flush();
                        Log.v(TAG,"Inserted into " + thisAVDPort);

                    }
                    else if (requested.requestType.equals("DeleteThisNode")) {

                        Integer count = sqldb.delete(KEY_VALUE_TABLE, KEY + "=?", new String[]{requested.data.firstEntry().getValue()});
                        outputStream.writeObject(count);
                        outputStream.flush();

                    }
                    else if (requested.requestType.equals("Query")) {

                        String key = requested.data.firstEntry().getValue();
                        Cursor cursor = sqldb.query(KEY_VALUE_TABLE, null, KEY + "=?", new String[]{key}, null, null, null);
                        requested.requestType = "Resolved";
                        TreeMap<String, String> response = new TreeMap<String, String>();
                        int kIndex = cursor.getColumnIndex(KEY);
                        int vIndex = cursor.getColumnIndex(VALUE);
                        cursor.moveToFirst();
                        for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                            response.put(cursor.getString(kIndex),cursor.getString(vIndex));
                        }
                        requested.data = response;
                        outputStream.writeObject(requested);
                        outputStream.flush();

                    }
                    else if (requested.requestType.equals("QueryAllAVDS")) {

                        Cursor cursor = sqldb.query(KEY_VALUE_TABLE, null, null, null, null, null, null);
                        requested.requestType = "Resolved";
                        TreeMap<String, String> response = new TreeMap<String, String>();
                        int kIndex = cursor.getColumnIndex(KEY);
                        int vIndex = cursor.getColumnIndex(VALUE);
                        cursor.moveToFirst();
                        for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                            response.put(cursor.getString(kIndex),cursor.getString(vIndex));
                        }
                        requested.data = response;
                        outputStream.writeObject(requested);
                        outputStream.flush();

                    }
                    else if (requested.requestType.equals("AllDataDelete")) {

                        Integer Done = sqldb.delete(KEY_VALUE_TABLE, null, null);
                        outputStream.writeObject(Done);
                        outputStream.flush();

                    }
                    else if (requested.requestType.equals("Resolved")){
                        Log.v(TAG,"Request resolved");
                        return null;
                    }
                    else{
                        return null;
                    }
                    inputStream.close();
                    outputStream.close();
                    socket.close();
                } catch (Exception e) {
                    Log.i(TAG, "Exception in Server Task ");
                }
            }
        }

        protected void onProgressUpdate(SingleChordStructure...msgs) {
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msgs[0]);
        }
    }

    private class ClientTask extends AsyncTask<SingleChordStructure, Void, Object> {

        @Override
        protected Object doInBackground(SingleChordStructure... msgs) {
            SingleChordStructure sendToServer = msgs[0];
            Log.v(TAG, " In Client Task");
            if(sendToServer.requestType.equals("Join") || sendToServer.requestType.equals("Insert") || sendToServer.requestType.equals("DeleteThisNode")) {
                Object acknowledgement = null;
                try{
                    int port = Integer.parseInt(sendToServer.destinationAVDPort) *2;
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),port);
                    ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
                    outStream.writeObject(sendToServer);
                    outStream.flush();
                    Log.v(TAG,"Sending to server" + sendToServer.requestType);
                    socket.getReceiveBufferSize();
                    acknowledgement = inStream.readObject();
                    Log.v(TAG, "Received Acknowledgement: " + acknowledgement);
                    inStream.close();
                    outStream.close();
                    socket.close();
                }catch (Exception e){
                    Log.e(TAG,"Exception occurred in sending request to server ");
                }
                return acknowledgement;
            }
            else if(sendToServer.requestType.equals("Query")) {
                Object acknowledgement = null;
                try{
                    Log.v(TAG,"Initializing socket connection ");
                    int port = Integer.parseInt(sendToServer.destinationAVDPort) *2;
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),port);
                    ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
                    outputStream.writeObject(sendToServer);
                    Log.v(TAG,"Sent request to server");
                    outputStream.flush();
                    acknowledgement = inStream.readObject();
                    Log.v(TAG, "Acknowledgement received " + acknowledgement);
                    outputStream.close();
                    inStream.close();
                    socket.close();
                }catch (Exception e){
                    Log.e(TAG,"Exception occurred in querying" );
                }
                SingleChordStructure received = (SingleChordStructure) acknowledgement;
                MatrixCursor cursor = new MatrixCursor(new String[]{KEY,VALUE});
                for(Map.Entry<String, String> entry : received.data.entrySet()){
                    cursor.addRow(new Object[]{entry.getKey(),entry.getValue()});
                }
                return cursor;

            }
            else if (sendToServer.requestType.equals("QueryAllAVDS")) {
                TreeMap<String, String> values = new TreeMap<String, String>();
                for (String port : chords_present.values()) {
                    sendToServer.destinationAVDPort = port;
                    if (port.equalsIgnoreCase(thisAVDPort)) {
                        Cursor cursor = sqldb.query(KEY_VALUE_TABLE, null, null, null, null, null, null);
                        TreeMap<String, String> response = new TreeMap<String, String>();
                        int kIndex = cursor.getColumnIndex(KEY);
                        int vIndex = cursor.getColumnIndex(VALUE);
                        cursor.moveToFirst();
                        for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                            response.put(cursor.getString(kIndex),cursor.getString(vIndex));
                        }
                        values.putAll(response);
                    } else {
                        SingleChordStructure received = (SingleChordStructure) sendRequestToServer(sendToServer);
                        values.putAll(received.data);
                    }
                }
                MatrixCursor cursor = new MatrixCursor(new String[]{KEY,VALUE});
                for(Map.Entry<String, String> entry : values.entrySet()){
                    cursor.addRow(new Object[]{entry.getKey(),entry.getValue()});
                }
                return cursor;
            }
            else if (sendToServer.requestType.equals("AllDataDelete")) {
                int deleted = 0;
                //Send Delete Request to all AVD's present
                for (String each : chords_present.values()) {
                    sendToServer.destinationAVDPort = each;
                    if (each.equalsIgnoreCase(thisAVDPort)) {
                        deleted += sqldb.delete(KEY_VALUE_TABLE, null, null);
                    } else {

                        Object acknowledgement = null;
                        try{
                            int port = Integer.parseInt(sendToServer.destinationAVDPort) *2;
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),port);
                            ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
                            ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
                            outStream.writeObject(sendToServer);
                            outStream.flush();
                            Log.v(TAG,"Sending to server" + sendToServer);
                            socket.getReceiveBufferSize();
                            acknowledgement = inStream.readObject();
                            Log.v(TAG, "Received Acknowledgement: " + acknowledgement);
                            inStream.close();
                            outStream.close();
                            socket.close();
                        }catch (Exception e){
                            Log.e(TAG,"Exception occurred in sending request to server ");
                        }
                        deleted += (Integer) acknowledgement;
                    }
                }
                return deleted;
            }
            else if (sendToServer.requestType.equals("UpdateAllAVD'sWithJoin")) {
                for (String port : chords_present.values()) {
                    sendToServer.destinationAVDPort = port;
                    sendRequestToServer(sendToServer);
                }
            }
            return null;
        }
    }
    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

}

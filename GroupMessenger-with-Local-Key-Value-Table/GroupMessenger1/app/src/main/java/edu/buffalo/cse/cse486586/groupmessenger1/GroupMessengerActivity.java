package edu.buffalo.cse.cse486586.groupmessenger1;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final int SERVER_PORT = 10000;
    static final String TAG = GroupMessengerProvider.class.getSimpleName();
    int counter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        counter = 0;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */

        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        try {
            Log.v(TAG,"server calling");
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Button button = (Button) findViewById(R.id.button4);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                EditText editText = (EditText) findViewById(R.id.editText1);
                final String content = editText.getText().toString();
                editText.setText("");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, content);
            }


        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            Log.v(TAG,"Inside server task");
            try {
                Log.v(TAG,"Inside server");
                while(true){
                    Log.v(TAG,"Server socket creation");
                    Socket server = serverSocket.accept();
                    ObjectInputStream ip = new ObjectInputStream(server.getInputStream());

                    String msg = (String) ip.readObject();
                    Uri.Builder uriBuilder = new Uri.Builder();
                    uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger1.provider");
                    uriBuilder.scheme("content");
                    Uri uri = uriBuilder.build();

                    final ContentValues mContentValues = new ContentValues();
                    mContentValues.put("key", counter);
                    mContentValues.put("value", msg);
                    getContentResolver().insert(uri, mContentValues);
                    counter = counter + 1;
                    msg = "";
                    publishProgress(msg);

                }



            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        protected void onProgressUpdate(String...strings){
            String received = strings[0].trim();
            TextView inner = (TextView) findViewById(R.id.textView1);
            inner.append(received+"\t\n");
            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
                try {
                    Log.v(TAG,"Inside ClientTask ");
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),11108);
                    Socket socket1 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),11112);
                    Socket socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),11116);
                    Socket socket3 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),11120);
                    Socket socket4 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),11124);

                    Log.v(TAG,"Step 1 ");
                    String msgToSend = msgs[0];
                    ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                    oos.writeObject(msgToSend);

                    Log.v(TAG,"Step 2 ");
                    ObjectOutputStream oos1 = new ObjectOutputStream(socket1.getOutputStream());
                    oos1.writeObject(msgToSend);

                    Log.v(TAG,"Step 3 ");
                    ObjectOutputStream out2 = new ObjectOutputStream(socket2.getOutputStream());
                    out2.writeObject(msgToSend);

                    ObjectOutputStream out3 = new ObjectOutputStream(socket3.getOutputStream());
                    out3.writeObject(msgToSend);

                    ObjectOutputStream out4 = new ObjectOutputStream(socket4.getOutputStream());
                    out4.writeObject(msgToSend);

                    Log.v("Message sent ", msgToSend);

                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG,"Client task exception");
                   e.printStackTrace();
                }

            return null;
        }
    }
}

package edu.buffalo.cse.cse486586.groupmessenger2;

import java.net.ServerSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;


import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.widget.TextView;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.net.Uri.Builder ;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    String[] PORTS = {"11108","11112","11116","11120","11124"};
    ArrayList available_ports = new ArrayList<String>();
    ArrayList received_sequences = new ArrayList();
    int current_seq_number = -1 ;
    int final_seq = -1 ;

    ArrayList proposed_sequence = new ArrayList();
    ArrayList created_sockets = new ArrayList();
    ContentValues values = new ContentValues();
    private  Uri mUri ;
    ContentResolver contentResolver;
    static final int SERVER_PORT = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        //Initialize Content Resolver Object like the onPTestClickListener
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
        uriBuilder.scheme("content");
        mUri =  uriBuilder.build();
        contentResolver = getContentResolver();

        try {

            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {

            Log.e(TAG, "Run setdir command");
            return;
        }
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        final EditText editText = (EditText) findViewById(R.id.editText1);
        findViewById(R.id.button4).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                //On send button click do the following
                String msg = editText.getText().toString();
                editText.setText("");
                TextView view = (TextView) findViewById(R.id.textView1);
                view.append(msg+"\n");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
                return;
            }

        });


    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            while (true) {
                Log.i(TAG, "Server Running!");
            try {
                String currentmsg;
                while (true) {
                    //Read text message
                    Log.i(TAG, "Still running");
                    Socket socket = serverSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter server_reply = new PrintWriter(socket.getOutputStream(), true);
                    currentmsg = in.readLine();
                    Log.i(TAG, "Received String " + currentmsg);
                    //Send current + 1 sequence number as proposed
                    if (currentmsg != null) {
                        Log.i(TAG, "Running");
                        server_reply.println(current_seq_number+1);
                        server_reply.flush();
                        publishProgress(currentmsg);
                    }
                    //Read sequence number back and store
                    String seq_num;
                    while ((seq_num = in.readLine()) != null) {
                        try {
                            final_seq = Integer.parseInt(seq_num);
                            //Update your current number according to final sequence decided
                            if (current_seq_number <= final_seq) {
                                current_seq_number = final_seq;
                            }
                            values.put("key", seq_num);
                            values.put("value", currentmsg);
                            Log.i("Storing", seq_num + " " +currentmsg);
                            contentResolver.insert(mUri, values);
                            socket.close();
                            break;
                        } catch (Exception e) {
                            e.printStackTrace();
                            continue;
                        }

                    }

                }

            } catch (IOException e) {
                Log.e(TAG, "Not receiving");
            }
        }
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String msg = strings[0].trim();
            TextView view = (TextView) findViewById(R.id.textView1);
            view.append(msg + "\n");
            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                //for all ports broadcast the message
                for(int i=0; i< PORTS.length ; i++ ) {
                    String msg = msgs[0];
                    String str;
                    Log.i(TAG,"Creating socket for "+PORTS[i]);
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(PORTS[i]));

                    PrintWriter broadcast_stream = new PrintWriter(socket.getOutputStream(), true);
                    created_sockets.add(socket);
                    Log.i(TAG,"Created socket for "+PORTS[i]);
                    BufferedReader broadcast_replies = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    /*
                     * TODO: Fill in your client code that sends out a message.
                     */
                    try {
                        //Send message
                        broadcast_stream.println(msg);
                        Log.i("Broadcasted to ",PORTS[i]+ " "+ msg);
                        broadcast_stream.flush();

                        try{
                            //Reading reply
                            //Log.i(TAG,"In here");
                            str = broadcast_replies.readLine();
                           // Log.i(TAG,"Read reply");
                            int num = Integer.parseInt(str);
                            proposed_sequence.add(num);
                            received_sequences.add(PORTS[i]);
                            Log.i("proposed num by :",PORTS[i] + " "+ Integer.toString(num));

                        }catch(Exception e){
                            //implement logic for AVD failure
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

                available_ports.add("11108");
                available_ports.add("11112");
                available_ports.add("11116");
                available_ports.add("11120");
                available_ports.add("11124");

                Log.i(TAG,"Reaching here");
                available_ports.removeAll(received_sequences);
                //If an AVD Fails
                if(available_ports.size() > 0) {
                    String port = (String)available_ports.get(0);
                    int index = -1;
                    //Remove Socket for failed AVD
                    for(int j=0; j <PORTS.length; j++){
                        if(port.equals(PORTS[j])){
                            index = j;
                            break;
                        }
                    }
                    created_sockets.remove(index);
                    String[] updatedPorts = new String[4];
                    for(int j=0; j < 4; j++){
                        if(index != j){
                            updatedPorts[j] = PORTS[j];
                        }
                    }
                    PORTS = updatedPorts;
                }
                //Broadcast to all others
                Log.i(TAG,"Checking failure: "+available_ports.size());

                    int finalSeq = -1;
                    try {

                        finalSeq = (Integer) Collections.max(proposed_sequence);
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Empty");
                    }
                    //For all the created sockets, broadcast the agreed sequence again
                    for (int i = 0; i < created_sockets.size(); i++) {

                        Log.i("Agreed sequences", "to" + PORTS[i]);
                        Socket sock = (Socket) created_sockets.get(i);
                        PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
                        out.println(Integer.toString(finalSeq));
                        out.flush();
                    }
                    proposed_sequence.clear();
                    created_sockets.clear();
                    available_ports.clear();


            } catch (UnknownHostException e) {
                Log.e(TAG, "UnknownHostException");
            } catch (IOException e) {
                e.printStackTrace();
            }
            catch (Exception e){
                e.printStackTrace();
            }

            return null;
        }

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Pattern;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDynamoProvider extends ContentProvider {

	String TAG = SimpleDynamoProvider.class.getSimpleName();
	private final Uri myUri  = buildUri("content", "edu.buffalo.cse.cse486586.simpledynamo.provider");
	ArrayList<NodeStructure> Structure = new ArrayList<NodeStructure>();
	static final Integer SERVER_PORT = 10000;
	static  String currAVD ="";
	String pred = "";
	String succ = "";
	boolean RecoveryFailureStatus;
	BlockingQueue<String> Queue = new ArrayBlockingQueue<String>(1);

	public void initializeStructure() {
		//Create a list of all nodes and create a chord with all pointers
		try {

			NodeStructure one = new NodeStructure("5554", null, null);
			NodeStructure two = new NodeStructure("5558", null, null);
			NodeStructure three = new NodeStructure("5560", null, null);
			NodeStructure four = new NodeStructure("5562", null, null);
			NodeStructure five = new NodeStructure("5556", null, null);

			one.setPredeccesor(five);
			one.setSuccessor(two);

			two.setPredeccesor(one);
			two.setSuccessor(three);

			three.setPredeccesor(two);
			three.setSuccessor(four);

			four.setPredeccesor(three);
			four.setSuccessor(five);

			five.setPredeccesor(four);
			five.setSuccessor(one);

			Structure.add(one);
			Structure.add(two);
			Structure.add(three);
			Structure.add(four);
			Structure.add(five);

			for (NodeStructure node : Structure) {

				if (node.getPort().equals(currAVD)) {
					pred = node.getPredeccesor().getPort();
					succ = node.getSuccessor().getPort();
					Log.v(TAG, currAVD + " " + pred + " " + succ);
				}
			}

			Log.d(TAG,"Node Structure created Successfully");
		}
		catch (Exception e){
			Log.d(TAG,"Exception in Node Structure Creation");
		}
	}

	private Uri buildUri(String scheme, String authority) {
		//Reference PA
		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.authority(authority);
		uriBuilder.scheme(scheme);
		return uriBuilder.build();
	}
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		//All files delete for this AVD
		if(selection.contains("@") || selection.contains("*")){
			Log.d(TAG,"Delete All Files for: "+currAVD);
			File[] files  = getContext().getFilesDir().listFiles();
			for(File file : files){
				file.delete();
			}
			Log.d(TAG,"Delete @/* for "+ currAVD +" successful");
		}
		//Delete a particular key
		else{
			try {
				if(selectionArgs == null) {
					//Call a delete for entire AVD
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "Delete@"+selection);
				}
				String key = selection.split("-")[0];
				Log.d(TAG,"Delete : " + key + " in AVD: "+ currAVD);
				File directory =  getContext().getFilesDir();
				File file = new File(directory,key);
				if(file.exists()){
					file.delete();
				}
				Log.d(TAG,"Selection: "+ key +" Successfully deleted from AVD: " + currAVD);
			} catch (Exception e) {
				Log.v(TAG,"Exception in deleting in AVD: "+ currAVD +" for key: ");
			}
		}
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	public String[] getNextNode(String inputKey) throws NoSuchAlgorithmException {
		//Return the next node for given key
		String key = genHash(inputKey);
		String[] EntireStrructure = {"","",""};
		for(NodeStructure node : Structure){

			String predecessor = genHash(node.getPredeccesor().getPort());
			String current = genHash(node.getPort());
			Log.v(TAG,"Structure"+ " " + key + " "+ current + " "+ predecessor);
			if((key.compareTo(predecessor) > 0 && key.compareTo(current) < 0)||
					((predecessor).compareTo(current)>0 &&
							(key.compareTo(predecessor)>0 || key.compareTo(current)<0))){

				Log.v(TAG,"In Get next node");
				EntireStrructure[0] = node.getPort();
				EntireStrructure[1] = node.getSuccessor().getPort();
				EntireStrructure[2] = node.getSuccessor().getSuccessor().getPort();
				return EntireStrructure;
			}
		}
		return EntireStrructure;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub
		String key = values.getAsString("key") ;
		String value = values.getAsString("value");
		Context context = getContext();
		File directory =  context.getFilesDir();
		try {
			if(value.contains(":")){
				File file = new File(directory,key);
				try {
					if(file.exists()){
						BufferedReader reader = new BufferedReader(new InputStreamReader(context.openFileInput(key)));
						String existingValue = reader.readLine();
						String initialTime = existingValue.split(":")[1];
						String newValue = value.split(":")[1];
						//Compare values with timestamps
						if(initialTime.compareTo(newValue) > 0){
							value = existingValue;
						}
					}
				} catch (FileNotFoundException e) {
					Log.d(TAG,"File Not Found"+ e);
				} catch (IOException e) {
					Log.d(TAG,"IO Exception " + e);
				}

				OutputStreamWriter out = new OutputStreamWriter(context.openFileOutput(key, Context.MODE_PRIVATE));
				out.write(value);
				out.close();

				Log.i(TAG,"Inserted key:"+file+ " record:" +value+ " at AVD "+currAVD );
			}
			else if(!value.contains(":")){
				value = value +":"+ (long)System.currentTimeMillis();
				try {
					File file = new File(directory,key);
					if(file.exists()){
						BufferedReader reader = new BufferedReader(new InputStreamReader(context.openFileInput(key)));
						String existingValue = reader.readLine();
						String initialTime = existingValue.split(":")[1];
						String newValue = value.split(":")[1];
						if(initialTime.compareTo(newValue) > 0){
							value = existingValue;
						}
					}
				} catch (FileNotFoundException e) {
					Log.d(TAG,"File not found" + e);
				} catch (IOException e) {
					Log.d(TAG,"IO Error"+ e);
				}

				String[] nodes = getNextNode(key);
				if ( nodes[0].equals(currAVD) || nodes[1].equals(currAVD) || nodes[2].equals(currAVD)) {
					OutputStreamWriter out = new OutputStreamWriter(context.openFileOutput(key, Context.MODE_PRIVATE));
					out.write(value);
					out.close();
				}
				Log.i(TAG,"Inserted key:"+ key + " record:" +value+ " at AVD "+currAVD );
				String msgTobeSent = "Insert@" + key + "-" + value;
				// Insert Replica
				for(String each :nodes){
					if(!each.equals(currAVD)){
						Log.i(TAG, "Replicating into " + each);
						new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgTobeSent, String.valueOf(Integer.parseInt(each) * 2));
					}
				}
			}

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return uri;
	}

	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub

		ServerSocket serverSocket = null;
		currAVD = getPortNumber();
		RecoveryFailureStatus = false;
		initializeStructure();
		try {
			serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
		}
		catch (IOException e){
			Log.d(TAG,"Exception in Socket Creation");

		}
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "MissedWrites");
		return false;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
						String[] selectionArgs, String sortOrder) {
		// TODO Auto-generated method stub
		//Log.d(TAG,selectionArgs[0]);
		if( (selectionArgs!=null && !selectionArgs[0].equals("Recover")) || selectionArgs==null){
			try {
				while(!RecoveryFailureStatus)
				{
					Log.v(TAG, "Waiting for Missed Writes Op to Complete");
					Thread.sleep(100);

				}
			} catch (InterruptedException e) {
				Log.d(TAG,"Exception in Thread Waiting");
				e.printStackTrace();
			}
		}
		MatrixCursor Cursor = new MatrixCursor(new String[] {"key", "value"});
		Context context = getContext();

		try {
			if(selection.contains("@") || selection.contains("*")) {
				//Query Entire AVD
				for (File Directories : context.getFilesDir().listFiles()) {
					String[] name = Directories.toString().split(Pattern.quote(System.getProperty("file.separator")));
					String key = name[name.length-1];
					InputStream ip = context.openFileInput(key);

					if (ip != null) {
						BufferedReader reader = new BufferedReader(new InputStreamReader(ip));
						String value = reader.readLine();
						if(selectionArgs == null){
							value = value.split(":")[0];
						}
						String data[] = {key, value};
						Cursor.addRow(data);
					} else {
						Log.v(TAG, "File Empty!");
					}
				}

				Log.i(TAG,"Query * successful on AVD: "+currAVD);
				//If only one Node is present
				if(selection.contains("*") && !(succ.equals("") || succ == null)){
					String msg = "*@";
					String port = String.valueOf(Integer.parseInt(currAVD)*2);
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, port);

					String data = Queue.take();
					String[] pairs = data.split("-");
					for(String pair : pairs){

						if(!pair.equals("") && !(pair == null)){
							String[] keyvalue = pair.split("_");
							String key = keyvalue[0];
							String val = keyvalue[1].split(":")[0];
							String Data[] = {key, val};
							Cursor.addRow(Data);
						}
					}
				}
				Log.i(TAG,"Query * successful on AVD: "+currAVD);
				return Cursor;
			}
			else {
				//Query single key
				String value = null;
				if(selectionArgs == null) {
					String[] ports = getNextNode(selection);
					String temp = "";
					for(String port: ports){
						if (port.equals(currAVD)){
							InputStream ip = context.openFileInput(selection);
							BufferedReader reader = new BufferedReader(new InputStreamReader(ip));
							value = reader.readLine();
							temp = selection+"_"+value;
						}
					}

					Log.i(TAG,"Query "+ selection +"successful on AVD: "+currAVD);
					String msg="Query@"+selection+"-"+currAVD;

					BlockingQueue<String> Queue0 = new ArrayBlockingQueue<String>(1);
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, Queue0);

					String str = Queue0.take();

					if(!temp.equals(""))
						str += temp;

					String[] pairs = str.split("-");
					String[] keyval1 = pairs[1].split("_");
					String newVal = keyval1[1].split(":")[0];
					String timeStamp1 = keyval1[1].split(":")[1];
					//Compare val with timestamp
					String[] keyval = pairs[0].split("_");
					String key = keyval[0];
					String val = keyval[1].split(":")[0];
					String timeStamp = keyval[1].split(":")[1];

					if(timeStamp1.compareTo(timeStamp)>0) {
						val = newVal;
					}

					String record[] = {key, val};
					Cursor.addRow(record);
					Log.d(TAG,"Query Result on CurrAVD "+ currAVD + " :" + key + " "+ val);
					return Cursor;

				} else if(selectionArgs!=null){
					BufferedReader reader = new BufferedReader(new InputStreamReader(context.openFileInput(selection)));
					String val = reader.readLine();
					String record[] = {selection, val};
					Cursor.addRow(record);
					Log.d(TAG,"Query Result on CurrAVD "+ currAVD + " :" + selection + " "+ val);
					return Cursor;
				}

			}

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
					  String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	//ServerTask
	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {


		@Override
		protected Void doInBackground(ServerSocket... sockets) {
			ServerSocket serverSocket = sockets[0];

			while (true) {
				try {
					Socket socket = serverSocket.accept();
					Log.d(TAG,"Server started on AVD:" + currAVD);
					socket.setSoTimeout(100);

					BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					DataOutputStream out;

					String requestReceived = in.readLine();
					String[] message = requestReceived.split("@");
					Log.d("Message","Request to "+ message[0]);

					if(message[0].equals("Delete")){

						Log.d(TAG,"Request received on AVD: " + currAVD + "to delete");
						String selection = message[1];
						String[] SelectionArgs = {"SomeUnusedString"};
						delete(myUri,selection,SelectionArgs);
						DataOutputStream deleteOut = new DataOutputStream(socket.getOutputStream());
						deleteOut.writeBytes("Acknowledgement"+"\n");
						deleteOut.flush();
						deleteOut.close();
						socket.close();

					}
					else if(message[0].equals("MissedWrites")){

						String ack = "Acknowledge#";
						String[] t = {"Recover"};
						Cursor cursor = query(myUri, null, "@" ,t , null, null);
						if(cursor.moveToFirst()){
							do{

								String key = cursor.getString(cursor.getColumnIndex("key"));
								String value = cursor.getString(cursor.getColumnIndex("value"));
								ack += key +"_" + value+"-";

							}while (cursor.moveToNext());
						}

						out = new DataOutputStream(socket.getOutputStream());
						out.writeBytes(ack +"\n");
						out.flush();
						out.close();
						in.close();
						socket.close();

					}
					else if(message[0].equals("Insert")){

						ContentValues values= new ContentValues();
						String[] data = message[1].split("-");
						Log.d(TAG,data[0] + "  " + data[1]);
						values.put("key",data[0]);
						values.put("value",data[1]);
						Log.d(TAG,"Request received to insert "+data[0]+" "+ data[1] +"in AVD: "+currAVD);
						insert(myUri,values);
						DataOutputStream insertout = new DataOutputStream(socket.getOutputStream());
						insertout.writeBytes("InsertSuccessful\n");
						insertout.flush();
						insertout.close();
						in.close();
						socket.close();


					} else if(message[0].equals("Query")) {

						try{

							String[] data = message[1].split("-");
							Log.d(TAG,"Data:" + data);
							String[] arr = {"rand"};
							Cursor cursor = query(myUri, null, data[0], arr, null, null);

							String key = null;
							String value = null;

							if (cursor.moveToFirst()) {
								do {

									key = cursor.getString(cursor.getColumnIndex("key"));
									value = cursor.getString(cursor.getColumnIndex("value"));

								} while (cursor.moveToNext());
							}

							out = new DataOutputStream(socket.getOutputStream());
							out.writeBytes("Acknowledge#" + key + "_" + value + "\n");
							out.flush();
							out.close();
							in.close();
							socket.close();

						}
						catch (Exception e){
							Log.i(TAG, "Exception in server while executing query "+e);
						}
					}
					else if(message[0].equals("*")){

						Cursor cursor = query(myUri, null, "@" , null, null, null);
						String ack = "Acknowledge#";
						if(cursor.moveToFirst()){
							do{

								String key = cursor.getString(cursor.getColumnIndex("key"));
								String value = cursor.getString(cursor.getColumnIndex("value"));
								ack += key +"_" + value+"-";

							}while (cursor.moveToNext());
						}
						ack += "#"+currAVD;

						out = new DataOutputStream(socket.getOutputStream());
						out.writeBytes(ack+"\n");
						out.flush();
						out.close();
						in.close();
						socket.close();
					}
				} catch (SocketException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}


	private class ClientTask extends AsyncTask<Object, Void, Void> {

		@Override
		protected Void doInBackground(Object... msgs) {

			try {

				String msgToSend = (String) msgs[0];
				String[] message = msgToSend.split("@");
				Log.d(TAG,message[0]);

				if(message[0].equals("Delete")) {
					Log.d(TAG,"Sending Request to Delete on AVD: "+currAVD);
					try {
						String[] selection = message[1].split("-");
						Log.v(TAG,"delete data : "+selection[0]);
						String[] ports = getNextNode(selection[0]);
						for(int i = 0; i< ports.length; i++) {

							try{
								if (ports[i] != "" && !ports[i].equals(currAVD)) {
									Log.v(TAG,"Inside loop");
									Socket deleteSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(ports[i]) * 2);
									DataOutputStream deleteout = new DataOutputStream(deleteSocket.getOutputStream());
									deleteout.writeBytes(msgToSend + "\n");
									deleteout.flush();
									Log.v(TAG,"Sent data to Server on another AVD");
									BufferedReader in = new BufferedReader(new InputStreamReader(deleteSocket.getInputStream()));
									String response = in.readLine();
									if (response.equals("Acknowledgement")) {
										in.close();
										deleteSocket.close();
										Log.v(TAG,"Response Received");
									}
								}
							}
							catch (Exception e){
								Log.d(TAG,"Exception in client task for Socket in deletion "+e);
							}
						}

					}catch (Exception e){
						Log.v(TAG,"Exception in Client for deletion" + e);
					}
				}
				else if(message[0].equals("MissedWrites")) {
					Log.v(TAG,"Reached in Client Task Missed Writes");
					String data = "";
					String msg = "";
					for (NodeStructure node : Structure) {
						try {
							if (!node.getPort().equals(currAVD)) {
								Log.v(TAG,node.getPort());
								Socket sockets = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(node.getPort()) * 2);
								DataOutputStream out = new DataOutputStream(sockets.getOutputStream());
								Log.v(TAG,"Reached socket creation from missed writes");
								msg = "MissedWrites";
								out.writeBytes(msg + "\n");
								out.flush();

								BufferedReader in = new BufferedReader(new InputStreamReader(sockets.getInputStream()));
								String ack = in.readLine();
								Log.v(TAG,"Reached ack from missed writes");
								if (ack.startsWith("Acknowledge")) {
									Log.v(TAG,"Missed Ack" + ack);
									in.close();
									sockets.close();
									String[] temp = ack.split("#");
									if (temp.length > 1)
										data += ack.split("#")[1];
								}

							}
						} catch (Exception e) {
							Log.v(TAG, "Exception in missed writes" + e);
						}
					}
					Log.v(TAG,data);
					if (data.length() > 0)
					{
						String[] pairs = data.split("-");
						HashMap<String, String> hashmap = new HashMap<String, String>();

						for (String pair : pairs) {
							String[] keyvalue = pair.split("_");
							String key = keyvalue[0];
							String value = keyvalue[1];
							String[] next = getNextNode(key);
							for (String s : next) {
								if (s.equals(currAVD)) {
									if (!hashmap.containsKey(key)) {
										hashmap.put(key, value);
									} else {
										if (value.split(":")[1].compareTo(hashmap.get(key).split(":")[1]) > 0) {
											hashmap.put(key, value);
										}
									}
								}
							}


						}

						for(String key:hashmap.keySet()){
							ContentValues values= new ContentValues();
							values.put("key",key);
							values.put("value",hashmap.get(key));
							insert(myUri,values);
						}
					}

					RecoveryFailureStatus = true;
					Log.d(TAG,"Reaching here");
				}
				else if (message[0].equals("Insert")) {
					try {
						Log.v(TAG," "+msgs[1]);
						Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt((String) msgs[1]));
						DataOutputStream insertOut = new DataOutputStream(socket.getOutputStream());
						insertOut.writeBytes(msgToSend + "\n");
						insertOut.flush();
						BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
						String ack = in.readLine();
						if (ack.equals("InsertSuccessful")) {
							Log.i(TAG, "Insert successful at AVD: "+currAVD);
							in.close();
							socket.close();
						}
						if (ack == null) {
							Log.d(TAG,"Exception in reading response for inserting at AVD: "+currAVD);
							throw new NullPointerException();
						}
					}
					catch (Exception e){
						Log.v(TAG,"Exception in Inserting at AVD: "+ currAVD +"\n"+ e);
					}


				} else if (message[0].equals("Query")){

					int count = 0;
					String keyvalues = "";

					String[] msg = message[1].split("-");
					Log.d(TAG,"Selection " + msg[0]);
					Log.d(TAG,"Length of msgs : " + msgs.length + "  Msgs : " + msgs[1]);
					BlockingQueue<String> queue3 = (BlockingQueue<String>) msgs[1];

					ArrayList<String> node = new ArrayList<String>();

					String[] nodes = getNextNode(msg[0]);

					node.add(nodes[0]);
					node.add(nodes[1]);
					node.add(nodes[2]);

					if(node.contains(currAVD)){
						node.remove(currAVD);
						count = 1;
					}

					for(int i = 0; i < node.size(); i++){
						Log.v(TAG,node.get(i));
					}
					for(String each : node){
						try {
							Log.v(TAG,"Each "+ each);
							Socket socketQuery = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(each) * 2);
							DataOutputStream out = new DataOutputStream(socketQuery.getOutputStream());
							out.writeBytes(msgToSend + "\n");
							out.flush();

							BufferedReader in = new BufferedReader(new InputStreamReader(socketQuery.getInputStream()));
							String ack = in.readLine();

							if (ack == null) {
								Log.d(TAG,"Acknowledgement Not Received");
								throw new NullPointerException();
							}

							if (ack.startsWith("Acknowledge")) {
								String[] response = ack.split("#");

								keyvalues += response[1]+"-";
								count++;
								if(count==2){
									queue3.put(keyvalues);
								}
								in.close();
								socketQuery.close();
								Log.d(TAG,"Query Executed Successfully");
							}

						}catch (UnknownHostException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}catch (Exception e){
							Log.v(TAG,"Query Exception: "+e);
						}
					}
				}

				else if (message[0].equals("*")) {
					Log.i(TAG, "Request received for * from AVD:  " + currAVD);
					String pairs = "";

					for(NodeStructure node: Structure) {
						try {
							Socket socketStar = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(node.getPort()) * 2);
							DataOutputStream out = new DataOutputStream(socketStar.getOutputStream());

							out.writeBytes(msgToSend + "\n");
							out.flush();

							BufferedReader in = new BufferedReader(new InputStreamReader(socketStar.getInputStream()));
							String ack = in.readLine();


							if (ack == null)
								throw new NullPointerException();

							if (ack.startsWith("Acknowledge")) {
								String[] data = ack.split("#");
								pairs += data[1];
								in.close();
								socketStar.close();
							}
						}catch(Exception e){

						}
					}
					Queue.put(pairs);
				}

			} catch (Exception e){

			}

			return null;
		}
	}

	private String getPortNumber() {
		TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
		String port = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		return String.valueOf((Integer.parseInt(port)));

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
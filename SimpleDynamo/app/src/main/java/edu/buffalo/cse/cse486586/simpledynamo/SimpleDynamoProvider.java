package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import  java.util.List;


import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

public class SimpleDynamoProvider extends ContentProvider {
	static final String TAG = SimpleDynamoProvider.class.getSimpleName();
	static final String[] REMOTE_PORTS = new String[] {"11108", "11112", "11116", "11120", "11124"};
	static final String[] EMU_PORTS = new String[] {"5554","5556","5558","5560","5562"};


	//static final String[] REMOTE_PORTS = new String[] {"11108", "11112", "11116"};
	//static final String[] EMU_PORTS = new String[] {"5554","5556","5558"};


	static ArrayList<String> idSpace = new ArrayList<String>();
	static ArrayList<String> hashValues = new ArrayList<String>();
	static final int SERVER_PORT = 10000;
	static  String emuPort = "0000";
	static  String myPort = "00000";
	static Uri mUri;
	static ContentResolver mContentResolver;
	static String[] columns = new String[] {"key", "value" };
	static MatrixCursor returnCursor = new MatrixCursor(columns);
	static int queryCounter=0;
	static ArrayList<Cursor> cursorlist = new ArrayList<Cursor>();
	static String FAILED_NODE = "00000";
	static ArrayList<String> signal = new ArrayList<String>();

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		File directory = getContext().getFilesDir();
		File[] files = directory.listFiles();
		String[] keys = selection.split(":");
		String key = keys[0];
		String msgType = "Delete";
		if(keys.length==2) {
			msgType="DeleteReplication";
		}

		try {
			if (key.equals("*") && msgType.equals("Delete")) {
				for (String emuport : idSpace) {
					String remotePort = String.valueOf((Integer.parseInt(emuport) * 2));
					if (remotePort.equals(myPort)) {
						for (File file : files) {
							boolean dir = getContext().deleteFile(file.getName());
						}
					} else {
						if(!remotePort.equals(FAILED_NODE)) {
							String msgToSend = "Delete" + ":" + key;
							new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, remotePort);
						}
					}
				}

			} else if (key.equals("@") && msgType.equals("Delete")) {
				for (File file : files) {
					boolean dir = getContext().deleteFile(file.getName());
				}
			} else if(msgType.equals("DeleteReplication")){
				boolean dir = getContext().deleteFile(key);
			}
			else{
				Log.i("Current ID Space",idSpace.toString());
				Log.i("Current Hashed ID Space",hashValues.toString());


				ArrayList<String> SuccessorEmuPorts = findSuccessor(idSpace,hashValues,genHash(key));
				ArrayList<String> SuccessorPorts = findRedirectionPorts(SuccessorEmuPorts);

				Log.i("Successor emuports",SuccessorEmuPorts.toString());
				Log.i("Successor ports",SuccessorPorts.toString());

				if (SuccessorPorts.get(0).equals(myPort)){
					boolean dir = getContext().deleteFile(key);

					String msgToSend = "DeleteReplication" + ":" + key;
					SuccessorPorts.remove(FAILED_NODE);
					SuccessorPorts.remove(myPort);
					for(String port:SuccessorPorts) {
						new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, port);
					}
				}
				else{
					String msgToSend = "Delete" + ":" + key;
					if(SuccessorPorts.get(0).equals(FAILED_NODE)) {
						msgToSend = "DeleteReplication" + ":" + key;
						SuccessorPorts.remove(FAILED_NODE);
						for(String port:SuccessorPorts) {
							new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, port);
						}
					} else{
						new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, SuccessorPorts.get(0));
					}

				}
			}
		}
		catch (Exception e){
			e.printStackTrace();
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
		// TODO Auto-generated method stub
		try {

			//TimeUnit.SECONDS.sleep(1);
			Log.i("","Insert Started");
			Log.i("Failed node",FAILED_NODE);

			String[] keys = values.get("key").toString().split(":");
			String key = keys[0];
			String value = values.get("value").toString() + "\n";
			String msgType = "Insert";

			if(keys.length==2) {
				msgType=keys[1];
			}

			Log.i("Message Type",msgType);
			Log.i("key :",key);
			Log.i("value :",value);

			Log.i("Current ID Space",idSpace.toString());
			Log.i("Current Hashed ID Space",hashValues.toString());


			ArrayList<String> SuccessorEmuPorts = findSuccessor(idSpace,hashValues,genHash(key));
			ArrayList<String> SuccessorPorts = findRedirectionPorts(SuccessorEmuPorts);

			Log.i("Successor emuports",SuccessorEmuPorts.toString());
			Log.i("Successor ports",SuccessorPorts.toString());

			if (msgType.equals("Insert") && SuccessorPorts.contains(myPort)) {
				FileOutputStream outputStream;
				outputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
				outputStream.write(value.getBytes());
				outputStream.close();

				Log.i("Message inserted", key + " : " + value);
				//Replication
				SuccessorPorts.remove(myPort);
				String msgToSend = "InsertReplication" + ":" + key + ":" + value;
				SuccessorPorts.remove(FAILED_NODE);
				for(String port: SuccessorPorts) {
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, port);
				}
			} else if(msgType.equals("Insert") && !SuccessorPorts.contains(myPort)) {
				String msgToSend = "InsertReplication" + ":" + key + ":" + value;
				SuccessorPorts.remove(FAILED_NODE);
				for(String port:SuccessorPorts) {
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, port);
				}
			} else if((msgType.equals("InsertReplication") || msgType.equals("FailedRecoveryResult")) && SuccessorPorts.contains(myPort)) {
				if (msgType.equals("FailedRecoveryResult")) {
					File file = new File(getContext().getFilesDir(), key);
					if (!file.exists()) {
						try {
							FileOutputStream outputStream;
							outputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
							outputStream.write(value.getBytes());
							outputStream.close();
						} catch (Exception e) { e.printStackTrace(); }
					}
					else{
						FileInputStream inputStream = getContext().openFileInput(key);
						StringBuilder sbuild = new StringBuilder();
						BufferedReader bufRead = new BufferedReader(new InputStreamReader(inputStream));
						String content;
						while ((content = bufRead.readLine()) != null) {
							sbuild.append(content);
						}
						bufRead.close();
						String val = sbuild.toString();
						inputStream.close();
						Log.i("File already exists",key+" : "+val);

					}
				} else {
					try {
						FileOutputStream outputStream;
						outputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
						outputStream.write(value.getBytes());
						outputStream.close();
						Log.i("Message inserted", key + " : " + value);
					} catch (Exception e) { e.printStackTrace(); }
				}
			}
		} catch (Exception e) {
			Log.i("writeToFile", "File write failed");
			e.printStackTrace();
		}
		return uri;
	}

	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub
		try {idSpace = buildIDSpace(EMU_PORTS); } catch(Exception e){ e.printStackTrace(); }
		try {hashValues = findHashValues(idSpace); } catch(Exception e){ e.printStackTrace(); }

		mContentResolver=getContext().getContentResolver();
		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.authority("edu.buffalo.cse.cse486586.simpledynamo.provider");
		uriBuilder.scheme("content");
		mUri= uriBuilder.build();

		Context context = getContext();
		TelephonyManager tel = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		emuPort = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		myPort = String.valueOf((Integer.parseInt(emuPort) * 2));
		Log.i("my port",myPort);
		//Log.i("My Ip address",)
		try {
			//Server task

			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);


			mContentResolver.delete(SimpleDynamoProvider.mUri, "@", null);

			//Client task
			String msg = "NodeJoin"+":"+myPort;
			for(String port: REMOTE_PORTS) {
				if(!port.equals(myPort)){
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, port);
				}
			}
			ArrayList<String> redirPorts = findRedirectionPorts(idSpace);
			int ind = redirPorts.indexOf(myPort);
			msg = "FailedRecovery"+":"+myPort;
			String port1="";
			String port2="";
			if(ind!=0 && ind!=redirPorts.size()-1) {
				port1=redirPorts.get(ind - 1);
				port2=redirPorts.get(ind + 1);
			} else if (ind==0){
				port1=redirPorts.get(redirPorts.size()-1);
				port2=redirPorts.get(ind + 1);
			} else if(ind==redirPorts.size()-1){
				port1=redirPorts.get(ind - 1);
				port2=redirPorts.get(0);
			}
			String returnval1= new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg,port1).get();
			String returnval2=new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg,port2).get();

		} catch (Exception e) {
			Log.i(TAG, "Can't create a ServerSocket");
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
						String sortOrder) {
		// TODO Auto-generated method stub
		Log.i("Query Started","key : "+selection);
		Log.i("Failed node",FAILED_NODE);
		String[] columns = new String[] {"key", "value" };
		MatrixCursor matrixCursor = new MatrixCursor(columns);
		String key=selection;

		File directory = getContext().getFilesDir();
		File[] files = directory.listFiles();
		try {
			//filename=genHash(filename);
			if (selection.equals("*")) {
				for (String emuport : idSpace) {
					String remotePort = String.valueOf((Integer.parseInt(emuport) * 2));
					if (remotePort.equals(myPort)) {
						for (File file : files) {
							FileInputStream inputStream = getContext().openFileInput(file.getName());
							StringBuilder sbuild = new StringBuilder();
							BufferedReader bufRead = new BufferedReader(new InputStreamReader(inputStream));
							String content;
							while ((content = bufRead.readLine()) != null) {
								sbuild.append(content);
							}
							bufRead.close();
							String value = sbuild.toString();
							Log.i("Query Content", value);
							inputStream.close();
							matrixCursor.addRow(new String[]{file.getName(), value});
						}
					} else {
						if (!remotePort.equals(FAILED_NODE)) {
							Log.i("Other ports", "");
							String msgToSend = "Query" + ":" + selection + ":" + myPort;
							String queryResult = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, remotePort).get();
							if(!queryResult.equals("null")) {
								String[] queryArray = queryResult.split(":");
								int i = 1;
								while (i < queryArray.length) {
									key = queryArray[i];
									String value = queryArray[i + 1];
									Log.i("Key : Value", key + " : " + value);
									matrixCursor.addRow(new String[]{key, value});
									i = i + 2;
								}
							}
						}
					}
				}
			} else if (selection.equals("@")){
				int count=0;
				for (File file: files){
					FileInputStream inputStream = getContext().openFileInput(file.getName());
					StringBuilder sbuild = new StringBuilder();
					BufferedReader bufRead = new BufferedReader(new InputStreamReader(inputStream));
					String content;
					while ((content=bufRead.readLine()) != null) {
						sbuild.append(content);
					}
					bufRead.close();
					String value=sbuild.toString();
					Log.i("Query Content", value);
					inputStream.close();
					matrixCursor.addRow(new String[]{file.getName(),value});
					count++;
				}
				Log.e("@ count",String.valueOf(count));
				return matrixCursor;
			}
			else {

				Log.i("Current ID Space", idSpace.toString());
				Log.i("Current Hashed ID Space", hashValues.toString());


				ArrayList<String> SuccessorEmuPorts = findSuccessor(idSpace, hashValues, genHash(key));
				ArrayList<String> SuccessorPorts = findRedirectionPorts(SuccessorEmuPorts);

				Log.i("Successor emuports", SuccessorEmuPorts.toString());
				Log.i("Successor ports", SuccessorPorts.toString());

				File file = new File(getContext().getFilesDir(), key);
				if (file.exists()) {
					try {
						FileInputStream inputStream = getContext().openFileInput(key);
						StringBuilder sbuild = new StringBuilder();
						BufferedReader bufRead = new BufferedReader(new InputStreamReader(inputStream));
						String content;
						while ((content = bufRead.readLine()) != null) {
							sbuild.append(content);
						}
						bufRead.close();
						String value = sbuild.toString();
						Log.i("Query Content", value);
						inputStream.close();
						matrixCursor.addRow(new String[]{key, value});
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					Log.i("", "File not found key:" + key);
					String msgToSend = "Query" + ":" + key + ":" + myPort;
					if (SuccessorPorts.contains(myPort)) {
						SuccessorPorts.remove(myPort);
					}
					SuccessorPorts.remove(FAILED_NODE);
					String queryResult="";
					for (String port : SuccessorPorts) {
						queryResult=new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, port).get();
						Log.e("Query result return",queryResult);
						if(!queryResult.equals("null")){
							break;
						}
					}
					String[] queryArray = queryResult.split(":");
					key = queryArray[1];
					String value=queryArray[2];
					Log.i("Query Successfull","key : "+key+","+"value:"+value);
					matrixCursor.addRow(new String[]{key,value});
				}
			}
		} catch (Exception e) {
			Log.i("Exception",e.getMessage());
			Log.i("query", selection);
			Log.i("ReadFile", e.getMessage());
		}
		return matrixCursor;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	public static String genHash(String input) throws NoSuchAlgorithmException {
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		byte[] sha1Hash = sha1.digest(input.getBytes());
		Formatter formatter = new Formatter();
		for (byte b : sha1Hash) {
			formatter.format("%02x", b);
		}
		return formatter.toString();
	}

	public static ArrayList<String> buildIDSpace(String[] emuports) throws NoSuchAlgorithmException {
		String[] hashedPorts = new String[emuports.length];

		for (int i = 0; i < emuports.length; i++) {
			hashedPorts[i] = genHash(emuports[i]);
		}
		for (int i = 0; i < emuports.length - 1; ++i) {
			for (int j = i + 1; j < emuports.length; ++j) {
				if (hashedPorts[i].compareTo(hashedPorts[j]) > 0) {
					String temp = hashedPorts[i];
					hashedPorts[i] = hashedPorts[j];
					hashedPorts[j] = temp;

					temp = emuports[i];
					emuports[i] = emuports[j];
					emuports[j] = temp;

				}
			}
		}

		ArrayList<String> idSpace = new ArrayList<String>();
		idSpace.addAll(Arrays.asList(emuports));
		return idSpace;
	}

	public static ArrayList<String> findSuccessor(ArrayList<String> nodes, ArrayList<String> hashnodes, String key) {
		int i=0;
		for (String element : hashnodes) {
			if (key.compareTo(element) >= 0) {
				i++;
			} else {
				break;
			}
		}
		if (i>=nodes.size()){
			i=0;
		}
		int firstIndex = i;
		int secondIndex = i+1;
		int thirdIndex = i+2;

		if (firstIndex==nodes.size()-2){
			secondIndex = nodes.size()-1;
			thirdIndex = 0;
		} else if (firstIndex==nodes.size()-1){
			secondIndex = 0;
			thirdIndex = 1;
		}
		ArrayList<String> successorPorts = new ArrayList<String>();
		successorPorts.add(nodes.get(firstIndex));
		successorPorts.add(nodes.get(secondIndex));
		successorPorts.add(nodes.get(thirdIndex));

		return successorPorts;
	}

	public static ArrayList<String> findHashValues(ArrayList<String> nodes) throws NoSuchAlgorithmException {
		ArrayList<String> hashednodes = new ArrayList<String>();
		for (String element : nodes) {
			hashednodes.add(genHash(element));
		}
		return hashednodes;
	}

	public static ArrayList<String> findRedirectionPorts(ArrayList<String> emuPorts) {
		ArrayList<String> redirPorts = new ArrayList<String>();
		for (String emuPort: emuPorts) {
			redirPorts.add(String.valueOf((Integer.parseInt(emuPort) * 2)));
		}
		return redirPorts;

	}
}
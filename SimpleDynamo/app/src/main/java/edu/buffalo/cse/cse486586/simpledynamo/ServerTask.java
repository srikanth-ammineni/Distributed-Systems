package edu.buffalo.cse.cse486586.simpledynamo;


import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import android.os.AsyncTask;

import java.net.SocketTimeoutException;
import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Semaphore;



public class ServerTask extends AsyncTask<ServerSocket, String, Void> {
    static Semaphore semaphore = new Semaphore(0);
    @Override
    protected Void doInBackground (ServerSocket...sockets){
        ServerSocket serverSocket = sockets[0];
            System.out.println("Server Task running");
            while (true) {
                try {
                    Socket socket = serverSocket.accept();


                    DataInputStream is = new DataInputStream(socket.getInputStream());
                    String msgFromClient = is.readUTF();

                    DataOutputStream os = new DataOutputStream(socket.getOutputStream());

                    String[] messages = msgFromClient.split(":");
                    String msgType = messages[0];

                    Log.i("Message from Client",msgFromClient);

                    if (msgType.equals("NodeJoin")){
                        if(!SimpleDynamoProvider.FAILED_NODE.equals("00000")){
                            synchronized (this) {
                                SimpleDynamoProvider.FAILED_NODE = "00000";
                            }
                        }
                        os.writeUTF("Failed node reset");
                        os.flush();
                    }
                    else if (msgType.equals("InsertReplication")) {
                        String key = messages[1].trim() + ":InsertReplication";
                        String value = messages[2].trim();
                        ContentValues cv = new ContentValues();
                        cv.put("key", key);
                        cv.put("value", value);
                        SimpleDynamoProvider.mContentResolver.insert(SimpleDynamoProvider.mUri, cv);
                        os.writeUTF("Insert Replication done");
                        os.flush();
                    }
                    else if (msgType.equals("Delete")) {
                        String key = messages[1].trim();
                        if (key.equals("*")) {
                            SimpleDynamoProvider.mContentResolver.delete(SimpleDynamoProvider.mUri, "@", null);
                        } else {
                            SimpleDynamoProvider.mContentResolver.delete(SimpleDynamoProvider.mUri, key, null);
                        }
                        os.writeUTF("Deletion done");
                        os.flush();
                    }
                    else if (msgType.equals("DeleteReplication")) {
                        String key = messages[1].trim() + ":DeleteReplication";
                        SimpleDynamoProvider.mContentResolver.delete(SimpleDynamoProvider.mUri, key, null);
                        os.writeUTF("Deletion Replication done");
                        os.flush();
                    }
                   else if (msgType.equals("Query")) {
                        String key = messages[1].trim();
                        String requestingPort = messages[2].trim();
                        String messagetoSend="QueryResult*";
                        if (!key.equals("*")) {
                            Cursor resultCursor = SimpleDynamoProvider.mContentResolver.query(SimpleDynamoProvider.mUri, null,
                                    key, null, null);
                            int keyIndex = resultCursor.getColumnIndex("key");
                            int valueIndex = resultCursor.getColumnIndex("value");
                            resultCursor.moveToFirst();
                            String returnKey = resultCursor.getString(keyIndex);
                            String returnValue = resultCursor.getString(valueIndex);
                            messagetoSend = "QueryResult" + ":" + returnKey + ":" + returnValue;
                            resultCursor.close();

                        } else {
                            Cursor resultCursor = SimpleDynamoProvider.mContentResolver.query(SimpleDynamoProvider.mUri, null,
                                    "@", null, null);
                            if (resultCursor.moveToFirst()) {
                                while (resultCursor.isAfterLast() == false) {
                                    String returnKey = resultCursor.getString(resultCursor.getColumnIndex("key"));
                                    String returnValue = resultCursor.getString(resultCursor.getColumnIndex("value"));
                                    messagetoSend = messagetoSend + ":" + returnKey + ":" + returnValue;
                                    resultCursor.moveToNext();
                                }
                            }
                        }
                        os.writeUTF(messagetoSend);
                        os.flush();
                    }

                    else if(msgType.equals("FailedRecovery")){
                        Log.e("","Failed recovery message received");
                        String requestingPort = messages[1];
                        Cursor resultCursor = SimpleDynamoProvider.mContentResolver.query(SimpleDynamoProvider.mUri, null,
                                "@", null, null);
                        String messagetoSend = "FailedRecoveryResult";
                        if (resultCursor.moveToFirst()) {
                            while (resultCursor.isAfterLast() == false) {
                                String returnKey = resultCursor.getString(resultCursor.getColumnIndex("key"));
                                String returnValue = resultCursor.getString(resultCursor.getColumnIndex("value"));
                                messagetoSend = messagetoSend + ":" + returnKey + ":" + returnValue;
                                resultCursor.moveToNext();
                            }
                        }
                        resultCursor.close();
                        os.writeUTF(messagetoSend);
                        os.flush();
                    }

                } catch(Exception e){
                    e.printStackTrace();
                }
            }

    }
}


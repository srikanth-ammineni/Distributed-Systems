package edu.buffalo.cse.cse486586.simpledht;


import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import android.os.AsyncTask;

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
            SimpleDhtProvider provider = new SimpleDhtProvider();
            ServerSocket serverSocket = sockets[0];
            try {
                ArrayList<String> ports = new ArrayList<String>();

                while (true) {
                    Socket socket = serverSocket.accept();
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String msgFromClient = input.readLine().trim();
                    String[] messages = msgFromClient.split(":");
                    String msgType = messages[0];

                    System.out.println("Message from Client");
                    System.out.println(msgFromClient);
                    if (msgType.equals("NodeJoin")) {
                        String port = messages[1];
                        ports.add(port);
                        String[] portsArray = ports.toArray(new String[ports.size()]);
                        SimpleDhtProvider.idSpace = SimpleDhtProvider.buildIDSpace(portsArray);
                        System.out.println("Id Space");
                        System.out.println(Collections.singletonList(SimpleDhtProvider.idSpace));

                        String msgToSend="IDSpace";

                        for(String element : SimpleDhtProvider.idSpace){
                            msgToSend=msgToSend+":"+element;
                        }

                        for (String element : SimpleDhtProvider.idSpace) {
                            String remotePort = String.valueOf((Integer.parseInt(element) * 2));
                            if (!remotePort.equals(SimpleDhtProvider.myPort)) {
                                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                        Integer.parseInt(remotePort));
                                PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                                output.println(msgToSend);
                            }
                        }
                    }
                    else if(msgType.equals("IDSpace")){
                        String[] portsArray = Arrays.copyOfRange(messages, 1, messages.length);
                        SimpleDhtProvider.idSpace = new ArrayList<String>(Arrays.asList(portsArray));
                        System.out.println("Id Space");
                        System.out.println(Collections.singletonList(SimpleDhtProvider.idSpace));
                    }
                    else if(msgType.equals("Insert")){
                        String key = messages[1].trim();
                        String value = messages[2].trim();
                        ContentValues cv = new ContentValues();
                        cv.put("key", key);
                        cv.put("value", value);
                        SimpleDhtProvider.mContentResolver.insert(SimpleDhtProvider.mUri, cv);
                    }
                    else if(msgType.equals("Delete")){
                        String key = messages[1].trim();
                        if(key.equals("*")) {
                            SimpleDhtProvider.mContentResolver.delete(SimpleDhtProvider.mUri, "@", null);
                        }
                        else{
                            SimpleDhtProvider.mContentResolver.delete(SimpleDhtProvider.mUri, key, null);
                        }
                    }
                    else if(msgType.equals("Query")){
                        String key = messages[1].trim();
                        String requestingPort = messages[2].trim();
                        if (!key.equals("*")) {
                            Cursor resultCursor = SimpleDhtProvider.mContentResolver.query(SimpleDhtProvider.mUri, null,
                                    key, null, null);
                            int keyIndex = resultCursor.getColumnIndex("key");
                            int valueIndex = resultCursor.getColumnIndex("value");
                            resultCursor.moveToFirst();
                            String returnKey = resultCursor.getString(keyIndex);
                            String returnValue = resultCursor.getString(valueIndex);
                            String messagetoSend = "QueryResult" + ":" + returnKey + ":" + returnValue;
                            resultCursor.close();
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(requestingPort));
                            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                            output.println(messagetoSend);
                        } else{
                            Cursor resultCursor = SimpleDhtProvider.mContentResolver.query(SimpleDhtProvider.mUri, null,
                                    "@", null, null);
                            String messagetoSend = "QueryResult*";

                            ;
                            if(resultCursor.moveToFirst()){
                                while(resultCursor.isAfterLast() == false) {
                                    String returnKey = resultCursor.getString(resultCursor.getColumnIndex("key"));
                                    String returnValue = resultCursor.getString(resultCursor.getColumnIndex("value"));
                                    messagetoSend=messagetoSend+":"+returnKey+":"+returnValue;
                                    resultCursor.moveToNext();
                                }
                                }
                            resultCursor.close();
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(requestingPort));
                            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                            output.println(messagetoSend);
                        }
                    }
                    else if(msgType.equals("QueryResult")){
                        String key = messages[1];
                        String value = messages[2];

                        SimpleDhtProvider.returnCursor=new MatrixCursor(SimpleDhtProvider.columns);
                        SimpleDhtProvider.returnCursor.addRow(new String[]{key, value});
                        semaphore.release();
                    }
                    else if(msgType.equals("QueryResult*")){
                        int i=1;
                        MatrixCursor cursor =new MatrixCursor(SimpleDhtProvider.columns);
                        System.out.println("Query Result *");
                        while(i<messages.length){
                            String key = messages[i];
                            String value = messages[i+1];
                            System.out.println(key+" : "+value);
                            cursor.addRow(new String[]{key, value});
                            i=i+2;
                        }
                        synchronized (this) {
                            SimpleDhtProvider.cursorlist.add(cursor);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    }


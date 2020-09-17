package edu.buffalo.cse.cse486586.simpledynamo;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;


public class ClientTask extends AsyncTask<String, Void, String> {


    @Override
    protected String doInBackground(String... msgs) {

        String msgtoSend = msgs[0];
        String portToSend = msgs[1];

        Log.i("Message to send",msgtoSend);
                try {
                    Log.i("Sending to Port",portToSend);
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(portToSend));
                    DataOutputStream os = new DataOutputStream(socket.getOutputStream());

                    os.writeUTF(msgtoSend);
                    os.flush();


                //Recieve message from Server
                    DataInputStream is = new DataInputStream(socket.getInputStream());
                    String messageFromServer = is.readUTF();
                    Log.i("message From Server", messageFromServer);
                    socket.close();

                    String[] messages = messageFromServer.split(":");
                    String msgType = messages[0];

                    if (msgType.equals("QueryResult")) {
                        return messageFromServer;
                    } else if (msgType.equals("QueryResult*")) {
                        return messageFromServer;
                    } else if(msgType.equals("FailedRecoveryResult")) {
                        Log.i("", "Getting data from neighbour nodes");
                        int i = 1;
                        while (i < messages.length) {
                            ContentValues cv = new ContentValues();
                            String key = messages[i];
                            String value = messages[i + 1];
                            Log.i("Key : Value", key + " : " + value);
                            cv.put("key", key + ":" + "FailedRecoveryResult");
                            cv.put("value", value);
                            Log.i("", "Inserting as failed recovery");
                            SimpleDynamoProvider.mContentResolver.insert(SimpleDynamoProvider.mUri, cv);
                            i = i + 2;
                        }
                        return "Failed recovery successfull";
                    }

                } catch(EOFException e) {
                    Log.i("Failed node", portToSend);
                    SimpleDynamoProvider.FAILED_NODE = portToSend;
                    return "null";
                    /*
                    String[] msgSplit = msgtoSend.split(":");
                    try{
                        String key = msgSplit[1];
                        ArrayList<String> SuccessorEmuPorts = SimpleDynamoProvider.findSuccessor(SimpleDynamoProvider.idSpace,SimpleDynamoProvider.hashValues,SimpleDynamoProvider.genHash(key));
                        ArrayList<String> SuccessorPorts = SimpleDynamoProvider.findRedirectionPorts(SuccessorEmuPorts);
                     if(msgSplit[0].equals("Query") && !key.equals("*")) {
                         int ind = SuccessorPorts.indexOf(portToSend);
                        portToSend = SuccessorPorts.get(ind+1);
                        String msgToSend = "Query" + ":" + key + ":" + SimpleDynamoProvider.myPort;
                        Log.i("Message to send",msgToSend);
                        Log.i("sending to port",portToSend);
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(portToSend));
                        DataOutputStream os = new DataOutputStream(socket.getOutputStream());
                        os.writeUTF(msgtoSend);
                    }
                    }catch(Exception exp){
                            exp.printStackTrace();
                        }
                     */
                } catch(Exception e){
                    e.printStackTrace();
                }
        return "null";
    }
}
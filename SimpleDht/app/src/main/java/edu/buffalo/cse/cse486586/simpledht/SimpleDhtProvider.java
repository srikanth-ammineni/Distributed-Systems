package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.concurrent.TimeUnit;

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
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;

public class SimpleDhtProvider extends ContentProvider {
    static final String TAG = SimpleDhtProvider.class.getSimpleName();
    static final String[] REMOTE_PORTS = new String[] {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    static  String emuPort = "0000";
    static  String myPort = "00000";
    static ArrayList<String> idSpace = new ArrayList<String>();
    static Uri mUri;
    static ContentResolver mContentResolver;
    static String[] columns = new String[] {"key", "value" };
    static MatrixCursor returnCursor = new MatrixCursor(columns);
    static int queryCounter=0;
    static ArrayList<Cursor> cursorlist = new ArrayList<Cursor>();

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        File directory = getContext().getFilesDir();
        File[] files = directory.listFiles();
        try {
            if (selection.equals("*")) {
                for (String emuport : idSpace) {
                    String remotePort = String.valueOf((Integer.parseInt(emuport) * 2));
                    if (remotePort.equals(myPort)) {
                        for (File file : files) {
                            boolean dir = getContext().deleteFile(file.getName());
                        }
                        if (idSpace.size() == 1) {
                            return 0;
                        }
                    } else {
                        String msgToSend = "Delete" + ":" + selection + ":" + myPort;
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(remotePort));
                        PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                        output.println(msgToSend);
                    }
                }

            } else if (selection.equals("@")) {
                for (File file : files) {
                    boolean dir = getContext().deleteFile(file.getName());
                }
            } else {
                ArrayList<String> hashedIds = findHashValues(idSpace);


                System.out.println("Current ID Space");
                System.out.println(Collections.singletonList(idSpace));

                System.out.println("Current Hashed ID Space");
                System.out.println(Collections.singletonList(hashedIds));

                String SuccessorEmuPort = findSuccessor(idSpace,hashedIds,genHash(selection));
                String SuccessorPort = String.valueOf((Integer.parseInt(SuccessorEmuPort) * 2));
                System.out.println("Successor emuport: "+SuccessorEmuPort);
                System.out.println("Successor port: "+SuccessorPort);

                if (SuccessorPort.equals(myPort)){
                    boolean dir = getContext().deleteFile(selection);
                }

                else{
                    String msgToSend = "Delete" + ":" + selection + ":" + myPort;
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(SuccessorPort));
                    PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                    output.println(msgToSend);
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

            String key = values.get("key").toString();
            String value = values.get("value").toString() + "\n";

            Log.v("key :",key);
            Log.v("value :",value);

            ArrayList<String> hashedIds = findHashValues(idSpace);


            System.out.println("Current ID Space");
            System.out.println(Collections.singletonList(idSpace));

            System.out.println("Current Hashed ID Space");
            System.out.println(Collections.singletonList(hashedIds));

            String SuccessorEmuPort = findSuccessor(idSpace,hashedIds,genHash(key));
            String SuccessorPort = String.valueOf((Integer.parseInt(SuccessorEmuPort) * 2));
            System.out.println("Successor emuport: "+SuccessorEmuPort);
            System.out.println("Successor port: "+SuccessorPort);
            if (SuccessorPort.equals(myPort)){
                FileOutputStream outputStream;
                outputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
                outputStream.write(value.getBytes());
                outputStream.close();
                }
            else{
                String msgToSend = "Insert"+":"+key+":"+value;
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(SuccessorPort));
                PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                output.println(msgToSend);
        }
        } catch (Exception e) {
            Log.e("writeToFile", "File write failed");
            e.printStackTrace();
        }
        return uri;
    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub

        mContentResolver=getContext().getContentResolver();
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.simpledht.provider");
        uriBuilder.scheme("content");
        mUri= uriBuilder.build();

        Context context = getContext();
        TelephonyManager tel = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        emuPort = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(emuPort) * 2));
        idSpace.add(emuPort);
        try {
            //Server task
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

            //Client task
            String msg=myPort+":"+"NodeJoin";
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);

        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
        }
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // TODO Auto-generated method stub
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
                                Log.v("Query Content", value);
                                inputStream.close();
                                matrixCursor.addRow(new String[]{file.getName(),value});
                            }
                            if (idSpace.size() == 1) {
                                return matrixCursor;
                            }
                            synchronized (this) {
                                cursorlist.add(matrixCursor);
                            }
                        } else {
                            System.out.println("Other ports");
                            String msgToSend = "Query" + ":" + key + ":" + myPort;
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(remotePort));
                            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                            output.println(msgToSend);
                        }
                    }
                    try {
                        while(true) {
                            synchronized (this) {
                                System.out.println("Cursor list size");
                                System.out.println(cursorlist.size());
                                if (idSpace.size() == cursorlist.size()) {
                                    Cursor[] cursors = cursorlist.toArray(new Cursor[0]);
                                    System.out.println("Cursor length");
                                    System.out.println(cursors.length);
                                    cursorlist = new ArrayList<Cursor>();
                                    MergeCursor mergecursor = new MergeCursor(cursors);
                                    mergecursor.moveToFirst();
                                    while (mergecursor.moveToNext()) {
                                        String returnKey = mergecursor.getString(mergecursor.getColumnIndex("key"));
                                        String returnValue = mergecursor.getString(mergecursor.getColumnIndex("value"));
                                        System.out.println(returnKey + " : " + returnValue);
                                    }
                                    mergecursor.close();
                                    return mergecursor;
                                }
                            }
                            //TimeUnit.SECONDS.sleep(1);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            else if (selection.equals("@")){
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
                    Log.v("Query Content", value);
                    inputStream.close();
                    matrixCursor.addRow(new String[]{file.getName(),value});
                }
                return matrixCursor;
            }
            else {
                ArrayList<String> hashedIds = findHashValues(idSpace);


                System.out.println("Current ID Space");
                System.out.println(Collections.singletonList(idSpace));

                System.out.println("Current Hashed ID Space");
                System.out.println(Collections.singletonList(hashedIds));

                String SuccessorEmuPort = findSuccessor(idSpace,hashedIds,genHash(key));
                String SuccessorPort = String.valueOf((Integer.parseInt(SuccessorEmuPort) * 2));
                System.out.println("Successor emuport: "+SuccessorEmuPort);
                System.out.println("Successor port: "+SuccessorPort);
                if (SuccessorPort.equals(myPort)) {
                    FileInputStream inputStream = getContext().openFileInput(key);
                    StringBuilder sbuild = new StringBuilder();
                    BufferedReader bufRead = new BufferedReader(new InputStreamReader(inputStream));
                    String content;
                    while ((content = bufRead.readLine()) != null) {
                        sbuild.append(content);
                    }
                    bufRead.close();
                    String value = sbuild.toString();
                    Log.v("Query Content", value);
                    inputStream.close();
                    matrixCursor.addRow(new String[]{key, value});
                }
                else{
                    String msgToSend = "Query"+":"+key+":"+myPort;
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(SuccessorPort));
                    PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                    output.println(msgToSend);
                    try{
                    ServerTask.semaphore.acquire();
                    return SimpleDhtProvider.returnCursor;

                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }

        } catch (Exception e) {
            Log.v("Exception",e.getMessage());
            Log.v("query", selection);
            Log.e("ReadFile", e.getMessage());
        }
        return matrixCursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private static String genHash(String input) throws NoSuchAlgorithmException {
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
        //System.out.println(Arrays.toString(hashedPorts));
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
        //System.out.println(Arrays.toString(hashedPorts));
        //System.out.println(Arrays.toString(emuports));

        ArrayList<String> idSpace = new ArrayList<String>();
        idSpace.addAll(Arrays.asList(emuports));

        //System.out.println(idSpace.toString());
        return idSpace;
    }

    public static String findSuccessor(ArrayList<String> nodes, ArrayList<String> hashnodes, String key) {
        int i=0;
        for (String element : hashnodes) {
            if (key.compareTo(element) >= 0) {
                i++;
            } else {
                break;
            }
        }
        if (i>=nodes.size()){
            System.out.println(nodes.get(0));
            return nodes.get(0);
        }
        System.out.println(nodes.get(i));
        return nodes.get(i);
    }

    public static ArrayList<String> findHashValues(ArrayList<String> nodes) throws NoSuchAlgorithmException {
        ArrayList<String> hashednodes = new ArrayList<String>();
        for (String element : nodes) {
            hashednodes.add(genHash(element));
        }
        return hashednodes;
    }
}



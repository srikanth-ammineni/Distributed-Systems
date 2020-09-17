package edu.buffalo.cse.cse486586.simpledht;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.util.HashMap;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;

public class ClientTask extends AsyncTask<String, Void, Void> {


    @Override
    protected Void doInBackground(String... msgs) {

            System.out.println("My port: "+SimpleDhtProvider.myPort);
            String messagetoSend = "NodeJoin"+":"+SimpleDhtProvider.emuPort;
            System.out.println("Message to send");
            System.out.println(messagetoSend);
        try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt("11108"));
                PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                output.println(messagetoSend);
        } catch(Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}

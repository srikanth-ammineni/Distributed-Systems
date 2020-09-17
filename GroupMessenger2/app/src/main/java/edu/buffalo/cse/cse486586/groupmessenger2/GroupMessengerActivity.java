package edu.buffalo.cse.cse486586.groupmessenger2;

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
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;

import static java.lang.Math.max;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String[] REMOTE_PORTS = new String[] {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    static final String KEY_FIELD = "key";
    static final String VALUE_FIELD = "value";
    static Uri mUri;
    static ContentResolver mContentResolver;
    static int proposed = 0;
    static int counter=0;
    static String myPort="00000";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        mContentResolver=getContentResolver();

        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
        uriBuilder.scheme("content");
        mUri= uriBuilder.build();
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        final EditText editText = (EditText) findViewById(R.id.editText1);
        final Button send = (Button) findViewById(R.id.button4);

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView textDisplay = (TextView) findViewById(R.id.textView1);
                String msg = editText.getText().toString() + "\n";
                editText.setText(""); // This is one way to reset the input box.
                textDisplay.append("\t" + msg); // This is one way to display a string.
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
    class MessageComparator implements Comparator<HashMap> {

        // Overriding compare()method of Comparator
        // for descending order of cgpa
        public int compare(HashMap h1, HashMap h2) {
            int pid1 = Integer.parseInt((String) h1.get("proposingProcessId"));
            int pid2 = Integer.parseInt((String) h2.get("proposingProcessId"));
            int seq1 = Integer.parseInt((String) h1.get("proposedSeq"));
            int seq2 = Integer.parseInt((String) h2.get("proposedSeq"));
            if (seq1 > seq2)
                return 1;
            else if (seq1 < seq2)
                return -1;
            else {
                if (pid1 > pid2)
                    return 1;
                else if (pid1 < pid2)
                    return -1;
            }
            return 0;
        }
    }
    public void insertIntoFile(ContentValues cv, String msg, int seq) {
        cv.put(KEY_FIELD, Integer.toString(seq));
        cv.put(VALUE_FIELD, msg);
        mContentResolver.insert(mUri, cv);
        Log.v("Sequence number", String.valueOf(seq));
    }
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            /*
             * server code that receives messages and passes them
             * to onProgressUpdate().
             */
            try {
                int seq =0;
                System.out.println("This is my port number");
                System.out.println(myPort);
                PriorityQueue<HashMap<String, String>> pQueue = new PriorityQueue<HashMap<String, String>>(1500, new MessageComparator());
                HashMap<String, HashMap<String, Integer>> proposedList = new HashMap<String, HashMap<String, Integer>>();
                HashMap<String, Integer> prevDelMsgs = new HashMap<String, Integer>();
                PriorityQueue<HashMap<String, String>> FinalpQueue = new PriorityQueue<HashMap<String, String>>(1500, new MessageComparator());
                while (true) {
                    Socket ssocket = serverSocket.accept();

                    //read message1 from client
                    ObjectInput oi1 = new ObjectInputStream(ssocket.getInputStream());
                    HashMap msgFromClient = (HashMap) oi1.readObject();
                    System.out.println(Collections.singletonList("msgFromClient"));
                    System.out.println(Collections.singletonList(msgFromClient));

                    String messageType=(String) msgFromClient.get("messageType");

                    if (messageType.equals("InitialMulticast")) {
                        //Add to priority queue
                        HashMap<String, String> MsginQueue = new HashMap<String, String>();
                        MsginQueue.put("message", (String) msgFromClient.get("message"));
                        MsginQueue.put("messageId", (String) msgFromClient.get("messageId"));
                        MsginQueue.put("sendingProcessId", (String) msgFromClient.get("sendingProcessId"));
                        MsginQueue.put("proposedSeq", String.valueOf(proposed));
                        MsginQueue.put("proposingProcessId", (String) msgFromClient.get("receivingProcessId"));
                        MsginQueue.put("status", "Undeliverable");
                        int msgPresentinQueue = 0;
                        Iterator<HashMap<String, String>> value = pQueue.iterator();
                        while (value.hasNext()) {
                            HashMap<String, String> map = value.next();
                            if (map.get("messageId").equals(msgFromClient.get("messageId")) && map.get("sendingProcessId").equals(msgFromClient.get("sendingProcessId"))) {
                                msgPresentinQueue = 1;
                                break;
                            }
                        }
                        if (msgPresentinQueue!=1) {
                            System.out.println("MsginQueue");
                            System.out.println(MsginQueue);
                            pQueue.add(MsginQueue);
                        }
                        System.out.println("Priority Queue");
                        System.out.println(Collections.singletonList(pQueue));

                        //Send proposal to process that sent message
                        proposed++;
                        HashMap<String, String> proposal = new HashMap<String, String>();
                        proposal.put("message", MsginQueue.get("message"));
                        proposal.put("messageId", MsginQueue.get("messageId"));
                        proposal.put("sendingProcessId", MsginQueue.get("sendingProcessId"));
                        proposal.put("proposingProcessId", myPort);
                        proposal.put("proposalSequence", String.valueOf(proposed));
                        proposal.put("messageType", "proposal");
                        if (MsginQueue.get("sendingProcessId").equals(myPort)) {
                            if (proposedList.containsKey((String) msgFromClient.get("messageId"))) {
                                HashMap<String, Integer> proposedSeqs = (HashMap<String, Integer>) proposedList.get((String) msgFromClient.get("messageId"));
                                proposedSeqs.put(myPort,Integer.parseInt(proposal.get("proposalSequence")));
                                proposedList.put(MsginQueue.get("messageId"),proposedSeqs);

                            } else{
                                HashMap<String, Integer> proposedSeqs = new HashMap<String, Integer>();
                                proposedSeqs.put(myPort,Integer.parseInt(proposal.get("proposalSequence")));
                                proposedList.put(MsginQueue.get("messageId"),proposedSeqs);
                            }
                            System.out.println("proposedList");
                            System.out.println(Collections.singletonList(proposedList));
                        }
                        else{
                            System.out.println("Proposal");
                            System.out.println(Collections.singletonList(proposal));
                            System.out.println("Sending proposal to client");
                            System.out.println(proposal.get("sendingProcessId"));
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(proposal.get("sendingProcessId")));
                            ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                            outputStream.writeObject(proposal);
                            //socket.close();
                        }
                    }
                    else if (messageType.equals("agreedSequence")){
                        Iterator<HashMap<String, String>> value = pQueue.iterator();
                        while (value.hasNext()) {
                            HashMap<String, String> map = value.next();
                            int msgId = Integer.parseInt(map.get("messageId"));
                            String SendingProcessId = map.get("sendingProcessId");
                            if (map.get("messageId").equals(msgFromClient.get("messageId")) && map.get("sendingProcessId").equals(msgFromClient.get("sendingProcessId"))) {
                                pQueue.remove(map);
                                HashMap<String, String> updatedEntry =  new HashMap<String, String>();
                                updatedEntry.put("messageId", map.get("messageId"));
                                updatedEntry.put("message",  map.get("message"));
                                updatedEntry.put("sendingProcessId", SendingProcessId);
                                updatedEntry.put("proposedSeq", (String) msgFromClient.get("AgreedSequence"));
                                updatedEntry.put("proposingProcessId", (String) msgFromClient.get("proposedProcessId"));
                                updatedEntry.put("status", "deliverable");
                                if (msgId==1) {
                                    updatedEntry.put("fifoStatus", "deliverable");
                                    pQueue.add(updatedEntry);
                                }
                                else {
                                    updatedEntry.put("fifoStatus", "waiting");
                                    pQueue.add(updatedEntry);
                                }
                                break;
                            }
                        }
                        String AgreedSeq = (String) msgFromClient.get("AgreedSequence");
                        proposed=Math.max(proposed,Integer.parseInt(AgreedSeq));
                        System.out.println("Priority Queue after updation");
                        System.out.println(Collections.singletonList(pQueue));
                    }
                    else if (messageType.equals("proposal")){
                        System.out.println("proposed list");
                        System.out.println(Collections.singletonList(proposedList));
                        int msgPresentinQueue = 0;
                        Iterator<HashMap<String, String>> value = pQueue.iterator();
                        while (value.hasNext()) {
                            HashMap<String, String> map = value.next();
                            if (map.get("messageId").equals(msgFromClient.get("messageId")) && map.get("sendingProcessId").equals(msgFromClient.get("sendingProcessId"))) {
                                msgPresentinQueue = 1;
                                break;
                            }
                        }
                        if (msgPresentinQueue!=1) {
                            //Add to priority queue
                            HashMap<String, String> MsginQueue = new HashMap<String, String>();
                            MsginQueue.put("message", (String) msgFromClient.get("message"));
                            MsginQueue.put("messageId", (String) msgFromClient.get("messageId"));
                            MsginQueue.put("sendingProcessId", (String) msgFromClient.get("sendingProcessId"));
                            MsginQueue.put("proposedSeq", (String) msgFromClient.get("proposalSequence"));
                            MsginQueue.put("proposingProcessId", (String) msgFromClient.get("proposingProcessId"));
                            MsginQueue.put("status", "Undeliverable");
                            System.out.println(Collections.singletonList(MsginQueue));
                            pQueue.add(MsginQueue);
                        }
                        System.out.println("Priority Queue");
                        System.out.println(Collections.singletonList(pQueue));
                        if (proposedList.containsKey((String) msgFromClient.get("messageId"))) {
                            HashMap<String, Integer> proposedSeqs = (HashMap<String, Integer>) proposedList.get((String) msgFromClient.get("messageId"));
                            System.out.println("proposedSeqs");
                            System.out.println(Collections.singletonList(proposedSeqs));
                            int propSeq = Integer.parseInt((String) msgFromClient.get("proposalSequence"));
                            proposedSeqs.put((String) msgFromClient.get("proposingProcessId"),propSeq);
                            proposedList.put((String) msgFromClient.get("messageId"), proposedSeqs);
                            System.out.println("proposedList");
                            System.out.println(Collections.singletonList(proposedList));
                        }
                        else{
                            System.out.println("creating for the first time");
                            HashMap<String, Integer> proposedSeqs = new HashMap<String, Integer>();
                            int propSeq = Integer.parseInt((String) msgFromClient.get("proposalSequence"));
                            proposedSeqs.put((String) msgFromClient.get("proposingProcessId"), propSeq);
                            proposedList.put((String) msgFromClient.get("messageId"), proposedSeqs);

                        }
                        System.out.println("Proposed list|||||||||");
                        System.out.println(Collections.singletonList(proposedList));
                        HashMap<String, Integer> proposedSequences = proposedList.get((String) msgFromClient.get("messageId"));
                        if(proposedSequences.size()==5) {
                            //Get Maximum Proposed Sequence
                            System.out.println("Proposed Sequences");
                            System.out.println(Collections.singletonList(proposedSequences));
                            int maxPropSeQ=(Collections.max(proposedSequences.values()));
                            System.out.println("maxPropSeQ");
                            System.out.println(maxPropSeQ);

                            //Choose smallest possible value for proposing process id if there are multiple suggesting this sequence #
                            ArrayList<Integer> listOfProcessIds = new ArrayList<Integer>();
                            for (Map.Entry<String, Integer> entry : proposedSequences.entrySet()) {
                                if (entry.getValue()==maxPropSeQ) {
                                    int key=Integer.parseInt(entry.getKey());
                                    listOfProcessIds.add(key);
                                }
                            }
                            System.out.println("listOfProcessIds");
                            System.out.println(Collections.singletonList(listOfProcessIds));
                            int minIndex = listOfProcessIds.indexOf(Collections.min(listOfProcessIds));
                            System.out.println("Minimum Value");
                            System.out.println(listOfProcessIds.get(minIndex));
                            HashMap<String, String> AgreedSeqMsg = new HashMap<String, String>();

                            AgreedSeqMsg.put("messageId", (String) msgFromClient.get("messageId"));
                            AgreedSeqMsg.put("sendingProcessId", myPort);
                            AgreedSeqMsg.put("AgreedSequence", String.valueOf(maxPropSeQ));
                            AgreedSeqMsg.put("proposedProcessId", Integer.toString(listOfProcessIds.get(minIndex)));
                            AgreedSeqMsg.put("messageType", "agreedSequence");
                            proposed=Math.max(proposed,maxPropSeQ);
                            for (int i = 0; i < REMOTE_PORTS.length; i++) {
                                if (!REMOTE_PORTS[i].equals(myPort)) {
                                    System.out.println("Sending Agreed Sequence to client");
                                    System.out.println(Integer.parseInt(REMOTE_PORTS[i]));
                                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                            Integer.parseInt(REMOTE_PORTS[i]));
                                    ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                                    outputStream.writeObject(AgreedSeqMsg);
                                    //socket.close();
                                } else {
                                    Iterator<HashMap<String, String>> itr = pQueue.iterator();
                                    while (itr.hasNext()) {
                                        HashMap<String, String> map = itr.next();
                                        int msgId = Integer.parseInt(map.get("messageId"));
                                        String SendingProcessId = map.get("sendingProcessId");
                                        if (map.get("messageId").equals(msgFromClient.get("messageId")) && map.get("sendingProcessId").equals(msgFromClient.get("sendingProcessId"))) {
                                            pQueue.remove(map);
                                            HashMap<String, String> updatedEntry =  new HashMap<String, String>();
                                            updatedEntry.put("messageId", map.get("messageId"));
                                            updatedEntry.put("message", map.get("message"));
                                            updatedEntry.put("sendingProcessId", SendingProcessId);
                                            updatedEntry.put("proposedSeq", AgreedSeqMsg.get("AgreedSequence"));
                                            updatedEntry.put("proposingProcessId", AgreedSeqMsg.get("proposedProcessId"));
                                            updatedEntry.put("status", "deliverable");
                                            if (msgId==1) {
                                                updatedEntry.put("fifoStatus", "deliverable");
                                                pQueue.add(updatedEntry);
                                            }
                                            else {
                                                updatedEntry.put("fifoStatus", "waiting");
                                                pQueue.add(updatedEntry);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Iterator<HashMap<String, String>> itr = pQueue.iterator();
                    while (itr.hasNext()) {
                        HashMap<String, String> map = itr.next();
                        int msgId = Integer.parseInt(map.get("messageId"));
                        String SendingProcessId = map.get("sendingProcessId");
                        String status = map.get("status");
                        String fifoStatus = map.get("fifoStatus");
                        if (status.equals("deliverable") && fifoStatus.equals("waiting")){
                            System.out.println("PrevDelMsgs");
                            System.out.println(prevDelMsgs);
                            if (msgId==(prevDelMsgs.get(SendingProcessId)+1)){
                                pQueue.remove(map);
                                map.put("fifoStatus", "deliverable");
                                pQueue.add(map);
                            }
                        }
                    }
                    while(true) {
                        HashMap<String, String> head = pQueue.peek();
                        if (pQueue.size() != 0) {
                            if (head.get("status").equals("deliverable") && head.get("fifoStatus").equals("deliverable")) {
                                pQueue.remove(head);
                                head.put("key",String.valueOf(seq));
                                FinalpQueue.add(head);
                                String msg = head.get("message");
                                publishProgress(msg);
                                ContentValues cv = new ContentValues();
                                insertIntoFile(cv,msg,seq);
                                seq++;
                                prevDelMsgs.put(head.get("sendingProcessId"), Integer.parseInt(head.get("messageId")));
                                System.out.println("Message being delivered");
                                System.out.println(Collections.singletonList(head));
                                System.out.println("Final Priority Queue");
                                System.out.println(Collections.singletonList(FinalpQueue));
                            } else {
                                break;
                            }
                        }
                        else {
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                Log.v("Server Error", e.getMessage());
            } catch (Exception e){
                e.printStackTrace();
            }
            return null;
        }


        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView localTextView = (TextView) findViewById(R.id.textView1);
            localTextView.append(strReceived + "\t\n");
        }
    }

    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    public synchronized int incrementCounter() {
        counter++;
        return counter;
    }
    private class ClientTask extends AsyncTask<String, Void, Void> {


        @Override
        protected Void doInBackground(String... msgs) {
            try {
                String processID = msgs[1];
                int tempCounter=incrementCounter();
                HashMap<String, String> MulticastMsg = new HashMap<String, String>();
                MulticastMsg.put("message", msgs[0].trim());
                MulticastMsg.put("messageId", String.valueOf(tempCounter));
                MulticastMsg.put("sendingProcessId", processID);
                MulticastMsg.put("messageType", "InitialMulticast");
                //System.out.println(Collections.singletonList(MulticastMsg));
                for (int i = 0; i < REMOTE_PORTS.length; i++) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORTS[i]));
                    //Send message to server of each client
                    MulticastMsg.put("receivingProcessId", REMOTE_PORTS[i]);
                    ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                    outputStream.writeObject(MulticastMsg);
                    //socket.close();
                }

            }catch(UnknownHostException e){
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch(IOException e){
                Log.e(TAG, "ClientTask socket IOException");
                e.printStackTrace();
            } catch(Exception e){
                e.printStackTrace();
            }

            return null;
        }
    }
}


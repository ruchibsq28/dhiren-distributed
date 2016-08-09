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
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.io.Serializable;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
/*Start of Code Change - Project 2B */

class message implements Serializable
{
    String mes_val;
    int proc_counter;
    int proc_id;
    int seq_no;
    int sug_proc_id;
    String mes_status;
    int mes_type;
}
class queue_struct
{
    String mes_val;
    int proc_id;
    int seq_no;
    int sug_proc_id;
    String mes_status;
    int proc_counter;
}
class prop_seq
{
    String mes_val;
    int proc_counter;
    int proposed_seq;
    int sug_proc_id;
    int count;
}
/*End of Code Change - Project 2B */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    Button button_send;
    EditText editText;
    Integer counter = 0;
    Uri uri = Uri.parse("content://edu.buffalo.cse.cse486586.groupmessenger2.provider/messages");
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final int SERVER_PORT = 10000;
    String myPort = "";
    /*Start of Code Change - Project 2B */
    private int no_aliveProc = 5;
    private int no_mes_proc[] = new int[5];
    private int ref_no_mes_proc[] = new int[5];
    private int waiting_proposal_proc_wise[] = new int[5];
    private message mes = new message();
    private ArrayList<queue_struct> mes_queue = new ArrayList<queue_struct>();
    private int seq_no = 0;
    private HashMap<String, prop_seq> deliv_mes = new HashMap<String, prop_seq>();
    private HashMap<String, Integer> mes_seq_map = new HashMap<String, Integer>();
    private int temp_count_del = 1;
    private final Object lock = new Object();
    private ArrayList<Integer> processes = new ArrayList<Integer>();
    ServerSocket serverSocket;
    int resultant;

    /*End of Code Change - Project 2B */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        mes.proc_id = Integer.parseInt(myPort);
        mes.proc_counter = 0;

        no_mes_proc[0] = 0;
        no_mes_proc[1] = 0;
        no_mes_proc[2] = 0;
        no_mes_proc[3] = 0;
        no_mes_proc[4] = 0;
        ref_no_mes_proc[0] = 0;
        ref_no_mes_proc[1] = 0;
        ref_no_mes_proc[2] = 0;
        ref_no_mes_proc[3] = 0;
        ref_no_mes_proc[4] = 0;
        waiting_proposal_proc_wise[0] = 0;
        waiting_proposal_proc_wise[1] = 0;
        waiting_proposal_proc_wise[2] = 0;
        waiting_proposal_proc_wise[3] = 0;
        waiting_proposal_proc_wise[4] = 0;
        resultant = Integer.MAX_VALUE;
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
            serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            //  new TimeoutTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        } catch (IOException io) {
            Log.e(TAG, "IO Exception Server Socket");
        }
        button_send = (Button) findViewById(R.id.button4);
        editText = (EditText) findViewById(R.id.editText1);
        button_send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Do something in response to button click
                String msg = editText.getText().toString();
                editText.setText(""); // This is one way to reset the input box.
                TextView localTextView = (TextView) findViewById(R.id.textView1);
                localTextView.append(msg + "\n"); // This is one way to display a string.
                TextView localTextView1 = (TextView) findViewById(R.id.textView2);
                localTextView1.append("\n"); // This is one way to display a string
                new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
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
            Socket s = null;
            String str;

            while (true) {
                try {
                    long start = System.currentTimeMillis();
                    s = serverSocket.accept();
                    long end = System.currentTimeMillis();
                    resultant -= (int) (end - start);
                    if (resultant < 0)
                        resultant = 500;
                    ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
                    message temp_mes = (message) ois.readObject();
                    ois.close();

                    synchronized (lock) {
                        if (temp_mes.mes_type == 1 && temp_mes.mes_status.equals("undeliverable")) {
                            no_mes_proc[(temp_mes.sug_proc_id - 11108) / 4]++;
                            // ref_no_mes_proc[(temp_mes.sug_proc_id - 11108) / 4]++;
                        } else {
                            no_mes_proc[(temp_mes.proc_id - 11108) / 4]++;
                            // ref_no_mes_proc[(temp_mes.proc_id - 11108) / 4]++;
                        }

                    }
                    serverSocket.setSoTimeout(resultant);

                    publishProgress(temp_mes.mes_val, String.valueOf(temp_mes.proc_counter), String.valueOf(temp_mes.proc_id), String.valueOf(temp_mes.seq_no),
                            String.valueOf(temp_mes.sug_proc_id), temp_mes.mes_status, String.valueOf(temp_mes.mes_type));
                } catch (SocketTimeoutException s1) {
                    synchronized (lock) {
                        int port = 11108;
                        int temp = 0;
                        while (port <= 11124) {
                            if (port != Integer.parseInt(myPort)) {
                                //  if (ref_no_mes_proc[(port - 11108) / 4] != 0) {
                                if (no_mes_proc[(port - 11108) / 4] == 0) {
                                    ref_no_mes_proc[(port - 11108) / 4]++;
                                    if(ref_no_mes_proc[(port - 11108) / 4]==3) {
                                        processes.add(port);
                                        no_aliveProc -= 1;
                                        temp = 1;
                                    }
                                    if (waiting_proposal_proc_wise[(port - 11108) / 4] > 0) {
                                        no_aliveProc -= 1;
                                        processes.add(port);
                                        //processes.remove((Object) port);
                                        temp = 1;
                                        // waiting_proposal_proc_wise[(port - 11108) / 4] = 0;
                                    }
                                } else {
                                    no_mes_proc[(port - 11108) / 4] = 0;
                                    ref_no_mes_proc[(port - 11108) / 4]=0;
                                }

                                //  }
                            }
                            port += 4;
                        }
                        if(temp == 1)
                        publishProgress("Special Mission");
                    }
                    try {
                        serverSocket.setSoTimeout(5000);
                    } catch (SocketException e) {
                        e.printStackTrace();
                    }
                    resultant = 5000;
                } catch (IOException io) {
                    Log.e(TAG, "Can't Receive on serverSocket");
                } catch (ClassNotFoundException cnfe) {
                    Log.e(TAG, "Class Not Found in ServerTask");
                }
            }
            //return null;
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            queue_struct qs;

            if (strings[0].equals("Special Mission")) {
                qs = new queue_struct();
                prop_seq ps = new prop_seq();
                //  deliv_mes.get(strings[0]);*/
                HashMap<String, prop_seq> deliv_mes_temp = new HashMap<String, prop_seq>();
                deliv_mes_temp.putAll(deliv_mes);
                Set set = deliv_mes_temp.entrySet();
                Iterator i = set.iterator();
                while (i.hasNext()) {
                    Map.Entry me = (Map.Entry) i.next();
                    ps = (prop_seq) me.getValue();
                    if (ps.count >= no_aliveProc) {
                        deliv_mes.remove(ps.mes_val);
                        new SendAckTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, (String) me.getKey(), String.valueOf(ps.proc_counter), myPort, String.valueOf(ps.proposed_seq),
                                String.valueOf(ps.sug_proc_id), "deliverable", String.valueOf(1));
                    }
                }
                while (true) {
                    int temp = 0, min_sug_proc = Integer.MAX_VALUE, min = Integer.MAX_VALUE, min_index = -1;
                    String temp_mes_status = new String();
                    if (mes_queue.size() != 0) {
                        while (temp < mes_queue.size()) {
                            if (!processes.contains(mes_queue.get(temp).proc_id) || mes_queue.get(temp).mes_status.equals("deliverable")) {
                                if (mes_queue.get(temp).seq_no < min) {
                                    min_index = temp;
                                    min = mes_queue.get(temp).seq_no;
                                    min_sug_proc = mes_queue.get(temp).sug_proc_id;
                                    temp_mes_status = mes_queue.get(temp).mes_status;
                                } else if (mes_queue.get(temp).seq_no == min) {
                                    if (mes_queue.get(temp).sug_proc_id < min_sug_proc && temp_mes_status.equals("deliverable")) {
                                        min_index = temp;
                                        min_sug_proc = mes_queue.get(temp).sug_proc_id;
                                        temp_mes_status = mes_queue.get(temp).mes_status;
                                    }
                                }
                            }
                            temp++;
                        }
                        if (min_index != -1) {
                            if (mes_queue.get(min_index).mes_status.equals("deliverable")) {
                                qs = new queue_struct();
                                qs = mes_queue.get(min_index);
                                mes_queue.remove(min_index);
                                ContentValues cv = new ContentValues();
                                cv.put("key", Integer.toString(counter));
                                cv.put("value", qs.mes_val);
                                counter++;
                                ContentResolver cr = getContentResolver();
                                cr.insert(uri, cv);
                            } else
                                break;
                        } else
                            break;
                    } else
                        break;
                }
            } else {
                TextView localTextView = (TextView) findViewById(R.id.textView2);
                localTextView.append(strings[1] + " " + strings[0] + " " + strings[3] + " " + strings[5] + "\n");
                TextView localTextView1 = (TextView) findViewById(R.id.textView1);
                localTextView1.append("\n");//\n\n\n\n\n\n");

                if (strings[5].equals("deliverable")) {

                    synchronized (lock) {
                        seq_no = Math.max(seq_no, Integer.parseInt(strings[3]));
                    }
                    //int temp = mes_seq_map.get(strings[0]);
                    qs = new queue_struct();
                    Iterator it = mes_queue.iterator();
                    while (it.hasNext()) {
                        qs = (queue_struct) it.next();
                        if (qs.mes_val.equals(strings[0])) {
                            mes_queue.remove(qs);
                            qs.mes_status = "deliverable";
                            qs.seq_no = Integer.parseInt(strings[3]);
                            qs.sug_proc_id = Integer.parseInt(strings[4]);
                            mes_queue.add(qs);
                            break;
                        }
                    }

                    while (true) {
                        int temp = 0, min_sug_proc = Integer.MAX_VALUE, min = Integer.MAX_VALUE, min_index = -1;
                        String temp_mes_status = new String();
                        if (mes_queue.size() != 0) {
                            while (temp < mes_queue.size()) {
                                if (!processes.contains(mes_queue.get(temp).proc_id) || mes_queue.get(temp).mes_status.equals("deliverable")) {
                                    if (mes_queue.get(temp).seq_no < min) {
                                        min_index = temp;
                                        min = mes_queue.get(temp).seq_no;
                                        min_sug_proc = mes_queue.get(temp).sug_proc_id;
                                        temp_mes_status = mes_queue.get(temp).mes_status;
                                    } else if (mes_queue.get(temp).seq_no == min) {
                                        if (mes_queue.get(temp).sug_proc_id < min_sug_proc && temp_mes_status.equals("deliverable")) {
                                            min_index = temp;
                                            min_sug_proc = mes_queue.get(temp).sug_proc_id;
                                            temp_mes_status = mes_queue.get(temp).mes_status;
                                        }
                                    }
                                }
                                temp++;
                            }
                            if (min_index != -1) {
                                if (mes_queue.get(min_index).mes_status.equals("deliverable")) {
                                    qs = new queue_struct();
                                    qs = mes_queue.get(min_index);
                                    mes_queue.remove(min_index);
                                    ContentValues cv = new ContentValues();
                                    cv.put("key", Integer.toString(counter));
                                    cv.put("value", qs.mes_val);
                                    counter++;
                                    ContentResolver cr = getContentResolver();
                                    cr.insert(uri, cv);
                                } else
                                    break;
                            } else
                                break;
                        } else
                            break;
                    }

                } else {
                    if (Integer.parseInt(strings[6]) == 0) {
                        if (strings[2].equals(myPort)) {
                            //mes_seq_map.put(strings[0],Integer.parseInt(strings[3]));
                            qs = new queue_struct();
                            qs.mes_val = strings[0];
                            qs.proc_id = Integer.parseInt(strings[2]);
                            qs.seq_no = Integer.parseInt(strings[3]);
                            qs.sug_proc_id = Integer.parseInt(strings[4]);
                            qs.mes_status = "undeliverable";
                            qs.proc_counter = Integer.parseInt(strings[1]);
                            mes_queue.add(qs);
                            prop_seq ps = new prop_seq();
                            if (deliv_mes.get(strings[0]) == null) {
                                ps = new prop_seq();
                                ps.mes_val = strings[0];
                                ps.proc_counter = Integer.parseInt(strings[1]);
                                //++seq_no;
                                ps.proposed_seq = Integer.parseInt(strings[3]);
                                ps.sug_proc_id = Integer.parseInt(myPort);
                                ps.count = 1;
                                deliv_mes.put(strings[0], ps);
                            } else {
                                ps = deliv_mes.get(strings[0]);
                                ps.proc_counter = Integer.parseInt(strings[1]);
                                //++seq_no;
                                if (Integer.parseInt(strings[3]) > ps.proposed_seq) {
                                    ps.proposed_seq = Integer.parseInt(strings[3]);
                                    ps.sug_proc_id = Integer.parseInt(myPort);
                                }
                                ps.count++;
                                deliv_mes.remove(strings[0]);
                                deliv_mes.put(strings[0], ps);
                            }
                        } else {
                            synchronized (lock) {
                                ++seq_no;
                                // mes_seq_map.put(strings[0],seq_no);
                                qs = new queue_struct();
                                qs.mes_val = strings[0];
                                qs.proc_id = Integer.parseInt(strings[2]);
                                qs.seq_no = seq_no;
                                qs.sug_proc_id = Integer.parseInt(myPort);
                                qs.mes_status = "undeliverable";
                                qs.proc_counter = Integer.parseInt(strings[1]);
                                mes_queue.add(qs);
                                new SendAckTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, strings[0], strings[1], strings[2], String.valueOf(seq_no),
                                        myPort, "undeliverable", String.valueOf(1));
                            }
                        }
                    } else {
                        prop_seq ps = deliv_mes.get(strings[0]);
                        if (ps == null) {
                            ps = new prop_seq();
                            ps.mes_val = strings[0];
                            ps.proc_counter = Integer.parseInt(strings[1]);
                            ps.proposed_seq = Integer.parseInt(strings[3]);
                            ps.sug_proc_id = Integer.parseInt(strings[4]);
                            ps.count = 1;
                        } else {
                            ps.count++;
                            if (Integer.parseInt(strings[3]) > ps.proposed_seq) {
                                ps.proposed_seq = Integer.parseInt(strings[3]);
                                ps.sug_proc_id = Integer.parseInt(strings[4]);
                            }
                            ps.proc_counter = Integer.parseInt(strings[1]);
                        }
                        deliv_mes.remove(strings[0]);
                        deliv_mes.put(strings[0], ps);
                        synchronized (lock) {
                            waiting_proposal_proc_wise[(Integer.parseInt(strings[4]) - 11108) / 4]--;
                        }
                        if (ps.count >= no_aliveProc) {
 /*check this line -- i thnk ok*/
                            synchronized (lock) {
                                seq_no = Math.max(seq_no, ps.proposed_seq);
                            }
                            deliv_mes.remove(strings[0]);
                            new SendAckTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, strings[0], String.valueOf(ps.proc_counter), myPort, String.valueOf(ps.proposed_seq),
                                    String.valueOf(ps.sug_proc_id), "deliverable", String.valueOf(1));
                        }
                    }
                }
                return;
            }
        }
    }

    private class SendTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            Integer remotePort = Integer.parseInt(REMOTE_PORT0);
            mes.mes_val = msgs[0];
            mes.proc_counter++;
            mes.mes_status = "undeliverable";
            mes.mes_type = 0;
            synchronized (lock) {
                seq_no++;
                mes.seq_no = seq_no;
            }
            mes.sug_proc_id = Integer.parseInt(myPort);
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */

            while (remotePort <= 11124) {
                try {
                    synchronized (lock) {
                        waiting_proposal_proc_wise[(remotePort - 11108) / 4]++;
                    }
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(Integer.toString(remotePort)));
                    ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                    oos.writeObject(mes);
                    oos.flush();
                    oos.close();
                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                }
                //  }
                remotePort += 4;
            }
            if (mes.proc_counter == 1) {
                synchronized (lock) {
                    try {
                        serverSocket.setSoTimeout(6000);
                    } catch (SocketException e) {
                        e.printStackTrace();
                    }
                    resultant = 6000;
                }
            }
            return null;
        }
    }

    private class SendAckTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            Integer remotePort = Integer.parseInt(REMOTE_PORT0);
            message mes1 = new message();
            mes1.mes_val = msgs[0];
            mes1.proc_counter = Integer.parseInt(msgs[1]);
            mes1.proc_id = Integer.parseInt(msgs[2]);
            mes1.mes_status = msgs[5];
            mes1.mes_type = 1;
            mes1.seq_no = Integer.parseInt(msgs[3]);
            mes1.sug_proc_id = Integer.parseInt(msgs[4]);
            remotePort = Integer.parseInt(msgs[2]);
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */

            if (msgs[5].equals("undeliverable")) {
                //  if (processes.contains(remotePort)) {
                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(Integer.toString(remotePort)));
                    ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                    oos.writeObject(mes1);
                    oos.flush();
                    oos.close();
                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                }
                //   }
            } else {
                remotePort = Integer.parseInt(REMOTE_PORT0);
                while (remotePort <= 11124) {
                    try {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(Integer.toString(remotePort)));
                        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                        oos.writeObject(mes1);
                        oos.flush();
                        oos.close();

                        socket.close();
                    } catch (UnknownHostException e) {
                        Log.e(TAG, "ClientTask UnknownHostException");
                    } catch (IOException e) {
                        Log.e(TAG, "ClientTask socket IOException");
                    }
                    //  }
                    remotePort += 4;
                }
            }
            return null;
        }
    }
}
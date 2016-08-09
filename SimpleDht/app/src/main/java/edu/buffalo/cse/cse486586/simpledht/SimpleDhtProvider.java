package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.UserDictionary;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

public class SimpleDhtProvider extends ContentProvider {

    private final Object lock = new Object();
    private static UriMatcher sUriMatcher  = new UriMatcher(UriMatcher.NO_MATCH);;
    DatabaseHelper dh = null;
    String TAG;
    String myPort = null;
    String prevPort = null;
    String nextPort = null;
    String hashedMyPort = null;
    String hashedPrevPort = null;
    String hashedNextPort = null;
    Uri uri = Uri.parse("content://edu.buffalo.cse.cse486586.simpledht.provider/messages");
    ArrayList<String> aliveProc = new ArrayList<String>();
    String smallestProcessId;
    int indicator = 0;
    ServerSocket serverSocket = null;
    HashMap<String,String> hm = new HashMap<String,String>();
    int globalIndicator = 0;
    int noOfResponses = 0;
    boolean queryOpeOver = false;
    ContentResolver cr ;
    MatrixCursor matrixCursor = new MatrixCursor(new String[] {"key","value"});

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        dh = new DatabaseHelper(getContext());
        TAG = "SimpleDhtProvider";

        cr = getContext().getContentResolver();
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        if(myPort.equals("11108"))
            myPort = "5554";
        else if(myPort.equals("11112"))
            myPort = "5556";
        else if(myPort.equals("11116"))
            myPort = "5558";
        else if(myPort.equals("11120"))
            myPort = "5560";
        else
            myPort = "5562";
        hm.put("5554","11108");
        hm.put("5556","11112");
        hm.put("5558","11116");
        hm.put("5560","11120");
        hm.put("5562", "11124");

        aliveProc.add(myPort);

        try {
            hashedMyPort = genHash(myPort);
            smallestProcessId = hashedMyPort;
            Log.e(TAG, "onCreate hashed myport " + hashedMyPort);
            //          hashedPrevPort = genHash(prevPort);
            //         hashedNextPort = genHash(nextPort);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        final int SERVER_PORT = 10000;
        //  ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            //  new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            new SendTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "1", myPort, "11108");

        } catch (IOException io) {
            Log.e(TAG, "IO Exception Server Socket");
        }



        try {
            serverSocket.setSoTimeout(1000);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        sUriMatcher.addURI("content://edu.buffalo.cse.cse486586.simpledht.provider", "messages", 1);
        return false;
    }


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        Log.e(TAG, "delete entered");
        int count=0;
        SQLiteDatabase db = dh.getWritableDatabase();
        String mSelectionClause = "key" + " LIKE ?";
        String[] values = new String[]{selection};
        if(selection.equals("@")) {
            count = db.delete( "messages", null , null);
        }
        else if(selection.equals("*"))
        {
            count = db.delete( "messages", null , null);
            new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "5", "*", hm.get(nextPort));
        }
        else
        {
        /*    try {
                selection = genHash(selection);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }*/
            count = db.delete("messages",mSelectionClause, values);
            if(count==0)
                new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "5", selection, hm.get(nextPort));
        }
        db.close();
        return count;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        String TABLE_NAME = "messages";
        SQLiteDatabase db = dh.getWritableDatabase();

        //if(sUriMatcher.match(uri) == 1)
        //{
        //long id =  db.insert(TABLE_NAME,null,values);
        String key = (String) values.get("key");
        String value = (String) values.get("value");

        //values.put("key",key);
        String hashedKey = null;
        try {
            hashedKey = genHash(key);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        //  Log.e(TAG, "sdp insert " + key + " " + value + " " + hashedKey);
        synchronized (lock) {
            // Log.e(TAG, "insert indicator  " + indicator + " " + prevPort + " " + myPort + " " + nextPort);
            if (hashedPrevPort == null)// && hashedKey.compareTo(hashedMyPort)<=0)
                db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            else if ((hashedKey.compareTo(hashedPrevPort) > 0 && hashedKey.compareTo(hashedMyPort) <= 0))// || (indicator == 1))// && nextPort.equals(null)))
                db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            else {
                if(hashedMyPort.equals(smallestProcessId) && (hashedKey.compareTo(hashedPrevPort)>0 || hashedKey.compareTo(hashedMyPort)<0))
                    db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                else {
                    String msgToSend = values.get("key") + " " + values.get("value");
                    new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "0", msgToSend, hm.get(nextPort));
                }
            }

        }
        db.close();
        //}
        Log.v("insert", values.toString());
        return uri;
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // TODO Auto-generated method stub
        Log.e(TAG,"spd query entered");
        SQLiteDatabase db = dh.getReadableDatabase();
        Cursor cursor = null;
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables("messages");
        //builder.appendWhere("key=" + uri.getPathLeafId());
        String []columns = new String[] {"key","value"};
        String condition = "key = ?";
        String[] values = new String[]{selection};
        synchronized (lock) {
            if (selection.equals("*")) {
                matrixCursor = new MatrixCursor(new String[] {"key","value"});
                cursor = builder.query(
                        db,
                        columns,
                        null,
                        null,
                        null,
                        null,
                        sortOrder);

                while (cursor.moveToNext()) {
                    matrixCursor.addRow(new String []{cursor.getString(cursor.getColumnIndex("key")),cursor.getString(cursor.getColumnIndex("value"))});
                }

                //if(aliveProc.size()>1)
                 //   new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", "*/" + myPort, hm.get(nextPort));
            } else if (selection.equals("@")) {
                cursor = builder.query(
                        db,
                        columns,
                        null,
                        null,
                        null,
                        null,
                        sortOrder);
            } else {
         /*   try {
                selection = genHash(selection);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }*/

                //if(sUriMatcher.match(uri) == 1) {
                cursor = builder.query(
                        db,
                        columns,
                        condition,
                        values,
                        null,
                        null,
                        sortOrder);
                //}
            }
            Log.v("query", selection);
        }
    //    db.close();
        //if ((!cursor.moveToFirst() || cursor.getCount() == 0) && !selection.equals("@")) {
        if ((cursor.getCount() == 0 || selection.equals("*")) && !selection.equals("@")  && aliveProc.size()>1) {
            Log.e(TAG,"spd query entered in loop");
            new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", selection + "/" + myPort, hm.get(nextPort));
            while (noOfResponses < aliveProc.size()-1 && queryOpeOver == false) {
            }
          /*  try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                //Handle exception
            }*/
            Log.e(TAG,"spd query noofresponses " + noOfResponses);
            noOfResponses = 0;
            queryOpeOver = false;

        /*    while (matrixCursor.moveToNext()) {
                Log.e(TAG,"spd query matrixcursor " +matrixCursor.getString(matrixCursor.getColumnIndex("key")) + " " + matrixCursor.getString(matrixCursor.getColumnIndex("value")));
            }
*/
            return matrixCursor;
           // db = dh.getReadableDatabase();

         /*   cursor = builder.query(
                    db,
                    columns,
                    condition,
                    values,
                    null,
                    null,
                    sortOrder);*/

         //   db.close();
      /*      synchronized (lock) {
                if(higherStrings!=null) {
                    for (int i = 0; i < higherStrings.length; i++) {
                        String lowerStrings[] = higherStrings[i].split("/");
                        Log.e(TAG,"spd query bfr delete ");
                        cr.delete(uri, lowerStrings[0], null);
                    }
                    higherStrings = null;
                }
            }*/
        }

        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
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


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            Socket s=null;
            String str;
            //  Log.e(TAG, "ServerTask started");

            while(true) {
                try {
                    s = serverSocket.accept();
                    BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    str = br.readLine();
                    // String strings[] = str.split(" ");
                    //if(strings[1].equals("11108"))
                    serverSocket.setSoTimeout(0);
                          Log.e(TAG, "ServerTask  message Received " + str);

                    publishProgress(str);
                }
                catch (SocketTimeoutException s1) {
                    try {
                        serverSocket.setSoTimeout(0);
                    } catch (SocketException e) {
                        e.printStackTrace();
                    }
                    indicator = 1;
                    Log.e(TAG, "ServerTask  timeout ");
                }
                catch(IOException io) {
                    Log.e(TAG, "Can't send on serverSocket");
                }
            }
            //return null;
        }
        @Override
        protected void onProgressUpdate(String... str) {

            String strings[] = str[0].split(" ");
            if (strings[0].equals("0"))
                insert(strings[1] + " " + strings[2]);
            else if(strings[0].equals("1")) {
                synchronized (lock) {
                    if(!strings[1].equals(myPort)) {
                        //   Log.e(TAG, "ServerTask onprogressupdate " +strings[1] );
                        if (myPort.equals("5554")) {

                            String allAliveProc = "";
                            for (int i = 0; i < aliveProc.size(); i++) {
                                allAliveProc = allAliveProc + aliveProc.get(i) + "/";
                            }
                            aliveProc.add(strings[1]);

                            try {
                                String hashedStr = genHash(strings[1]);
                                if(hashedStr.compareTo(smallestProcessId) < 0)
                                    smallestProcessId = hashedStr;
                                if (hashedPrevPort == null) {
                                    prevPort = strings[1];
                                    hashedPrevPort = genHash(prevPort);
                                } else if (hashedStr.compareTo(hashedPrevPort) >= 0 && hashedStr.compareTo(hashedMyPort) < 0) {
                                    prevPort = strings[1];
                                    hashedPrevPort = genHash(prevPort);
                                }
                                if (hashedNextPort == null) {
                                    nextPort = strings[1];
                                    hashedNextPort = genHash(nextPort);
                                } else if (hashedStr.compareTo(hashedNextPort) <= 0 && hashedStr.compareTo(hashedMyPort) > 0) {
                                    nextPort = strings[1];
                                    hashedNextPort = genHash(nextPort);
                                }

                                if(hashedNextPort.compareTo(hashedMyPort) < 0 && (hashedStr.compareTo(hashedNextPort)<0 ||hashedStr.compareTo(hashedMyPort)>0))
                                {
                                    nextPort = strings[1];
                                    hashedNextPort = genHash(nextPort);
                                }
                                if(hashedPrevPort.compareTo(hashedMyPort) > 0 && (hashedStr.compareTo(hashedPrevPort)>0 ||hashedStr.compareTo(hashedMyPort)<0))
                                {
                                    prevPort = strings[1];
                                    hashedPrevPort = genHash(prevPort);
                                }

                            } catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                            }

                            for (int i = 0; i < aliveProc.size(); i++) {
                                String port = aliveProc.get(i);
                                if (!port.equals("5554")) {
                                    if (port.equals(strings[1]))
                                        new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "1", allAliveProc, hm.get(port));
                                    else
                                        new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "1", strings[1],  hm.get(port));
                                }
                            }
                            //   Log.e(TAG, "ServerTask onprogressupdate " + prevPort + " " + myPort + " " + nextPort);
                            //   Log.e(TAG, "ServerTask onprogressupdate " + hashedPrevPort + " " + hashedMyPort + " " + hashedNextPort);

                        } else {
                            //   Log.e(TAG, "ServerTask onprogressupdate all strings " +strings[1] );
                            String allStrings[] = strings[1].split("/");
                            for (int i = 0; i < allStrings.length; i++) {
                                aliveProc.add(allStrings[i]);

                                //   Log.e(TAG, "ServerTask onprogressupdate  separate strings " + allStrings[i]);
                                try {
                                    String hashedStr = genHash(allStrings[i]);
                                    if(hashedStr.compareTo(smallestProcessId) < 0)
                                        smallestProcessId = hashedStr;
                                    if (hashedPrevPort == null) {
                                        prevPort = allStrings[i];
                                        hashedPrevPort = genHash(prevPort);
                                    } else if (hashedStr.compareTo(hashedPrevPort) > 0 && hashedStr.compareTo(hashedMyPort) < 0) {
                                        prevPort = allStrings[i];
                                        hashedPrevPort = genHash(prevPort);
                                    }
                                    if (hashedNextPort == null) {
                                        nextPort = allStrings[i];
                                        hashedNextPort = genHash(nextPort);
                                    } else if (hashedStr.compareTo(hashedNextPort) < 0 && hashedStr.compareTo(hashedMyPort) > 0) {
                                        nextPort = allStrings[i];
                                        hashedNextPort = genHash(nextPort);
                                    }

                                    if(hashedNextPort.compareTo(hashedMyPort) < 0 && (hashedStr.compareTo(hashedNextPort)<0 ||hashedStr.compareTo(hashedMyPort)>0))
                                    {
                                        nextPort = allStrings[i];
                                        hashedNextPort = genHash(nextPort);
                                    }
                                    if(hashedPrevPort.compareTo(hashedMyPort) > 0 && (hashedStr.compareTo(hashedPrevPort)>0 ||hashedStr.compareTo(hashedMyPort)<0))
                                    {
                                        prevPort = allStrings[i];
                                        hashedPrevPort = genHash(prevPort);
                                    }

                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                }
                                // Log.e(TAG, "ServerTask onprogressupdate " + prevPort + " " + myPort + " " + nextPort);
                                // Log.e(TAG, "ServerTask onprogressupdate " + hashedPrevPort + " " + hashedMyPort + " " + hashedNextPort);
                            }
                        }
                    }
                }
            }
            else if(strings[0].equals("2"))
            {
                String allStrings[] = strings[1].split("/");

                SQLiteDatabase db = dh.getReadableDatabase();
                SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables("messages");
                //builder.appendWhere("key=" + uri.getPathLeafId());
                String []columns = new String[] {"key","value"};
                String condition = "key = ?";
                String[] values = new String[]{allStrings[0]};
                Cursor queryCursor;
                if(allStrings[0].equals("*")) {
                    queryCursor = builder.query(
                            db,
                            columns,
                            null,
                            null,
                            null,
                            null,
                            null);
                }
                else {
                    queryCursor = builder.query(
                            db,
                            columns,
                            condition,
                            values,
                            null,
                            null,
                            null);
                }


               // if(!queryCursor.moveToFirst() || queryCursor.getCount() == 0) {

                    if (queryCursor.getCount() == 0) {
                        if(allStrings[0].equals("*")) {
                            new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "4", "null123", hm.get(allStrings[1]));
                        }
                        else
                        new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "3", "null123", hm.get(allStrings[1]));
                        Log.e(TAG, "ServerTask 2 forward " + strings[1] + " " + nextPort);
                        if (!nextPort.equals(allStrings[1]))
                            new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", strings[1], hm.get(nextPort));
                    } else {
                        String msgToSend = "";
                        while (queryCursor.moveToNext()) {
                            msgToSend = msgToSend + queryCursor.getString(queryCursor.getColumnIndex("key")) + "/";
                            msgToSend = msgToSend + queryCursor.getString(queryCursor.getColumnIndex("value")) + "_";
                            Log.e(TAG, "ServerTask 2 query result not null " + queryCursor.getString(queryCursor.getColumnIndex("key")) + " " + queryCursor.getString(queryCursor.getColumnIndex("value")));
                        }
                        if(allStrings[0].equals("*")) {
                            if (!nextPort.equals(allStrings[1]))
                            new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", strings[1], hm.get(nextPort));
                            new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "4", msgToSend, hm.get(allStrings[1]));
                        }
                        else {
                            new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "3", msgToSend, hm.get(allStrings[1]));
                        }
                    }

            }
            else if(strings[0].equals("3"))
            {
                Log.e(TAG, "ServerTask 3 query received entered");
                matrixCursor = new MatrixCursor(new String[] {"key","value"});
                if(!strings[1].equals("null123"))
                {
                    String higherStrings[] = null;
                    Log.e(TAG, "ServerTask 3 query received non zero");

                    synchronized (lock) {
                        higherStrings = strings[1].split("_");
                        for(int i=0;i<higherStrings.length;i++)
                        {
                            String lowerStrings[] = higherStrings[i].split("/");
                            if (lowerStrings[1].endsWith("_")) {
                                lowerStrings[1] = lowerStrings[1].substring(0, lowerStrings[1].length() - 1);
                            }
                         //   Log.e(TAG, "ServerTask 3 query received data entered " + lowerStrings[0]+ " " + lowerStrings[1]);
                          //  String TABLE_NAME = "messages";

                            matrixCursor.addRow(new String []{  lowerStrings[0],lowerStrings[1]});
                            //    SQLiteDatabase db = dh.getWritableDatabase();
                        //    ContentValues cv = new ContentValues();
                       //     cv.put("key", lowerStrings[0]);
                      //      cv.put("value", lowerStrings[1]);
                      //      db.insertWithOnConflict(TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                     //       db.close();
                        }

                    }
                    queryOpeOver = true;
                }
                noOfResponses++;
                Log.e(TAG, "ServerTask 3 query received ends "+ noOfResponses + " " + queryOpeOver );
            }
            else if(strings[0].equals("4"))
            {
                if(!strings[1].equals("null123"))
                {
                    String higherStrings[] = null;
                    synchronized (lock) {
                        higherStrings = strings[1].split("_");
                        for(int i=0;i<higherStrings.length;i++)
                        {
                            Log.e(TAG, "ServerTask 4 higherstrings " + higherStrings[i]);
                            String lowerStrings[] = higherStrings[i].split("/");
                            Log.e(TAG, "ServerTask 4 lowerstrings " + lowerStrings[0] + " " + lowerStrings[1]);
                            if (lowerStrings[1].endsWith("_")) {
                                lowerStrings[1] = lowerStrings[1].substring(0, lowerStrings[1].length() - 1);
                            }

                            matrixCursor.addRow(new String []{  lowerStrings[0],lowerStrings[1]});
                        }

                    }
                }
                noOfResponses++;
            }
            else if(strings[0].equals("5"))
            {
                SQLiteDatabase db = dh.getWritableDatabase();
                if(strings[1].equals("*")) {
                    int count = db.delete("messages", null, null);
                    new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "5", "*", hm.get(nextPort));
                }
                else {
                    String mSelectionClause = "key" + " LIKE ?";
                    String[] values = new String[]{strings[1]};
                    int count = db.delete("messages",mSelectionClause, values);
                    if(count==0)
                        new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "5", strings[1], hm.get(nextPort));
                }

            }
            return;
        }
    }
    public void insert(String str)
    {
        String strings[] = str.split(" ");
        Uri uri = Uri.parse("content://edu.buffalo.cse.cse486586.simpledht.provider/messages");
        ContentValues cv = new ContentValues();
        cv.put("key", strings[0]);
        cv.put("value", strings[1]);


        cr.insert(uri, cv);

    }

    private class SendTask extends AsyncTask<String, String, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            try {
                      Log.e(TAG, "SendTask Entered " + msgs[0] + " "+ msgs[1] + " " + msgs[2] );
                Socket socket;
                String msgToSend = msgs[0] + " " + msgs[1];

                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(msgs[2]));

                OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());

                out.write(msgToSend);
                out.flush();
                out.close();
                socket.close();
                if(globalIndicator==0)
                {
                    globalIndicator++;
                    // Log.e(TAG,"SendTask bfr onprogress ");
                    publishProgress("fgf");
                }

            } catch (IOException e) {
                Log.e(TAG, "SendTask use set_redir");
             /*   String TABLE_NAME = "messages";
                SQLiteDatabase db = dh.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put("key", msgs[0]);
                cv.put("value", msgs[1]);
                db.insertWithOnConflict(TABLE_NAME,null,cv,SQLiteDatabase.CONFLICT_REPLACE);
                Log.e(TAG, "ClientTask socket IOException " + "from here " + msgs[0] + " " + nextPort);*/
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String ...str) {
            //       Log.e(TAG,"SendTask onprogress entered");
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }
    }
}

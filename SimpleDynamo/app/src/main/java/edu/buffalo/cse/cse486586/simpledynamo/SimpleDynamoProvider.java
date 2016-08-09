package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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
import java.util.Iterator;
import java.util.Map;

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

public class SimpleDynamoProvider extends ContentProvider {

	private final Object lock = new Object();
	private final String recoverLock = new String();
	private final Object sendTaskQueryLock = new Object();
	private static UriMatcher sUriMatcher  = new UriMatcher(UriMatcher.NO_MATCH);;
	DatabaseHelper dh = null;
	String TAG;
	String myPort = null;
	String prevPort = null;
	String nextPort = null;
	String hashedMyPort = null;
	String hashedPrevPort = null;
	String hashedNextPort = null;
	int ring[];
	String hashedRing[];
	Uri uri = Uri.parse("content://edu.buffalo.cse.cse486586.simpledynamo.provider/messages");
	ArrayList<String> aliveProc = new ArrayList<String>();
	String smallestProcessId;
	int indicator = 0;
	ServerSocket serverSocket = null;
	HashMap<String,String> hm = new HashMap<String,String>();
	int globalIndicator = 0;
	int noOfResponses = 0;
	boolean queryOpeOver = false;
	HashMap<String,String> vals = new HashMap<String,String>();
	HashMap<String,Integer> versions = new HashMap<String,Integer>();
	HashMap<String,String> recoverVals = new HashMap<String,String>();
	HashMap<String,Integer> recoverVersions = new HashMap<String,Integer>();
	HashMap<String,Integer> times = new HashMap<String,Integer>();
	String keyReceived = "null";
	ContentResolver cr ;
	boolean gotMyContents = false;
	boolean loading = true;
	MatrixCursor matrixCursor = new MatrixCursor(new String[] {"key","value"});

	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub
		dh = new DatabaseHelper(getContext());
		TAG = "SimpleDynamoProvider";

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
		//aliveProc.add(myPort);

		sUriMatcher.addURI("content://edu.buffalo.cse.cse486586.simpledynamo.provider", "messages", 1);

		ring = new int[]{5562, 5556, 5554, 5558, 5560};
		hashedRing = new String[ring.length];
		int index = -2;
		try {
			for(int i=0;i<ring.length;i++) {
				aliveProc.add(hm.get(String.valueOf(ring[i])));
				if(ring[i] == Integer.parseInt(myPort)) {
					index = i;
					if(i==0)
						prevPort = String.valueOf(ring[ring.length-1]);
					else
						prevPort = String.valueOf(ring[i-1]);
					if(i==ring.length-1)
						nextPort = String.valueOf(ring[0]);
					else
						nextPort = String.valueOf(ring[i+1]);
				}
				hashedRing[i] = genHash(String.valueOf(ring[i]));
			}
			hashedPrevPort = genHash(prevPort);
			hashedNextPort = genHash(nextPort);
			hashedMyPort = genHash(myPort);
			smallestProcessId = hashedMyPort;
			Log.e(TAG, "onCreate hashed myport " + hashedMyPort);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		Log.e(TAG, "Ports " + prevPort + " " + myPort + " " + nextPort);
		final int SERVER_PORT = 10000;

		try {
			serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
			//new ConcurrentOpe().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "initial");
			//new SendTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "1", myPort, "11108");

		} catch (IOException io) {
			Log.e(TAG, "IO Exception Server Socket");
		}

		synchronized (lock) {
			noOfResponses = 0;
		}
		//new loadMessages().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,"load");
		new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "3", prevPort + " " + myPort, hm.get(nextPort));
		new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "4", myPort, hm.get(prevPort));
		if(prevPort.equals(String.valueOf(ring[0])))
			new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "4", myPort, hm.get(String.valueOf(ring[ring.length-1])));
		else if(myPort.equals(String.valueOf(ring[0])))
			new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "4", myPort, hm.get(String.valueOf(ring[ring.length-2])));
		else
			new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "4", myPort, hm.get(String.valueOf(ring[index-2])));


		for(int i=0;i<ring.length;i++) {
			if(!myPort.equals(String.valueOf(ring[i])))
				new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "1",hm.get(myPort), hm.get(String.valueOf(ring[i])));
		}

		return true;
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
		SQLiteDatabase db1 = dh.getReadableDatabase();

		String[] columnsAll = new String[]{"key", "value","version"};
		String condition = "key = ?";
		SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
		builder.setTables("messages");
		Cursor cursor = null;

		String key = (String) values.get("key");
		String value = (String) values.get("value");


		String[] values1 = new String[]{key};

		String strings[] = key.split("/");
		if(strings.length == 2) {
			ContentValues cv = new ContentValues();
			if(strings[0].equals("inserthere")) {
				Log.e(TAG, "inserted here 2");

				values1 = new String[]{strings[1]};
				cursor = builder.query(db1, columnsAll, condition, values1, null, null, null);
				//while (cursor.moveToNext()) {
				int temp = -1;
				if (cursor != null) {
					while (cursor.moveToNext())
					temp = cursor.getInt(cursor.getColumnIndex("version"));
				}
				//}

				cv.put("key", strings[1]);
				cv.put("value", value);
				cv.put("version", temp + 1);
				db.insertWithOnConflict(TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
			}
			else {
				values1 = new String[]{strings[1]};
				cursor = builder.query(db1, columnsAll, condition, values1, null, null, null);
				cv.put("key", strings[1]);
				cv.put("value", value);
				int version = (Integer) values.get("version");
				cv.put("version", version);

				if (cursor != null) {
					int temp=-1;
					while (cursor.moveToNext())
					temp = cursor.getInt(cursor.getColumnIndex("version"));
					if(version>temp)
						db.insertWithOnConflict(TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
				}
				else {
					db.insertWithOnConflict(TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
				}

			}
		}
		else {
			String msgToSend = key + " " + value;
			String hashedKey = null;
			try {
				hashedKey = genHash(key);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}

			if (hashedKey.compareTo(hashedPrevPort) > 0 && hashedKey.compareTo(hashedMyPort) <= 0) {
				int i = 0;
				for (i = 0; i < ring.length; i++) {
					if (ring[i] == Integer.parseInt(myPort))
						break;
				}

				cursor = builder.query(db1, columnsAll, condition, values1, null, null, null);
				int temp=-1;
				if(cursor!=null) {
					while (cursor.moveToNext())
					temp = cursor.getInt(cursor.getColumnIndex("version"));
				}
				ContentValues cv = new ContentValues();
				cv.put("key", key);
				cv.put("value", value);
				cv.put("version", temp + 1);
				//values.put("version", temp+1);
			//	msgToSend = key + " " + value + " " + String.valueOf(temp+1);
				db.insertWithOnConflict(TABLE_NAME, null, cv , SQLiteDatabase.CONFLICT_REPLACE);
				//new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "0", msgToSend, hm.get(String.valueOf(ring[i%5])));
			//	if (aliveProc.contains(hm.get(String.valueOf(ring[(i + 1) % 5]))))
			//	if(!String.valueOf(ring[(i + 1) % 5]).equals(myPort))
				new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "0", msgToSend + " " + myPort, hm.get(String.valueOf(ring[(i + 1) % 5])));
			//	if (aliveProc.contains(hm.get(String.valueOf(ring[(i + 2) % 5]))))
			//	if(!String.valueOf(ring[(i + 2) % 5]).equals(myPort))
				new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "0", msgToSend + " " + myPort, hm.get(String.valueOf(ring[(i + 2) % 5])));
			}
			else if (hashedKey.compareTo(hashedRing[0]) <= 0 || hashedKey.compareTo(hashedRing[4]) > 0) {
			//	if (aliveProc.contains(hm.get(String.valueOf(ring[0]))))
			//	if(!String.valueOf(ring[0]).equals(myPort))
				new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "0", msgToSend + " " + myPort, hm.get(String.valueOf(ring[0])));
			//	if (aliveProc.contains(hm.get(String.valueOf(ring[1]))))
			//	if(!String.valueOf(ring[1]).equals(myPort))
				new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "0", msgToSend + " " + myPort, hm.get(String.valueOf(ring[1])));
			//	if (aliveProc.contains(hm.get(String.valueOf(ring[2]))))
			//	if(!String.valueOf(ring[2]).equals(myPort))
				new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "0", msgToSend + " " + myPort, hm.get(String.valueOf(ring[2])));
			} else {
				for (int i = 1; i <= 4; i++) {
					if (hashedKey.compareTo(hashedRing[i - 1]) > 0 && hashedKey.compareTo(hashedRing[i]) <= 0) {
			//			if (aliveProc.contains(hm.get(String.valueOf(ring[i % 5]))))
			//			if(!String.valueOf(ring[i % 5]).equals(myPort))
						new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "0", msgToSend + " " + myPort, hm.get(String.valueOf(ring[i % 5])));
			//			if (aliveProc.contains(hm.get(String.valueOf(ring[(i + 1) % 5]))))
			//			if(!String.valueOf(ring[(i + 1) % 5]).equals(myPort))
						new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "0", msgToSend + " " + myPort, hm.get(String.valueOf(ring[(i + 1) % 5])));
			//			if (aliveProc.contains(hm.get(String.valueOf(ring[(i + 2) % 5]))))
			//			if(!String.valueOf(ring[(i + 2) % 5]).equals(myPort))
						new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "0", msgToSend + " " + myPort, hm.get(String.valueOf(ring[(i + 2) % 5])));
						break;
					}
				}
			}
		}
		//db.close();
		Log.v("insert", values.toString());
		return uri;
	}


	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
						String sortOrder) {
		// TODO Auto-generated method stub
		Log.e(TAG, "spd query entered");
		SQLiteDatabase db = dh.getReadableDatabase();
		Cursor cursor = null;
		SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
		builder.setTables("messages");
		//builder.appendWhere("key=" + uri.getPathLeafId());
		String[] columnsAll = new String[]{"key", "value","version"};
		String[] columns = new String[]{"key", "value"};
		String condition = "key = ?";

		String strings[] = selection.split("/");
		if (strings.length == 2) {
			String[] values = new String[]{strings[1]};
			if (strings[1].equals("*")) {
				cursor = builder.query(db, columnsAll, null, null, null, null, sortOrder);
			} else {
				cursor = builder.query(db, columnsAll, condition, values, null, null, sortOrder);
			}
			return cursor;
		}
		else if(selection.equals("@")) {
			cursor = builder.query(db, columns, null, null, null, null, sortOrder);
			return cursor;
		}
		else if(selection.equals("*")) {
			/*matrixCursor = new MatrixCursor(new String[]{"key", "value"});
			cursor = builder.query(db, columns, null, null, null, null, sortOrder);
			while (cursor.moveToNext()) {
				synchronized (lock) {
					matrixCursor.addRow(new String[]{cursor.getString(cursor.getColumnIndex("key")), cursor.getString(cursor.getColumnIndex("value"))});
				}
			}
			new SendTaskQueryAll().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "1", selection + "/" + myPort, hm.get(prevPort));
			while (noOfResponses < aliveProc.size() - 1) {
			}
			synchronized (lock) {
				noOfResponses = 0;
			}*/
			vals = new HashMap<String,String>();
			versions = new HashMap<String,Integer>();
			times = new HashMap<String,Integer>();
			synchronized (lock) {
				noOfResponses = 0;
			}
			Log.e(TAG, "query * " + noOfResponses);
			for(int i=0;i<ring.length;i++) {
				if(!String.valueOf(ring[i]).equals(myPort)) {
					//if (aliveProc.contains(hm.get(String.valueOf(ring[i]))))
						new SendTaskQueryAll().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", selection + "/" + myPort, hm.get(String.valueOf(ring[i])));
				}
			}
			while (noOfResponses < aliveProc.size() - 1) {
			}
			Log.e(TAG, "query * " + noOfResponses);
			synchronized (lock) {
				noOfResponses = 0;
			}

			synchronized (lock) {
				matrixCursor = new MatrixCursor(new String[]{"key", "value"});
				cursor = builder.query(db, columns, null, null, null, null, sortOrder);
				while (cursor.moveToNext()) {
						matrixCursor.addRow(new String[]{cursor.getString(cursor.getColumnIndex("key")), cursor.getString(cursor.getColumnIndex("value"))});
				}
				cursor.close();
				Iterator it = vals.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry pair = (Map.Entry) it.next();
					String tempKey = (String) pair.getKey();
					String tempValue = (String) pair.getValue();
					Log.e(TAG, "matrixcursor values " + tempKey + " " + tempValue);
					matrixCursor.addRow(new String[]{tempKey, tempValue});
				}
				vals = new HashMap<String,String>();
				versions = new HashMap<String,Integer>();
				times = new HashMap<String,Integer>();
			}
			return matrixCursor;
		}
		else {
			vals.remove(selection);
			versions.remove(selection);
			times.remove(selection);

			String[] values = new String[]{selection};
			String hashedKey = null;
			int temp = 0;
			try {
				hashedKey = genHash(selection);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			//synchronized (lock) {
			synchronized (lock) {
				//vals = new HashMap<String, String>();
				//versions = new HashMap<String, Integer>();
			}
		/*	synchronized (lock) {
				noOfResponses = 0;
			}*/
			if (hashedKey.compareTo(hashedPrevPort) > 0 && hashedKey.compareTo(hashedMyPort) <= 0) {

				cursor = builder.query(db, columnsAll, condition, values, null, null, sortOrder);


				while (cursor.moveToNext()) {
					String tempKey = cursor.getString(cursor.getColumnIndex("key"));
					String tempValue = cursor.getString(cursor.getColumnIndex("value"));
					Integer tempVersion = cursor.getInt(cursor.getColumnIndex("version"));
					synchronized (lock) {
						if (vals.get(tempKey) != null) {
							if (tempVersion > versions.get(tempKey)) {
								Log.e(TAG, "vars added " + tempKey + " " + tempValue);
								vals.put(tempKey, tempValue);
								versions.put(tempKey, tempVersion);
							}
							//int number = times.get(tempKey);
						//	times.put(tempKey, number + 1);
						} else {
							Log.e(TAG, "vars added " + tempKey + " " + tempValue);
							vals.put(tempKey, tempValue);
							versions.put(tempKey, tempVersion);
						//	times.put(tempKey, 1);
						}
						times.put(tempKey, 1);
					}
				}

				cursor.close();
				int i = 0;
				for (i = 0; i < ring.length; i++) {
					if (ring[i] == Integer.parseInt(myPort))
						break;
				}
		/*		synchronized (lock) {
					noOfResponses++;
				}*/
				//	if(aliveProc.contains(hm.get(String.valueOf(ring[(i + 1) % 5])))) {
				new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", selection + "/" + myPort, hm.get(String.valueOf(ring[(i + 1) % 5])));// ++temp;}
				//	if(aliveProc.contains(hm.get(String.valueOf(ring[(i + 2) % 5])))) {
				new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", selection + "/" + myPort, hm.get(String.valueOf(ring[(i + 2) % 5])));// ++temp;}
				while (times.get(selection) < 3) {
				}

			} else if (hashedKey.compareTo(hashedRing[0]) <= 0 || hashedKey.compareTo(hashedRing[4]) > 0) {

					times.put(selection, 0);
					//	if(aliveProc.contains(hm.get(String.valueOf(ring[0])))) {
					//	if(!String.valueOf(ring[0]).equals(myPort))
					new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", selection + "/" + myPort, hm.get(String.valueOf(ring[0]))); //++temp;}
					//	if(aliveProc.contains(hm.get(String.valueOf(ring[1])))) {
					//	if(!String.valueOf(ring[1]).equals(myPort))
					new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", selection + "/" + myPort, hm.get(String.valueOf(ring[1])));// ++temp;}
					//	if(aliveProc.contains(hm.get(String.valueOf(ring[2])))) {
					//	if(!String.valueOf(ring[2]).equals(myPort))
					new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", selection + "/" + myPort, hm.get(String.valueOf(ring[2])));// ++temp;}
					while (times.get(selection) < 3) {
					}

			} else {
				for (int i = 1; i <= 4; i++) {
					if (hashedKey.compareTo(hashedRing[i - 1]) > 0 && hashedKey.compareTo(hashedRing[i]) <= 0) {

							times.put(selection, 0);
							//			if(aliveProc.contains(hm.get(String.valueOf(ring[i%5])))) {
							//			if(!String.valueOf(ring[i % 5]).equals(myPort))
							new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", selection + "/" + myPort, hm.get(String.valueOf(ring[i % 5]))); //++temp;}
							//			if(aliveProc.contains(hm.get(String.valueOf(ring[(i+1)%5])))) {
							//			if(!String.valueOf(ring[(i + 1) % 5]).equals(myPort))
							new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", selection + "/" + myPort, hm.get(String.valueOf(ring[(i + 1) % 5])));// ++temp;}
							//			if(aliveProc.contains(hm.get(String.valueOf(ring[(i+2)%5])))) {
							//			if(!String.valueOf(ring[(i + 2) % 5]).equals(myPort))
							new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "2", selection + "/" + myPort, hm.get(String.valueOf(ring[(i + 2) % 5])));// ++temp;}
							while (times.get(selection) < 3) {
							}

						break;
					}
				}
			}

			//while(noOfResponses<3) {
			//}
			//while(vals.get(selection)==null) {
			//}
			synchronized (lock) {
				matrixCursor = new MatrixCursor(new String[]{"key", "value"});
				//Iterator it = vals.entrySet().iterator();
				//while (it.hasNext()) {
				//	Map.Entry pair = (Map.Entry)it.next();
				//	String tempKey= (String) pair.getKey();
				//	String tempValue = (String) pair.getValue();
					String tempKey= selection;
					String tempValue = vals.get(selection);
				//	if(selection.equals(tempKey)) { //&& version >
						Log.e(TAG, "matrixcursor values " + tempKey + " " + tempValue);
						matrixCursor.addRow(new String[]{tempKey, tempValue});
				vals.remove(selection);
				versions.remove(selection);
				times.remove(selection);
				//		break;
				//	}
				//}
				return matrixCursor;
			}
		}
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		Log.e(TAG, "delete entered");
		int count=0;
		SQLiteDatabase db = dh.getWritableDatabase();
		String mSelectionClause = "key" + " LIKE ?";
		String strings[] = selection.split("/");
		if(strings.length==2) {
			String[] values = new String[]{strings[1]};
			if (strings[1].equals("*"))
				count = db.delete("messages", null, null);
			else
				count = db.delete("messages", mSelectionClause, values);
			return count;
		}
		else {
			String[] values = new String[]{selection};
			if (selection.equals("@")) {
				count = db.delete("messages", null, null);
			} else if (selection.equals("*")) {
				count = db.delete("messages", null, null);
				for (int i = 0; i < ring.length; i++) {
					if(!myPort.equals(String.valueOf(ring[i]))) {
					//	if(aliveProc.contains(hm.get(String.valueOf(ring[i]))))
						new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "5", selection, hm.get(String.valueOf(ring[i])));
					}
				}
			} else {
				count = db.delete("messages", mSelectionClause, values);
				for (int i = 0; i < ring.length; i++) {
					if (!myPort.equals(String.valueOf(ring[i]))) {
					//	if(aliveProc.contains(hm.get(String.valueOf(ring[i]))))
						new SendTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "5", selection, hm.get(String.valueOf(ring[i])));
					}
				}
			}
			//db.close();
			return count;
		}
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
	/*		while(noOfResponses<3) {
			}
			Log.e(TAG, "onCreate noOfResponses " + noOfResponses);
			synchronized (lock) {
				noOfResponses = 0;
				//}
				//synchronized (lock) {
				Iterator it = recoverVals.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry pair = (Map.Entry) it.next();
					String tempKey = (String) pair.getKey();
					String tempValue = (String) pair.getValue();
					Log.e(TAG, "inserted here 1");
					insert1(tempKey + " " + tempValue + " " + String.valueOf(recoverVersions.get(tempKey)));
				}
				//}

				//synchronized (lock) {
				//	vals = new HashMap<String, String>();
				//	versions = new HashMap<String, Integer>();
					//times = new HashMap<String, Integer>();
			}
*/

			Socket s=null;
			String str = new String();
			//  Log.e(TAG, "ServerTask started");

			while(true) {
				try {
					s = serverSocket.accept();

					BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
					OutputStreamWriter out = new OutputStreamWriter(s.getOutputStream());

					str = br.readLine();


					Log.e(TAG, "ServerTask  message Received " + str);

					String strings[] = str.split(" ");
					if(strings[0].equals("0")) {   //insert request
				//		synchronized (recoverLock) {
							Log.e(TAG, "inserting " + strings[1] + " " + strings[2]);
							insert(strings[1] + " " + strings[2]);
				//		}
						Log.e(TAG, "serverTask sending");
						out.write("writeReqAck\n");
						out.flush();
					}
					else if(strings[0].equals("2")) {   //query request
						String allStrings[] = strings[1].split("/");
						ContentResolver cr = getContext().getContentResolver();
						Cursor queryCursor = cr.query(uri, null, "queryhere/" + allStrings[0], null, null);
						if (queryCursor.getCount() == 0) {
							out.write("nullQueryResult\n");
							out.flush();
						}
						else {
							String msgToSend = "";
							while (queryCursor.moveToNext()) {
								msgToSend = msgToSend + queryCursor.getString(queryCursor.getColumnIndex("key")) + "/";
								msgToSend = msgToSend + queryCursor.getString(queryCursor.getColumnIndex("value")) + "/";
								msgToSend = msgToSend + String.valueOf(queryCursor.getInt(queryCursor.getColumnIndex("version"))) + "_";
								//Log.e(TAG, "ServerTask 2 query result not null " + queryCursor.getString(queryCursor.getColumnIndex("key")) + " " + queryCursor.getString(queryCursor.getColumnIndex("value")));
							}
							out.write(msgToSend+"\n");
							out.flush();
						}
						queryCursor.close();
					}
					else if(strings[0].equals("5")) {   //delete request
						ContentResolver cr = getContext().getContentResolver();
						cr.delete(uri,"deletehere/"+strings[1],null);
						out.write("deleteDone\n");
						out.flush();
					}
					else if(strings[0].equals("1")) {  //aliveproc add request
						synchronized (lock) {
							if (!aliveProc.contains(strings[1]))
								aliveProc.add(strings[1]);
						}
						out.write("aliveProcAdded\n");
						out.flush();
					}
					else if(strings[0].equals("3") || strings[0].equals("4")) { //giving data back
						ContentResolver cr = getContext().getContentResolver();
						Cursor queryCursor = cr.query(uri, null, "queryhere/*", null, null);
						String node1 = new String();
						String node2 = new String();
						try {
						if(strings[0].equals("3")) {
							node1 = genHash(strings[1]);
							node2 = genHash(strings[2]);
						}
						else {
							node1 = hashedPrevPort;
							node2 = hashedMyPort;
						}
						} catch (NoSuchAlgorithmException e) {
							e.printStackTrace();
						}
						if (queryCursor.getCount() == 0) {
							out.write("nullQueryResult\n");
							out.flush();
						}
						else {
							String msgToSend = "";
							while (queryCursor.moveToNext()) {
								String tempKey = queryCursor.getString(queryCursor.getColumnIndex("key"));
								String hashedTempKey = new String();

								boolean get = false;
								try {
									hashedTempKey = genHash(tempKey);

								if(node2.equals(genHash(String.valueOf(ring[0])))) {
									if(hashedTempKey.compareTo(node1)>0 || hashedTempKey.compareTo(node2)<=0)
										get = true;
								}
								else {
									if(hashedTempKey.compareTo(node1)>0 && hashedTempKey.compareTo(node2)<=0)
										get = true;
								}
								} catch (NoSuchAlgorithmException e) {
									e.printStackTrace();
								}
								if(get == true) {
									msgToSend = msgToSend + tempKey + "/";
									msgToSend = msgToSend + queryCursor.getString(queryCursor.getColumnIndex("value")) + "/";
									msgToSend = msgToSend + String.valueOf(queryCursor.getInt(queryCursor.getColumnIndex("version"))) + "_";
									//Log.e(TAG, "ServerTask 2 query result not null " + queryCursor.getString(queryCursor.getColumnIndex("key")) + " " + queryCursor.getString(queryCursor.getColumnIndex("value")));
								}
							}
							Log.e(TAG, "ServerTask 3 output " + msgToSend);
							if(msgToSend.equals(""))
								out.write("nullQueryResult\n");
							else
								out.write(msgToSend+"\n");
							out.flush();
						}
						queryCursor.close();
					}
					br.close();
					out.close();
				//	publishProgress(str);
				}

				catch(IOException io) {
					Log.e(TAG, "Can't send on serverSocket");
				}
			}
			//return null;
		}
		@Override
		protected void onProgressUpdate(String... str) {

		//	String strings[] = str[0].split(" ");

		//		new ConcurrentOpe().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, str[0]);
			return;
		}
	}
	public void insert(String str)
	{
		String strings[] = str.split(" ");
		Uri uri = Uri.parse("content://edu.buffalo.cse.cse486586.simpledynamo.provider/messages");
		ContentValues cv = new ContentValues();
		try {
			Log.e(TAG,"insert belongs here " + strings[0] + " " + genHash(strings[0]));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		cv.put("key", "inserthere/"+ strings[0]);
		cv.put("value", strings[1]);
		//synchronized (recoverLock) {
			cr.insert(uri, cv);
//		}

	}

	public void insert1(String str)
	{
		String strings[] = str.split(" ");
		Uri uri = Uri.parse("content://edu.buffalo.cse.cse486586.simpledynamo.provider/messages");
		ContentValues cv = new ContentValues();
		try {
			Log.e(TAG,"insert belongs here " + strings[0] + " " + genHash(strings[0]));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		cv.put("key", "inserthere1/"+ strings[0]);
		cv.put("value", strings[1]);
		cv.put("version", Integer.parseInt(strings[2]));
		//synchronized (recoverLock) {
			cr.insert(uri, cv);
		//}
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
				BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());

				out.write(msgToSend+"\n");
				out.flush();


				socket.setSoTimeout(1500);
				String str = new String();
				str = br.readLine();
				Log.e(TAG, "SendTask received " + str);

				if(str!=null) {
					if (!str.equals("writeReqAck") && !str.equals("nullQueryResult") && !str.equals("deleteDone") && !str.equals("aliveProcAdded")) {
						synchronized (lock) {
							String higherStrings[] = str.split("_");
							for (int i = 0; i < higherStrings.length; i++) {
								String lowerStrings[] = higherStrings[i].split("/");
								if (lowerStrings[2].endsWith("_"))
									lowerStrings[2] = lowerStrings[2].substring(0, lowerStrings[2].length() - 1);
								String tempKey = lowerStrings[0];
								String tempValue = lowerStrings[1];
								Integer tempVersion = Integer.parseInt(lowerStrings[2]);
								//	synchronized (lock) {
								if(msgs[0].equals("2")) {
									if (vals.get(tempKey) != null) {
										if (tempVersion > versions.get(tempKey)) {
											Log.e(TAG, "vars added " + tempKey + " " + tempValue);
											vals.put(tempKey, tempValue);
											versions.put(tempKey, tempVersion);
										}

									} else {
										Log.e(TAG, "vars added " + tempKey + " " + tempValue);
										vals.put(tempKey, tempValue);
										versions.put(tempKey, tempVersion);
										//times.put(tempKey,1);
									}
								}
								else if(msgs[0].equals("3") || msgs[0].equals("4")) {
									if (recoverVals.get(tempKey) != null) {
										if (tempVersion > recoverVersions.get(tempKey)) {
											Log.e(TAG, "vars added " + tempKey + " " + tempValue);
											recoverVals.put(tempKey, tempValue);
											recoverVersions.put(tempKey, tempVersion);
										}

									} else {
										Log.e(TAG, "vars added " + tempKey + " " + tempValue);
										recoverVals.put(tempKey, tempValue);
										recoverVersions.put(tempKey, tempVersion);
										//times.put(tempKey,1);
									}
								}
									if(msgs[0].equals("2")) {
										int number = times.get(tempKey);
										times.put(tempKey, number + 1);
									}
							}
						}
						//}
					}
				}

				String strings[] = strings = msgs[1].split("/");
				if(str==null || str.equals("nullQueryResult")) {
					if(msgs[0].equals("2")) {
						Log.e(TAG, "SendTask received 2 " + msgs[0] + " " + strings[0]);
			//			if (times.get(strings[0]) != null) {
							int number = times.get(strings[0]);
							times.put(strings[0],number+1);
			//			}
			//			else
			//				times.put(strings[0],1);

					}
				}
				if(msgs[0].equals("3") || msgs[0].equals("4")) {// ||  msgs[0].equals("2")) {
					synchronized (lock) {
						noOfResponses++;
					}
					Log.e(TAG, "noOfResponses " + noOfResponses);
				}

				Log.e(TAG, "SendTask received 3 " + times.get(strings[0]));
		//		gotMyContents = true;
				out.close();
				br.close();

				socket.close();

			}
			catch(SocketTimeoutException ste) {
				aliveProc.remove(msgs[2]);
				Log.e(TAG, "SendTask Timeout " + msgs[0] + " "+ msgs[1]);
				if(msgs[0].equals("3") || msgs[0].equals("4")) {// || msgs[0].equals("2")) {
					synchronized (lock) {
						noOfResponses++;
					}

					Log.e(TAG, "noOfResponses " + noOfResponses);
				}
					if(msgs[0].equals("2")) {
						String strings[] = msgs[1].split("/");
					//	if (times.get(strings[0]) != null) {
							int number = times.get(strings[0]);
							times.put(strings[0],number+1);
					//	}
					//	else
					//		times.put(strings[0],1);
						Log.e(TAG, "SendTask Timeout 2" + times.get(strings[0]));
					}


			//	gotMyContents = true;
			}
			catch (IOException e) {
				Log.e(TAG, "SendTask use set_redir");
			}

			if(loading == true) {
				loading = false;
				publishProgress("load");
			}

			return null;
		}

		@Override
		protected void onProgressUpdate(String ...str) {
			//       Log.e(TAG,"SendTask onprogress entered");
			new loadMessages().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,"load");
			//new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
		}
	}

/*	private class ConcurrentOpe extends AsyncTask<String, String, Void> {
		@Override
		protected Void doInBackground(String... msgs) {
		//	if(!msgs[0].equals("initial"))
				publishProgress(msgs[0]);
		//	else {

		//	}
			return null;
		}
		@Override
		protected void onProgressUpdate(String ...str) {
			String strings[] = str[0].split(" ");
			if (strings[0].equals("0"))  {  //insertion {
				Log.e(TAG, "inserting " + strings[1] + " "+strings[2]);
				insert(strings[1] + " " + strings[2]);
			}
		}
	}
*/
	private class SendTaskQueryAll extends AsyncTask<String, String, Void> {
		@Override
		protected Void doInBackground(String... msgs) {
			try {
				Log.e(TAG, "SendTask Entered " + msgs[0] + " " + msgs[1] + " " + msgs[2]);
				Socket socket;
				String msgToSend = msgs[0] + " " + msgs[1];

				socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
						Integer.parseInt(msgs[2]));
				BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());

				out.write(msgToSend + "\n");
				out.flush();


				socket.setSoTimeout(1500);
				String str = new String();

				str = br.readLine();
				Log.e(TAG, "SendTask received " + str);

				if(str!=null) {
				if (!str.equals("writeReqAck") && !str.equals("nullQueryResult") && !str.equals("deleteDone") && !str.equals("aliveProcAdded")) {
					//	synchronized (lock) {
					String higherStrings[] = str.split("_");
					for (int i = 0; i < higherStrings.length; i++) {
						String lowerStrings[] = higherStrings[i].split("/");
						if (lowerStrings[2].endsWith("_"))
							lowerStrings[2] = lowerStrings[2].substring(0, lowerStrings[2].length() - 1);
						String tempKey = lowerStrings[0];
						String tempValue = lowerStrings[1];
						Integer tempVersion = Integer.parseInt(lowerStrings[2]);
						synchronized (lock) {
							if (vals.get(tempKey) != null) {
								if (tempVersion > versions.get(tempKey)) {
									Log.e(TAG, "vars added " + tempKey + " " + tempValue);
									vals.put(tempKey, tempValue);
									versions.put(tempKey, tempVersion);
								}
								int number = times.get(tempKey);
								times.put(tempKey,number+1);
							} else {
								Log.e(TAG, "vars added " + tempKey + " " + tempValue);
								vals.put(tempKey, tempValue);
								versions.put(tempKey, tempVersion);
								times.put(tempKey,1);
							}
						}
					}
					//}
				}

			}
				synchronized (lock) {
					noOfResponses++;
				}


				out.close();
				br.close();

				socket.close();

			}
			catch(SocketTimeoutException ste) {
				aliveProc.remove(msgs[2]);
				//noOfResponses++;
				Log.e(TAG, "SendTask Timeout");
			}
			catch (IOException e) {
				Log.e(TAG, "SendTask use set_redir");
			}
			return null;
		}
		@Override
		protected void onProgressUpdate(String ...str) {
			//       Log.e(TAG,"SendTask onprogress entered");
		}
	}

	private class loadMessages extends AsyncTask<String, Void, Void> {
		@Override
		protected Void doInBackground(String... msgs) {
			while(noOfResponses<3) {
			}
			Log.e(TAG, "onCreate noOfResponses " + noOfResponses);
			synchronized (lock) {
				noOfResponses = 0;
			}
				//}
				//synchronized (lock) {
				Iterator it = recoverVals.entrySet().iterator();
			//synchronized (recoverLock) {
				while (it.hasNext()) {
					Map.Entry pair = (Map.Entry) it.next();
					String tempKey = (String) pair.getKey();
					String tempValue = (String) pair.getValue();
					Log.e(TAG, "inserted here 1");
					insert1(tempKey + " " + tempValue + " " + String.valueOf(recoverVersions.get(tempKey)));
				}
		//	}
				//}

				//synchronized (lock) {
				//	vals = new HashMap<String, String>();
				//	versions = new HashMap<String, Integer>();
				//times = new HashMap<String, Integer>();

			return null;
		}
	}

}
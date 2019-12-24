package com.varunisystems.messanger;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService extends Service {

    private static final String APP_NAME = "BluetoothChatApp";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");



    private BluetoothAdapter bluetoothAdapter;

    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ReadWriteThread connectedThread;


    // SPP UUID service - this should work for most devices
    //private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // String for MAC address
    //private static final String MAC_ADDRESS = "YOUR:MAC:ADDRESS:HERE";

    private StringBuilder recDataString = new StringBuilder();

    private final Handler handler;
    static final int STATE_NONE = 0;
    static final int STATE_LISTEN = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    private int state;
    private String inMessage = "";

    public BluetoothService(Handler handler) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;

        this.handler = handler;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("BT SERVICE", "SERVICE CREATED");
        //stopThread = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("BT SERVICE", "SERVICE STARTED");
        /*handler = new Handler() {

            public void handleMessage(android.os.Message msg) {
                Log.d("DEBUG", "handleMessage");
                if (msg.what == handlerState) {                                     //if message is what we want
                    String readMessage = (String) msg.obj;                                                                // msg.arg1 = bytes from connect thread
                    recDataString.append(readMessage);

                    Log.d("RECORDED", recDataString.toString());
                    // Do stuff here with your data, like adding it to the database
                }
                recDataString.delete(0, recDataString.length());                    //clear all string data
            }
        };*/


        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();       // get Bluetooth adapter

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        /*handler.removeCallbacksAndMessages(null);
        stopThread = true;
        if (mConnectedThread != null) {
            mConnectedThread.closeStreams();
        }
        if (mConnectingThread != null) {
            mConnectingThread.closeSocket();
        }*/
        Log.d("SERVICE", "onDestroy");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Set the current state of the chat connection
    private synchronized void setState(int state) {
        this.state = state;

        handler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    // get current connection state
    public synchronized int getState() {
        return state;
    }


    // start service
    public synchronized void start() {
        // Cancel any thread
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any running thresd
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        setState(STATE_LISTEN);
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    // initiate connection to remote device
    public synchronized void connect(BluetoothDevice device) {
        // Cancel any thread
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        // Cancel running thread
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the thread to connect with the given device
        connectThread = new ConnectThread(device);
        connectThread.start();
        setState(STATE_CONNECTING);
    }

    // manage Bluetooth connection
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        // Cancel the thread
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel running thread
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ReadWriteThread(socket);
        connectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = handler.obtainMessage(MainActivity.MESSAGE_DEVICE_OBJECT);
        Bundle bundle = new Bundle();
        bundle.putParcelable(MainActivity.DEVICE_OBJECT, device);
        msg.setData(bundle);
        handler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    // stop all threads
    public synchronized void stop() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        setState(STATE_NONE);
    }

    public void write(byte[] out) {
        ReadWriteThread r;
        synchronized (this) {
            if (state != STATE_CONNECTED)
                return;
            r = connectedThread;
        }
        r.write(out);
    }

    private void connectionFailed() {
        Message msg = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Unable to connect device");
        msg.setData(bundle);
        handler.sendMessage(msg);

        // Start the service over to restart listening mode
        this.start();
    }

    private void connectionLost() {
        Message msg = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Device connection was lost");
        msg.setData(bundle);
        handler.sendMessage(msg);

        // Start the service over to restart listening mode
        this.start();
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            serverSocket = tmp;
        }

        public void run() {
            setName("AcceptThread");
            BluetoothSocket socket;
            while (state != STATE_CONNECTED) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (this) {
                        switch (state) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate
                                // new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                }
                                break;
                        }
                    }
                }
            }
        }
        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket = tmp;
        }

        public void run() {
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            bluetoothAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                socket.connect();
                //Log.e("Socket RUN", "Socket Runnnnnnnnnnnnnnn");
            } catch (IOException e) {
                try {
                    socket.close();
                    Log.e("Socket: Error 1", String.valueOf(e));
                } catch (IOException e2) {
                    Log.e("Socket:", String.valueOf(e2));
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (this) {
                connectThread = null;
            }

            // Start the connected thread
            connected(socket, device);
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }


    private class ReadWriteThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ReadWriteThread(BluetoothSocket socket) {
            this.bluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            StringBuilder readMessage = new StringBuilder();
            // Keep listening to the InputStream
            while (true) {
                try {

                    bytes = inputStream.read(buffer);
                    String read = new String(buffer, 0, bytes);
                    readMessage.append(read);

                    if (read.contains("\n")) {
                        //Log.e("Input Stream", String.valueOf(readMessage));
                        inMessage = "";
                        inMessage = String.valueOf(readMessage);
                        handler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1,
                                buffer).sendToTarget();
                        readMessage.setLength(0);
                    }


                    // Read from the InputStream
                   /* bytes = inputStream.read(buffer);
                    if(bytes > 0) {
                        //Log.e("Input Stream", String.valueOf(bytes));
                        // Send the obtained bytes to the UI Activity
                        handler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1,
                                buffer).sendToTarget();
                    }*/

                } catch (IOException e) {
                    connectionLost();
                    // Start the service over to restart listening mode
                    this.start();
                    break;
                }
            }
        }

        // write to OutputStream
        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);
                handler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1,
                        buffer).sendToTarget();
            } catch (IOException e) {
            }
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}



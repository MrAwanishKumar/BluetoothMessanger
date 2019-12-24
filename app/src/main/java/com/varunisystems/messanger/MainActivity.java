package com.varunisystems.messanger;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MenuItem;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity implements LocationListener, OnMapReadyCallback {

    private static final int LOCATION_PSERMISSION = 0;
    private TextView status, latlngId, tv_sending;
    private Button btnConnect, btn_set_id;
    private ListView listView;
    private Dialog dialog;
    private TextInputLayout inputLayout, set_id_layout;
    private ArrayAdapter<String> chatAdapter;
    private ArrayList<String> chatMessages;
    private BluetoothAdapter bluetoothAdapter;

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final String DEVICE_OBJECT = "device_name";

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private ChatController chatController;
    private BluetoothDevice connectingDevice;
    private ArrayAdapter<String> discoveredDevicesAdapter;

    protected LocationManager locationManager;
    protected LocationListener locationListener;
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

    private GoogleMap mMap;
    RelativeLayout rlMainDataView,rlMainMapView, rlSettingView;

    public double lat=0;
    public double lng=0;
    public Location location;
    public Handler timer;

    private Marker firstMarker;
    private Marker secondMarker;

    private boolean msgTypeFlag = false;
    private boolean msgReadWriteFlag = false;
    private int timeinsec, gpsTimeInSec, setId= 0;
    private int tempId, tmpId = 0;
    private int interval = 5;

    Queue<String> queue = new LinkedList<String>();

    public String gpsTime = "";

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private NavigationView navigationView;

    SaveId saveId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.act_main);
        findViewsByIds();


        drawerLayout = (DrawerLayout)findViewById(R.id.act_main);
        toggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.Open, R.string.Close);

        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();


        //getActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        saveId = new SaveId(MainActivity.this);

        if(!saveId.getId().isEmpty()) {
            setId = Integer.parseInt(String.valueOf(saveId.getId()));
            //tempId = Integer.parseInt(String.valueOf(saveId.getId()));
            //tmpId = Integer.parseInt(String.valueOf(saveId.getId())) + 5;

            set_id_layout.getEditText().setText(saveId.getId());
            Toast.makeText(MainActivity.this, "Id Set to "+saveId.getId(), Toast.LENGTH_SHORT).show();
        }


        //check device support bluetooth or not
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            finish();
        }

        //show bluetooth devices dialog when click connect button
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPrinterPickDialog();
            }
        });

        //set chat adapter
        chatMessages = new ArrayList<>();
        chatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chatMessages);
        listView.setAdapter(chatAdapter);

        timer = new Handler();
        timer.postDelayed(timeInSecCounter, 1000);

        //location permission
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, (LocationListener) this);
        location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        boolean statusOfGPS = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if(!statusOfGPS) {
            latlngId.setText("Please enable GPS!");
        }
        DecimalFormat decimalFormat = new DecimalFormat("##.######");
        if (location != null){
            lat= Double.valueOf(decimalFormat.format(location.getLatitude()));
            lng= Double.valueOf(decimalFormat.format(location.getLongitude()));

        }


        rlMainDataView =  findViewById(R.id.rl_main_view);
        rlMainMapView = findViewById(R.id.rl_map_view_main);
        rlSettingView = findViewById(R.id.rl_settings_view_main);

        navigationView = (NavigationView)findViewById(R.id.nv);
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                switch(id)
                {
                    case R.id.home_menu:
                        ShowMapView(1);
                        break;

                    case R.id.map_view:
                        ShowMapView(2);
                        break;

                    case R.id.settings_menu:
                        ShowMapView(3);
                        break;
                }


                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
        });

        //////////////////map/////
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync((OnMapReadyCallback) this);


    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if(toggle.onOptionsItemSelected(item))
            return true;

        return super.onOptionsItemSelected(item);
    }


    public Runnable timeInSecCounter = new Runnable(){
        @Override
        public void run() {
            /*timeinsec++;
            if(timeinsec == 60) {
                timeinsec = 0;
            }*/

            gpsTimeInSec++;
            if(gpsTimeInSec >= 60) {
                gpsTimeInSec = 0;
            }

            //Log.e("GPS Time In Second - ", String.valueOf(gpsTimeInSec));
//
//
//            Log.e("GPS Time In Second - ", String.valueOf(gpsTimeInSec) + "gpsTimeInSec % 5 -"+(gpsTimeInSec % 5) + " = "+ setId);

            //if(setId > 0) {
                //Log.d("GPS Second Id ", ""+ setId+" "+String.valueOf(gpsTimeInSec)+" Queue "+ queue);
                if(gpsTimeInSec % 6 ==  setId ) {
                    //Log.e("Logic : ", "Id - "+setId+" Time: "+gpsTimeInSec+ " tmpId :"+ tmpId  );
                    if(gpsTimeInSec <= 5) {
                        updateMap(lat, lng);
                    }
                    else {

                        if (queue.size() > 0) {

                            if (chatController.getState() != ChatController.STATE_CONNECTED) {
                                Log.e("Not Connected : ", "Not Connected");
                                //String iteratorValue = (String) queue.poll();
                                tv_sending.setVisibility(View.GONE);
                                // return;
                            } else {
                                Log.d("Sent : ", "Id - " + setId + " at : " + gpsTimeInSec);
                                String iteratorValue = (String) queue.poll();
                                //Log.d("Queue Next Value :", "Chat String" + iteratorValue);
                                sendMessage(iteratorValue);
                            }
                        }
                    }

                }

            //}

            MainActivity.this.timer.postDelayed(MainActivity.this.timeInSecCounter, 1000);
        }
    };


    private void ShowMapView(int pos) {
        switch (pos)
        {
            case 1:
                rlMainDataView.setVisibility(View.VISIBLE);
                rlMainMapView.setVisibility(View.GONE);
                rlSettingView.setVisibility(View.GONE);
                showKeyBoard(this, rlMainDataView);
                break;

            case 2:
                rlMainDataView.setVisibility(View.GONE);
                rlMainMapView.setVisibility(View.VISIBLE);
                rlSettingView.setVisibility(View.GONE);
                hideKeyBoard(this, rlMainMapView);
                break;
            case 3:
                rlMainDataView.setVisibility(View.GONE);
                rlMainMapView.setVisibility(View.GONE);
                rlSettingView.setVisibility(View.VISIBLE);
                showKeyBoard(this, rlMainMapView);
                break;
        }

        Toast.makeText(MainActivity.this, "The set id is "+setId, Toast.LENGTH_SHORT).show();
    }

    int io;
    private StringBuilder sb = new StringBuilder();
    private StringBuilder wsb = new StringBuilder();

    final static String DOUBLE_PATTERN = "[0-9]+(.){0,1}[0-9]*";

    private Handler handler = new Handler(new Handler.Callback() {

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case ChatController.STATE_CONNECTED:
                            setStatus("Connected to: " + connectingDevice.getName());
                            btnConnect.setEnabled(false);
                            break;
                        case ChatController.STATE_CONNECTING:
                            setStatus("Connecting...");
                            btnConnect.setEnabled(false);
                            break;
                        case ChatController.STATE_LISTEN:
                        case ChatController.STATE_NONE:
                            setStatus("Not connected");
                            btnConnect.setEnabled(true);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;

                    msgReadWriteFlag = true;

                    String writeMessage = new String(writeBuf);
//                    Log.d("Wrint String", String.valueOf(msg.obj));

                    wsb.append(writeMessage);
                    int wEndOfLineIndex = wsb.indexOf(";");


                    if (wEndOfLineIndex > 0) {
                        String wMsg = String.valueOf(wsb);  // extract string
                        wsb.delete(0, wsb.length());
                        //Log.d("Wrint String Full", wMsg);

                        /*String rem = "";
                        if(wMsg.length()> 1) {
                            rem = wMsg.substring(1, wMsg.length() - 1); //remove id and semicolon from start and end of the string
                        }
                        else {
                            rem = wMsg.substring(0, wMsg.length() - 1);
                        }
                        chatMessages.add("Me: " + rem);
                        chatAdapter.notifyDataSetChanged();
                        scrollMyListViewToBottom();*/
                        ////
                        String[] wStr = wMsg.split(":");

                        if(wStr[0].equals("P")) {
                            String wSenderId = !wStr[1].isEmpty() ? wStr[1].toString().trim() :  "0";
                            String rlat = !wStr[2].isEmpty() ? wStr[2].toString().trim() :  "0";
                            String secondStr = !wStr[3].isEmpty() ? wStr[3].toString().trim() : "0";
                            String rlng = secondStr.substring(0, secondStr.length() - 1)                                                                                 ;
                            String wrtmsg = rlat+","+rlng;

                            chatMessages.add("Me: " + wrtmsg);
                            chatAdapter.notifyDataSetChanged();
                            scrollMyListViewToBottom();
                        }
                        else if(wStr[0].equals("C")) { // if position - dont show it in the chat history
                            String wSdrId = !wStr[1].isEmpty() ? wStr[1].toString().trim() : "0";
                            String sStr = !wStr[1].isEmpty() ? wStr[2].toString().trim() : " ";
                            String rem = sStr.substring(0, sStr.length() - 1); //remove id and semicolon from start and end of the string
                            chatMessages.add("Me: " + rem);
                            chatAdapter.notifyDataSetChanged();
                            scrollMyListViewToBottom();
                        }
                    }

                    tv_sending.setVisibility(View.GONE);
                    msgReadWriteFlag = false;
                    break;
                case MESSAGE_READ:

                    byte[] readBuf = (byte[]) msg.obj;

                    msgReadWriteFlag = true;

                    String readMessage = new String(readBuf, 0, msg.arg1);

                    byte[] bytes = readMessage.getBytes();


                    sb.append(chatController.inMessage);

                    int endOfLineIndex = sb.indexOf(";");

                    Log.e("Read  --", String.valueOf(readMessage));
                    if (endOfLineIndex > 0) {                                            // if end-of-line,
                    //if (readMessage.equals(";")) {                                            // if end-of-line,
                        String recMsg = String.valueOf(sb);//sb.substring(0, endOfLineIndex+1);               // extract string
                        //sb.delete(0, sb.length());                                      // and clear
                        sb.setLength(0); //empty
                        //Log.e("Full String Append", recMsg);

                        String[] strArr = recMsg.split(":");
                        if(strArr[0].equals("P")) {
                            try {
                                String senderId = !strArr[1].isEmpty() ? strArr[1].toString().trim() : "0";
                                String rlat = !strArr[2].isEmpty() ? strArr[2].toString().trim() : "0";
                                String secondStr = !strArr[3].isEmpty() ? strArr[3].toString().trim() : "0";
                                String rlng = secondStr.substring(0, secondStr.length() - 1);

                                if (rlat.length() > 2 && rlng.length() > 2) {
                                    if (Pattern.matches(DOUBLE_PATTERN, rlat) && Pattern.matches(DOUBLE_PATTERN, rlng)) {
                                        Log.e("Position String ", rlat + "  " + rlng);
                                        Double dlat = Double.valueOf(rlat);
                                        Double dlng = Double.valueOf(rlng);
                                        showSecondDevicePosition(dlat, dlng);
                                    }
                                }
                                chatMessages.add("Sparrow"+senderId  + ":  " + rlat + "," + rlng);
                                chatAdapter.notifyDataSetChanged();
                                scrollMyListViewToBottom();
                            }catch (Exception e) {
                                throw e;
                            }
                        }
                        else if(strArr[0].equals("C")) {
                            String sdrId = !strArr[1].isEmpty() ? strArr[1].toString().trim(): "0";
                            String rstring = !strArr[2].isEmpty() ? strArr[2].toString().trim() : " ";
                            String rmsg = rstring.substring(0, rstring.length() - 1); //remove semicolon from the end of the string

                            //Log.e("Chat String ", rmsg);

                            chatMessages.add("Sparrow"+sdrId + ":  " + rmsg);
                            chatAdapter.notifyDataSetChanged();
                            scrollMyListViewToBottom();
                        }
                    }

                    msgReadWriteFlag = false;

                    break;
                case MESSAGE_DEVICE_OBJECT:
                    connectingDevice = msg.getData().getParcelable(DEVICE_OBJECT);
                    Toast.makeText(getApplicationContext(), "Connected to " + connectingDevice.getName(),
                            Toast.LENGTH_SHORT).show();
                    //id2 = 2;
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });



    private void showPrinterPickDialog() {
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.layout_bluetooth);
        dialog.setTitle("Bluetooth Devices");

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();

        //Initializing bluetooth adapters
        ArrayAdapter<String> pairedDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        discoveredDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        //locate listviews and attatch the adapters
        ListView listView = (ListView) dialog.findViewById(R.id.pairedDeviceList);
        ListView listView2 = (ListView) dialog.findViewById(R.id.discoveredDeviceList);
        listView.setAdapter(pairedDevicesAdapter);
        listView2.setAdapter(discoveredDevicesAdapter);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(discoveryFinishReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryFinishReceiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            pairedDevicesAdapter.add(getString(R.string.none_paired));
        }

        //Handling listview item click event
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);
                Log.e("Address", String.valueOf(address.length()));
                saveId.setMacAddress(address);
                connectToDevice(address);
                dialog.dismiss();
            }

        });

        listView2.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);
                saveId.setMacAddress(address);
                connectToDevice(address);
                dialog.dismiss();


                // Get the device MAC address, which is the last 17 chars in the View

                final String name = info.substring(0,info.length() - 17);

                // Spawn a new thread to avoid blocking the GUI one
               /* new Thread()
                {
                    public void run() {
                        boolean fail = false;

                        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

                        try {
                            mBTSocket = createBluetoothSocket(device);
                        } catch (IOException e) {
                            fail = true;
                            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                        // Establish the Bluetooth socket connection.
                        try {
                            mBTSocket.connect();
                        } catch (IOException e) {
                            try {
                                fail = true;
                                mBTSocket.close();
                                mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                        .sendToTarget();
                            } catch (IOException e2) {
                                //insert code to deal with this
                                Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                        if(fail == false) {
                            mConnectedThread = new ConnectedThread(mBTSocket);
                            mConnectedThread.start();

                            mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                                    .sendToTarget();
                        }
                    }
                }.start();*/
            }
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setCancelable(false);
        dialog.show();
    }

   /* private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connection with BT device using UUID
    }*/

    private void setStatus(String s) {
        status.setText(s);
    }

    private void connectToDevice(String deviceAddress) {
        bluetoothAdapter.cancelDiscovery();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        Log.e("Device", String.valueOf(device));
        chatController.connect(device);
    }

    private void findViewsByIds() {
        status = (TextView) findViewById(R.id.status);
        latlngId = (TextView) findViewById(R.id.latlngId);
        btnConnect = (Button) findViewById(R.id.btn_connect);
        btn_set_id = (Button) findViewById(R.id.btn_set_id);
        listView = (ListView) findViewById(R.id.list);
        inputLayout = (TextInputLayout) findViewById(R.id.input_layout);
        set_id_layout = (TextInputLayout) findViewById(R.id.set_id_layout);
        tv_sending = (TextView) findViewById(R.id.tv_sending);
        View btnSend = findViewById(R.id.btn_send);

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (inputLayout.getEditText().getText().toString().equals("")) {
                    Toast.makeText(MainActivity.this, "Please input some texts", Toast.LENGTH_SHORT).show();
                } else {
                    //TODO: create string to post


                    String msg = "C:" + setId + ":" + inputLayout.getEditText().getText().toString() + ";";
                    //String msg = "" + setId + "" + inputLayout.getEditText().getText().toString() + ";";

                    queue.add(msg);
                    //sendMessage(msg);
                    if (inputLayout.getEditText().getText().length() > 0) {
                        inputLayout.getEditText().setText("");
                    }

                    tv_sending.setVisibility(View.VISIBLE);
                    //Log.e("Msg: ", msg);
                }
            }
        });

        btn_set_id.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String getInput = set_id_layout.getEditText().getText().toString();
                if(getInput.equals("")) {
                    Toast.makeText(MainActivity.this, "Please enter id number!", Toast.LENGTH_SHORT).show();
                }
                else {
                    setId = Integer.parseInt(getInput);

                    saveId.setId(String.valueOf(getInput));
                    Toast.makeText(MainActivity.this, "Success ID is Set Now: "+getInput, Toast.LENGTH_SHORT).show();
                    ShowMapView(1); //home page
                }
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH:
                if (resultCode == Activity.RESULT_OK) {
                    chatController = new ChatController(this, handler);
                } else {
                    Toast.makeText(this, "Bluetooth still disabled, turn off application!", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void sendMessage(String message) {
        if (chatController.getState() != ChatController.STATE_CONNECTED) {
            Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (message.length() > 0) {
            byte[] send = message.getBytes();
            chatController.write(send);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        } else {
            chatController = new ChatController(this, handler);
        }

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,  Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "Netowork Permission Required. Please enable!", Toast.LENGTH_SHORT).show();
            }
            else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PSERMISSION);

            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (chatController != null) {
            if (chatController.getState() == ChatController.STATE_NONE) {
                chatController.start();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (chatController != null)
            chatController.stop();
    }

    private final BroadcastReceiver discoveryFinishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    discoveredDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (discoveredDevicesAdapter.getCount() == 0) {
                    discoveredDevicesAdapter.add(getString(R.string.none_found));
                }
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case LOCATION_PSERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }

    }



    @Override
    public void onLocationChanged(Location location) {

        //round of to 6 decimal places
        DecimalFormat decimalFormat = new DecimalFormat("##.######");

        latlngId.setText("Position Lat:" + decimalFormat.format(location.getLatitude()) + ", Lng:" + decimalFormat.format(location.getLongitude()));
        //Log.d("Postions","Lat:"+ decimalFormat.format(location.getLatitude()) +", Lng:"+ decimalFormat.format(location.getLongitude()));
        lat= Double.valueOf(decimalFormat.format(location.getLatitude()));
        lng= Double.valueOf(decimalFormat.format(location.getLongitude()));

        gpsTime = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(location.getTime());

        String second = new SimpleDateFormat("ss").format(location.getTime());
        //Log.e("Position", "Position Lat:" + decimalFormat.format(location.getLatitude()) + ", Lng:" + decimalFormat.format(location.getLongitude()));
        int gpsSecond = Integer.parseInt(String.valueOf(second));

        gpsTimeInSec = gpsSecond;

        //Log.d("GPS Second InLo ", String.valueOf(gpsTimeInSec));
        //Log.d("GPS TIME ", gpsTime);



    }



    private void updateMap(double lat, double lng) {
        if(lat != 0 && lng != 0) {
            //DecimalFormat decimalFormat = new DecimalFormat("##.######");
            //lat= Double.valueOf(decimalFormat.format(lat));
            //lng= Double.valueOf(decimalFormat.format(lng));
            // Add a marker to device location and move the camera
            LatLng myLocation = new LatLng(lat, lng);
            //LatLng secondLocation = new LatLng(19.13033, 72.54718);
            //      LatLng sydney = new LatLng(lat, lan);
            //mMap.clear();
            if(firstMarker != null) {
                firstMarker.remove();
            }
            firstMarker = mMap.addMarker(new MarkerOptions().position(myLocation).title("Me"));
            firstMarker.setPosition(new LatLng(lat, lng));
            //mMap.addMarker(new MarkerOptions().position(myLocation).title("Me"));
            //mMap.addMarker(marker);// in Mumbai
            //mMap.addMarker(new MarkerOptions().position(myLocation).title("Me"));// in Mumbai
            //mMap.addMarker(new MarkerOptions().position(secondLocation).title("Other"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 17));
            //mMap.moveCamera(CameraUpdateFactory.newLatLng(secondLocation));

            String msg = "P:"+ setId +":"+ lat +":"+ lng+";";

            if (chatController.getState() != ChatController.STATE_CONNECTED) {
                Log.d("No Chat active", "No Chat Active..........");
            }
            else {
                if (gpsTimeInSec % 6 == setId) {
                    //Log.e("Logic : ", "Id - "+setId+" Time: "+gpsTimeInSec+ " tmpId :"+ tmpId  );
                    if (gpsTimeInSec <= 5) {
                        sendMessage(msg);
                        //queue.add(msg);
                        tv_sending.setVisibility(View.VISIBLE);
                    } else {
                        queue.add(msg);
                    }
                }
            }
        }
    }

    private void showSecondDevicePosition(double lat, double lng) {
        if(lat != 0 && lng != 0) {
            // Add a marker to device location and move the camera
            LatLng myLocation = new LatLng(lat, lng);
            //LatLng secondLocation = new LatLng(19.13033, 72.54718);
            //      LatLng sydney = new LatLng(lat, lan);
            //mMap.clear();
            if(secondMarker != null) {
                secondMarker.remove();
            }
            secondMarker = mMap.addMarker(new MarkerOptions().position(myLocation).title("Other"));// in Mumbai
            secondMarker.setPosition(new LatLng(lat, lng));
            //mMap.addMarker(new MarkerOptions().position(secondLocation).title("Other"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 17));
            //mMap.moveCamera(CameraUpdateFactory.newLatLng(secondLocation));
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d("GPS","disable");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d("GPS","enable");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        //Log.d("GPS","status");
    }

    ///////////////////map view/////////////////////////
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Add a marker in Sydney and move the camera
      /*LatLng firstLocation = new LatLng(19.03033, 72.84718);
      LatLng secondLocation = new LatLng(19.13033, 72.54718);
//      LatLng sydney = new LatLng(lat, lan);
      mMap.addMarker(new MarkerOptions().position(firstLocation).title("Me"));// in Mumbai
      mMap.addMarker(new MarkerOptions().position(secondLocation).title("Other"));
      mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstLocation, 15));
      mMap.moveCamera(CameraUpdateFactory.newLatLng(secondLocation));*/
    }

    //////////////////////////////////////////////


    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(discoveryFinishReceiver);
        super.onPause();
    }

    private void scrollMyListViewToBottom() {
        listView.post(new Runnable() {
            @Override
            public void run() {
                // Select the last row so it will scroll into view...
                listView.setSelection(chatAdapter.getCount() - 1);
            }
        });
    }

    /**
     * open soft keyboard.
     *
     * @param context
     * @param view
     */
    public static void showKeyBoard(Context context, View view) {
        /*try {
            InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.showSoftInput(view, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }*/
    }

    /**
     * close soft keyboard.
     *
     * @param context
     * @param view
     */
    public static void hideKeyBoard(Context context, View view) {
        /*try {
            InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }*/
    }

    boolean doubleBackToExitPressedOnce = false;
    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();

            return;
        }

        this.doubleBackToExitPressedOnce = true;
        //Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();
        ShowMapView(1); //hoem page
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {

                doubleBackToExitPressedOnce=false;
            }
        }, 2000);
    }
}
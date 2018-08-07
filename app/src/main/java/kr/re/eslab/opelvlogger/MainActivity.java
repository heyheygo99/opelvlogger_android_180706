package kr.re.eslab.opelvlogger;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static kr.re.eslab.opelvlogger.MonitorFragment.monitorItemListViewAdapter;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, RadioGroup.OnCheckedChangeListener  {

    public static boolean connect_flag = false;

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "OPELvlogger";
    public static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;

    private static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS =  1;

    TextView mRemoteRssiVal;
    RadioGroup mRg;
    public static int mState = UART_PROFILE_DISCONNECTED;
    public static UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;

    public static boolean countDownTimerFlag = false;

    public static ArrayList<MonitorItem> monitorItems = new ArrayList<MonitorItem>();

    private byte[] txValue;
    private String receiveMessage;
    private String[] receiveMessage_split;

    SharedPreferences sp;
    SharedPreferences.Editor editor;

    private static String folderName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Extract/";

    TimerTask tt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (Build.VERSION.SDK_INT >= 23) {
            // Marshmallow+ Permission APIs
            permissionMarshMallow();
        }

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        service_init();

        sp = getSharedPreferences("obd", 0);
        editor = sp.edit();

        // 180512 수정사항 - 최초 시작 후 바로 추출 시 Monitor Fragment에서 ID 등록되지 않던 문제 해결
        monitorItemListViewAdapter = new monitorItemAdapter();

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        Fragment fragment = new HomeFragment();
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add( R.id.fragment_place, fragment);
        fragmentTransaction.commit();

    }

    @Override
    public void onBackPressed() {

        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("OPELvlogger's running in background.\n             Disconnect to exit");
        }
        else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.popup_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
        }
//
//        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
//        if (drawer.isDrawerOpen(GravityCompat.START)) {
//            drawer.closeDrawer(GravityCompat.START);
//        } else {
//            super.onBackPressed();
//        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.BLE_connect) {
            if( mState == UART_PROFILE_CONNECTED ) {
                item.setChecked(true);
            }

            else if ( mState == UART_PROFILE_DISCONNECTED ) {
                item.setChecked(false);
            }

            if (!mBtAdapter.isEnabled()) {
                Log.i(TAG, "onClick - BT not enabled yet");
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            }
            else {
                if (mDevice!=null) {
                    mService.disconnect();
                }
                Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();



        if( mState == UART_PROFILE_CONNECTED ) {

            Fragment fragment = null;

            if (id == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (id == R.id.nav_monitor) {
                fragment = new MonitorFragment();
            } else if (id == R.id.nav_extract_gear) {
                fragment = new ExtractGearFragment();
            } else if (id == R.id.nav_extract_winker) {
                fragment = new ExtractWinkerFragment();
            } else if (id == R.id.nav_extract_brake) {
                fragment = new ExtractBrakeFragment();
            } else if (id == R.id.nav_extract_wheel) {
                fragment = new ExtractWheelFragment();
            }

            if (fragment != null) {
                FragmentManager fragmentManager = getFragmentManager();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.fragment_place, fragment);
                fragmentTransaction.commit();
            }

            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);
        }

        else {
            Toast.makeText(getApplicationContext(), "Please Connect BLE to OPELvlogger", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
        }
    };

    private Handler mHandler = new Handler() {
        @Override

        //Handler events that received from UART service
        public void handleMessage(Message msg) {

        }
    };

    private final BroadcastReceiver UARTStatusChangeReceiver;

    {
        UARTStatusChangeReceiver = new BroadcastReceiver() {

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                final Intent mIntent = intent;
                //*********************//
                if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            Log.d(TAG, "UART_CONNECT_MSG");
//                             btnConnectDisconnect.setText("Disconnect");
//                             edtMessage.setEnabled(true);
//                             btnSend.setEnabled(true);
//                             ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - ready");
//                             listAdapter.add("["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
//                        	 	messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                            mState = UART_PROFILE_CONNECTED;

                        }
                    });
                }

                //*********************//
                if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            Log.d(TAG, "UART_DISCONNECT_MSG");
//                             btnConnectDisconnect.setText("Connect");
//                             edtMessage.setEnabled(false);
//                             btnSend.setEnabled(false);
//                             ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
//                             listAdapter.add("["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());
                            mState = UART_PROFILE_DISCONNECTED;
                            mService.close();
                            //setUiState();

                        }
                    });
                }

                //*********************//
                if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                    mService.enableTXNotification();
                }
                //*********************//
                if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
                    // BLE - Receive data
                    txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            try {
                                // BLE - Processing receive data
                                receiveMessage = new String(txValue, "UTF-8");
                                Log.d("receiveMessage", receiveMessage);

                                if(receiveMessage.contains("EXTRACT_STATE_COMPLETE") == true) {
                                    countDownTimerFlag = true;
                                }

                                else if(receiveMessage.contains("EXTRACT_START_READY") == true) {
                                    countDownTimerFlag = true;
                                }

                                else if(receiveMessage.contains("EXTRACT_ID") == true) {
                                    countDownTimerFlag = true;
                                    String[] text_split_result = receiveMessage.split(" ");
                                    WriteTextFile(folderName, "test", receiveMessage);
                                    String id = text_split_result[1];
                                    if( id.contains("NULL") == true) {
                                        Toast.makeText(getApplicationContext(),"Fail - Extraction",Toast.LENGTH_SHORT).show();
                                    }
                                    else {

                                        Toast.makeText(getApplicationContext(),"Extract ID - 0x"+id ,Toast.LENGTH_SHORT).show();
                                        String tempPacket = "N " + id + " 00 00 00 00 00 00 00 00";

                                        boolean addResult = monitorItemListViewAdapter.addItem(tempPacket);
                                        if ( addResult == false) {
                                            Toast.makeText(getApplicationContext(),"Make less than 20 monitored packets",Toast.LENGTH_SHORT).show();
                                        }
                                        else {
                                            monitorItemListViewAdapter.notifyDataSetChanged();
                                        }
                                    }

                                    String message = "MONITOR";
                                    byte[] value = message.getBytes("UTF-8");
                                    mService.writeRXCharacteristic(value);

                                    Fragment fragment = new MonitorFragment();
                                    FragmentManager fragmentManager = getFragmentManager();
                                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                                    fragmentTransaction.replace(R.id.fragment_place, fragment);
                                    fragmentTransaction.commit();


                                }
                                else {
                                    receiveMessage_split = receiveMessage.split(" ");


                                    for (int j = 0; j < monitorItemListViewAdapter.getCount(); j++) {
                                        if (receiveMessage_split[0].equals("N") && receiveMessage_split[1].equalsIgnoreCase(monitorItemListViewAdapter.getItem(j).get_MsgID())) {
                                            monitorItemListViewAdapter.setItem(j, receiveMessage); // Monitor Listview의 해당 ID 부분 갱신

                                            editor.putString("NPid", receiveMessage_split[3]+receiveMessage_split[2]);
                                            editor.commit();
//                                            WriteTextFile(folderName, "test_N.txt", receiveMessage);
//                                            WriteTextFile(folderName, "test_N.txt", "\r\n");

                                            break;
                                        } else if (receiveMessage_split[0].equals("S") && receiveMessage_split[3].equalsIgnoreCase(monitorItemListViewAdapter.getItem(j).get_data(1)) && receiveMessage_split[4].equalsIgnoreCase(monitorItemListViewAdapter.getItem(j).get_data(2))) {
                                            monitorItemListViewAdapter.setItem(j, receiveMessage); // Monitor Listview의 해당 ID 부분 갱신

                                            editor.putString("SPid", receiveMessage_split[5]);
                                            editor.commit();
//                                            WriteTextFile(folderName, "test_S.txt", receiveMessage);
//                                            WriteTextFile(folderName, "test_S.txt", "\r\n");
                                            break;
                                        }
                                    }
                                    monitorItemListViewAdapter.notifyDataSetChanged(); // Monitor Listview 갱신
                                }
                            }
                            catch (Exception e) {
                                Log.e(TAG, e.toString());
                            }
                        }
                     });
                }

                if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)) {
                    showMessage("Device doesn't support UART. Disconnecting");
                    mService.disconnect();
                }
            }
        };
    }

    public void WriteTextFile(String foldername, String filename, String contents) {

        try {
            File dir = new File(foldername);
            //디렉토리 폴더가 없으면 생성함
            if (!dir.exists()) {
                dir.mkdir();
            }
            //파일 output stream 생성
            FileWriter fos = new FileWriter(foldername + "/" + filename+".txt", true);
            //파일쓰기
            BufferedWriter writer = new BufferedWriter(fos);
            writer.write(contents+"\r\n");
            writer.flush();

            writer.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        startService(bindIntent);
        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }
    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;

    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
//                ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
                    mService.connect(deviceAddress);


                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<String, Integer>();
                // Initial
                perms.put(Manifest.permission.ACCESS_FINE_LOCATION, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);


                // Fill with results
                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);

                // Check for ACCESS_FINE_LOCATION
                if (perms.get(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        ||  perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

                        ) {
                    // All Permissions Granted
                } else {
                    // Permission Denied
                    finish();
                }
            }
            break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void permissionMarshMallow() {
        List<String> permissionsNeeded = new ArrayList<String>();

        final List<String> permissionsList = new ArrayList<String>();
        if (!addPermission(permissionsList, Manifest.permission.ACCESS_FINE_LOCATION))
            permissionsNeeded.add("Show Location");

        if (!addPermission(permissionsList, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            permissionsNeeded.add("Write Storage");

        if (permissionsList.size() > 0) {
            if (permissionsNeeded.size() > 0) {

                // Need Rationale
                String message = "App need access to " + permissionsNeeded.get(0);

                for (int i = 1; i < permissionsNeeded.size(); i++)
                    message = message + ", " + permissionsNeeded.get(i);

                showMessageOKCancel(message,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                                        REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
                            }
                        });
                return;
            }
            requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                    REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
            return;
        }
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean addPermission(List<String> permissionsList, String permission) {

        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            // Check for Rationale Option
            if (!shouldShowRequestPermissionRationale(permission))
                return false;
        }
        return true;
    }
}

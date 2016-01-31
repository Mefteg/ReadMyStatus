package com.ladybugriders.readmystatus;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private final static String DEVICE_NAME = "Bluno";
    private final static String DEVICE_SERVICE = "0000dfb0-0000-1000-8000-00805f9b34fb";
    private final static String DEVICE_CHARACTERISTIC = "0000dfb1-0000-1000-8000-00805f9b34fb";

    private final static String MESSAGE_TYPE_UPDATE = "UPDATE\n";
    private final static String MESSAGE_TYPE_SUCCESS = "SUCCESS\n";
    private final static String MESSAGE_TYPE_ERROR = "ERROR\n";

    private final static char TAG_BATTERY_SEPARATOR = '$';
    private final static char TAG_DISK_SEPARATOR = '&';

    private final static int REQUEST_ENABLE_BT = 0;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 20000;

    protected final int UI_ACTION_UPDATE_BTN_CONNECT = 0;


    // Callbacks

    // Device scan callback.
    private ScanCallback m_scanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult (int callbackType, ScanResult result) {
                    BluetoothDevice device = result.getDevice();

                    Log.d(TAG, "Found a device: " + device.toString());
                    Log.d(TAG, "\tdevice name: " + device.getName());
                    if (device.getName() != null && device.getName().equals(DEVICE_NAME)) {
                        m_btDevice = device;
                        m_scanning = false;
                        m_btAdapter.getBluetoothLeScanner().stopScan(m_scanCallback);
                        Log.d(TAG, "Bluno found!");

                        m_btGatt = device.connectGatt(m_context, false, m_gattCallback);
                    }
                }
            };

    private BluetoothGattCallback m_gattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);

                    Log.d(TAG, "newState: " + String.valueOf(newState));
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "\tCONNECTED");

                        m_connected = true;

                        // update UI
                        Message messageToUi =
                                m_handlerUI.obtainMessage(UI_ACTION_UPDATE_BTN_CONNECT);
                        messageToUi.sendToTarget();

                        m_btGatt.discoverServices();
                    }
                    else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                        m_connected = false;

                        // update UI
                        Message messageToUi =
                                m_handlerUI.obtainMessage(UI_ACTION_UPDATE_BTN_CONNECT);
                        messageToUi.sendToTarget();
                    }
                }
                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);

                    UUID serialServiceUUID = UUID.fromString(DEVICE_SERVICE);
                    UUID serialCharacteristicUUID = UUID.fromString(DEVICE_CHARACTERISTIC);

                    Log.d(TAG, "nb services: " + gatt.getServices().size());
                    for (BluetoothGattService service : gatt.getServices()) {
                        Log.d(TAG, service.toString());
                        Log.d(TAG, "\tUUID" + service.getUuid());
                        if (service.getUuid().equals(serialServiceUUID)) {
                            Log.d(TAG, "\tSerial Service found!");
                            m_btGattService = service;

                            BluetoothGattCharacteristic characteristic =
                                    service.getCharacteristic(serialCharacteristicUUID);
                            if (characteristic != null) {
                                Log.d(TAG, "\tSerial Characteristic found!");
                                m_btGattCharacteristic = characteristic;

                                Log.d(TAG, "Turn on notification.");
                                m_btGatt.setCharacteristicNotification(characteristic, true);
                            }
                        }
                    }
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicRead(gatt, characteristic, status);
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicWrite(gatt, characteristic, status);

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "Write successful.");
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    super.onCharacteristicChanged(gatt, characteristic);

                    String data = new String(characteristic.getValue());
                    Log.d(TAG, "Characteristic new value: " + data);

                    switch (data) {
                        case MESSAGE_TYPE_UPDATE:
                            send();
                            break;
                        case MESSAGE_TYPE_SUCCESS:
                            break;
                        case MESSAGE_TYPE_ERROR:
                            break;
                    }
                }
            };

    // BLE
    protected BluetoothAdapter m_btAdapter = null;
    protected BluetoothDevice m_btDevice = null;
    protected BluetoothGatt m_btGatt = null;
    protected BluetoothGattService m_btGattService = null;
    protected BluetoothGattCharacteristic m_btGattCharacteristic = null;

    private boolean m_scanning = false;
    private boolean m_connected = false;
    private Handler m_handler = null;

    // UI
    private Context m_context = null;

    protected Button m_btnConnect = null;

    Handler m_handlerUI = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        m_context = this;

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        m_btAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (m_btAdapter == null || !m_btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // ask for location permission (mandatory to scan)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
        }

        m_scanning = false;
        m_handler = new Handler();

        m_btnConnect = (Button) findViewById(R.id.btn_connect);
        m_btnConnect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (m_connected) {
                    if (m_btDevice != null) {
                        closeDeviceConnection();

                        m_connected = false;
                        updateBtnConnect();
                    }
                } else {
                    // first scan devices
                    scanDevices(!m_scanning);
                }
            }
        });

        m_handlerUI = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                // choose the action to perform
                switch (inputMessage.arg1) {
                    case UI_ACTION_UPDATE_BTN_CONNECT:
                        updateBtnConnect();
                        break;
                    default:
                        break;
                }
            }
        };
    }

    private void scanDevices(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            m_handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (m_scanning) {
                        Log.d(TAG, "Nothing found, stop scanning.");
                        m_scanning = false;
                        m_btAdapter.getBluetoothLeScanner().stopScan(m_scanCallback);
                    }
                }
            }, SCAN_PERIOD);

            m_scanning = true;
            m_btAdapter.getBluetoothLeScanner().startScan(m_scanCallback);
            Log.d(TAG, "Start scanning...");
        } else {
            m_scanning = false;
            m_btAdapter.getBluetoothLeScanner().stopScan(m_scanCallback);
        }
    }

    protected void closeDeviceConnection() {
        m_btGatt.close();

        m_btGattCharacteristic = null;
        m_btGattService = null;
        m_btGatt = null;
        m_btDevice = null;
    }

    protected void updateBtnConnect() {
        if (m_connected) {
            m_btnConnect.setText(getString(R.string.disconnect));
        }
        else {
            m_btnConnect.setText(getString(R.string.connect));
        }
    }

    private void send() {
        String data = computeData();

        Log.d(TAG, "New value: " + data);
        m_btGattCharacteristic.setValue(data);
        m_btGatt.writeCharacteristic(m_btGattCharacteristic);
    }

    protected String computeData() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = m_context.registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level / (float)scale;
        int battery = (int) batteryPct * 100;

        long availableExternalMemorySize = getAvailableExternalMemorySize();
        long totalExternalMemorySize = getTotalExternalMemorySize();

        double diskAvailableDouble = 0.0f;
        if (totalExternalMemorySize > 0) {
            diskAvailableDouble = (double) availableExternalMemorySize / (double) totalExternalMemorySize;
        }

        int diskUsage = (int) ((1.0f - diskAvailableDouble) * 100.0f);

        return
                Integer.toString(battery)
                +   TAG_BATTERY_SEPARATOR
                +   Integer.toString(diskUsage)
                +   TAG_DISK_SEPARATOR
                +   '\n';
    }

    public static boolean externalMemoryAvailable() {
        return android.os.Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);
    }

    public static long getAvailableExternalMemorySize() {
        if (externalMemoryAvailable()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long availableBlocks = stat.getAvailableBlocksLong();
            return availableBlocks;
        } else {
            return 0;
        }
    }

    public static long getTotalExternalMemorySize() {
        if (externalMemoryAvailable()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long totalBlocks = stat.getBlockCountLong();
            return totalBlocks;
        } else {
            return 0;
        }
    }
}

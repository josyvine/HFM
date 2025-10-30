package com.hfm.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ShareHubActivity extends Activity implements WifiP2pManager.ChannelListener, WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener {

    private static final String TAG = "ShareHubActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 100;
    private static final int CATEGORY_PICKER_REQUEST_CODE = 200;
    public static final String ACTION_DISCONNECT_WIFI_P2P = "com.hfm.app.DISCONNECT_WIFI_P2P";


    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver = null;
    private final IntentFilter intentFilter = new IntentFilter();
    private boolean isWifiP2pEnabled = false;

    private WifiP2pInfo connectionInfo;
    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    private DeviceListAdapter deviceListAdapter;

    private TextView statusTextView;
    private Button sendButton, receiveButton;
    private RecyclerView devicesRecyclerView;

    private boolean isTransferInProgress = false;
    private BroadcastReceiver disconnectReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_hub);

        initializeViews();
        setupWifiDirect();
        setupListeners();
        setupDisconnectReceiver();
    }

    private void initializeViews() {
        statusTextView = findViewById(R.id.status_text);
        sendButton = findViewById(R.id.button_send);
        receiveButton = findViewById(R.id.button_receive);
        devicesRecyclerView = findViewById(R.id.devices_recycler_view);

        deviceListAdapter = new DeviceListAdapter(this, peers);
        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        devicesRecyclerView.setAdapter(deviceListAdapter);
    }

    private void setupWifiDirect() {
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
    }

    private void setupListeners() {
        sendButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					discoverPeers();
				}
			});

        receiveButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					createGroup();
				}
			});
    }

    private void setupDisconnectReceiver() {
        disconnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_DISCONNECT_WIFI_P2P.equals(intent.getAction())) {
                    disconnect();
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(disconnectReceiver, new IntentFilter(ACTION_DISCONNECT_WIFI_P2P));
    }


    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    public void setTransferStatus(boolean status) {
        this.isTransferInProgress = status;
    }

    private void discoverPeers() {
        if (!isWifiP2pEnabled) {
            Toast.makeText(this, "Enable P2P from settings", Toast.LENGTH_SHORT).show();
            return;
        }
        statusTextView.setText("Discovering devices...");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)) {
             Toast.makeText(this, "Permissions are required. Please grant them and try again.", Toast.LENGTH_SHORT).show();
             checkPermissions();
             return;
        }
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
				@Override
				public void onSuccess() {
					Toast.makeText(ShareHubActivity.this, "Discovery Initiated", Toast.LENGTH_SHORT).show();
				}

				@Override
				public void onFailure(int reasonCode) {
					Toast.makeText(ShareHubActivity.this, "Discovery Failed: " + reasonCode, Toast.LENGTH_SHORT).show();
				}
			});
    }

    private void createGroup() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "Permissions are required. Please grant them and try again.", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
				@Override
				public void onSuccess() {
					statusTextView.setText("Hosting. Ready to receive files.");
					Toast.makeText(ShareHubActivity.this, "Device is now discoverable.", Toast.LENGTH_SHORT).show();
				}

				@Override
				public void onFailure(int reason) {
					Toast.makeText(ShareHubActivity.this, "Could not create group: " + reason, Toast.LENGTH_SHORT).show();
				}
			});
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
        List<WifiP2pDevice> refreshedPeers = new ArrayList<WifiP2pDevice>(peerList.getDeviceList());
        if (!refreshedPeers.equals(peers)) {
            peers.clear();
            peers.addAll(refreshedPeers);
            deviceListAdapter.notifyDataSetChanged();
            if (peers.size() == 0) {
                Log.d(TAG, "No devices found");
                statusTextView.setText("No devices found.");
                return;
            }
        }
    }

    public void connect(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)) {
             Toast.makeText(this, "Permissions are required. Please grant them and try again.", Toast.LENGTH_SHORT).show();
             checkPermissions();
             return;
        }
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
				@Override
				public void onSuccess() {
					// WiFiDirectBroadcastReceiver will notify us.
				}

				@Override
				public void onFailure(int reason) {
					Toast.makeText(ShareHubActivity.this, "Connect failed. Retry.", Toast.LENGTH_SHORT).show();
				}
			});
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (isTransferInProgress) {
            return;
        }

        this.connectionInfo = info;

        if (info.groupFormed && !info.isGroupOwner) {
            statusTextView.setText("Connected. Select files to send.");
            Intent intent = new Intent(this, CategoryPickerActivity.class);
            startActivityForResult(intent, CATEGORY_PICKER_REQUEST_CODE);
        } else if (info.groupFormed) {
            isTransferInProgress = true;
            statusTextView.setText("Connected. Waiting for files...");
            Intent serviceIntent = new Intent(this, FileTransferService.class);
            serviceIntent.setAction(FileTransferService.ACTION_RECEIVE_FILES);
            serviceIntent.putExtra(FileTransferService.EXTRA_GROUP_OWNER_ADDRESS, info.groupOwnerAddress.getHostAddress());
            startService(serviceIntent);

            Intent progressIntent = new Intent(this, TransferProgressActivity.class);
            progressIntent.setAction(FileTransferService.ACTION_RECEIVE_FILES);
            startActivity(progressIntent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CATEGORY_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> filePaths = data.getStringArrayListExtra("picked_files");
            if (filePaths != null && !filePaths.isEmpty()) {
                if (this.connectionInfo != null && this.connectionInfo.groupOwnerAddress != null) {
                    isTransferInProgress = true;
                    Intent serviceIntent = new Intent(this, FileTransferService.class);
                    serviceIntent.setAction(FileTransferService.ACTION_SEND_FILES);
                    serviceIntent.putStringArrayListExtra(FileTransferService.EXTRA_FILE_PATHS, filePaths);
                    serviceIntent.putExtra(FileTransferService.EXTRA_GROUP_OWNER_ADDRESS, this.connectionInfo.groupOwnerAddress.getHostAddress());
                    startService(serviceIntent);

                    Intent progressIntent = new Intent(ShareHubActivity.this, TransferProgressActivity.class);
                    progressIntent.putStringArrayListExtra(FileTransferService.EXTRA_FILE_PATHS, filePaths);
                    startActivity(progressIntent);
                } else {
                    Toast.makeText(this, "Connection lost. Please reconnect and try again.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    public void disconnect() {
        if (manager != null && channel != null) {
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
					@Override
					public void onSuccess() {
						Log.d(TAG, "P2P group removed.");
					}

					@Override
					public void onFailure(int reason) {
						Log.d(TAG, "P2P group removal failed. Reason: " + reason);
					}
				});
        }
    }


    public void resetDeviceList() {
        peers.clear();
        deviceListAdapter.notifyDataSetChanged();
        statusTextView.setText("Ready to connect");
        this.connectionInfo = null;
    }

    public void updateThisDevice(WifiP2pDevice device) {
        // You can update UI with this device's info if needed
    }

    @Override
    public void onChannelDisconnected() {
        Toast.makeText(this, "Channel disconnected. Try again.", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermissions()) {
            receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
            registerReceiver(receiver, intentFilter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (disconnectReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(disconnectReceiver);
        }
    }

    private boolean checkPermissions() {
        // --- THIS IS THE FIX ---
        // This method now robustly checks for ALL permissions needed for sharing AND saving files.
        List<String> permissionsToRequest = new ArrayList<String>();

        // 1. Check for modern Storage Permission (All Files Access)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("HFM Share needs 'All Files Access' permission to save received files. Please grant this permission in the next screen.")
                    .setPositiveButton("Grant", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.addCategory("android.intent.category.DEFAULT");
                                intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                                startActivity(intent);
                            } catch (Exception e) {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivity(intent);
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return false; // Stop here, user must grant permission first.
            }
        } else { // Android 10 and below
             if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        
        // 2. Check for Location and Wi-Fi permissions
        String[] wifiPermissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        };
        for (String permission : wifiPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        
        // 3. Check for new Wi-Fi permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
        }

        // 4. If any standard permissions are missing, request them.
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                // Permissions were granted, we can now safely register the receiver.
                receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
                registerReceiver(receiver, intentFilter);
            } else {
                Toast.makeText(this, "Permissions are required for this feature.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {
        private Context context;
        private List<WifiP2pDevice> devices;

        public DeviceListAdapter(Context context, List<WifiP2pDevice> devices) {
            this.context = context;
            this.devices = devices;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.list_item_device, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final WifiP2pDevice device = devices.get(position);
            holder.deviceName.setText(device.deviceName);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						((ShareHubActivity) context).connect(device);
					}
				});
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            ImageView deviceIcon;
            TextView deviceName;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                deviceIcon = itemView.findViewById(R.id.device_icon);
                deviceName = itemView.findViewById(R.id.device_name);
            }
        }
    }
}
package com.hfm.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;

import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "HFM_MainActivity";
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 456;
    // --- UPDATE 1: Add a new request code for the notification permission ---
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 457;

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Launch the Dashboard Activity as a popup on start
        startActivity(new Intent(this, DashboardActivity.class));

        webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webView.setBackgroundColor(0x00000000);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // --- UPDATE 2: Start the permission request sequence ---
        requestFilePermissions();
        webView.loadUrl("file:///android_asset/webview-app.html");
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void requestFilePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            if (!Environment.isExternalStorageManager()) {
                // For Android 11+, we need to guide the user to the settings screen
                // to grant "All Files Access".
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivityForResult(intent, STORAGE_PERMISSION_REQUEST_CODE);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, STORAGE_PERMISSION_REQUEST_CODE);
                }
            } else {
                // If we already have file permission, proceed to check for notification permission.
                requestNotificationPermission();
            }
        } else { // Android 10 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // For older versions, use the standard runtime permission dialog.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST_CODE);
            } else {
                // If we already have file permission, proceed to check for notification permission.
                requestNotificationPermission();
            }
        }
    }

    // --- UPDATE 3: Add new method to request notification permission ---
    private void requestNotificationPermission() {
        // Notification permission is only required on Android 13 (API 33) and above.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // If permission is not granted, request it from the user.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    // --- UPDATE 4: Re-implement onActivityResult to handle the result from the settings screen ---
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // Permission has been granted. Now check for notification permission.
                    requestNotificationPermission();
                } else {
                    Toast.makeText(this, "All Files Access permission is required for the app to function correctly.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    // --- UPDATE 5: Implement onRequestPermissionsResult to handle dialog results ---
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            // This handles the result for older Android versions (pre-Android 11)
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Storage permission granted, now check for notification permission.
                requestNotificationPermission();
            } else {
                Toast.makeText(this, "Storage permission is required for the app to function.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            // This handles the result for the notification permission on Android 13+
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications will be disabled.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void log(String message) {
            Log.d("HFMApp_WebView", message);
        }

        @JavascriptInterface
        public void openDashboard() {
            Log.d("HFMApp_WebView", "openDashboard() called. Relaunching DashboardActivity.");
            Intent intent = new Intent(mContext, DashboardActivity.class);
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void openSearch() {
            Log.d("HFMApp_WebView", "openSearch() called. Launching SearchActivity.");
            Intent intent = new Intent(mContext, SearchActivity.class);
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void openMassDelete() {
            Log.d("HFMApp_WebView", "openMassDelete() called. Launching MassDeleteActivity.");
            Intent intent = new Intent(mContext, MassDeleteActivity.class);
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void openRecycleBin() {
            Log.d("HFMApp_WebView", "openRecycleBin() called. Launching RecycleBinActivity.");
            Intent intent = new Intent(mContext, RecycleBinActivity.class);
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void openContactForm() {
            Log.d("HFMApp_WebView", "openContactForm() called. Launching ContactActivity.");
            Intent intent = new Intent(mContext, ContactActivity.class);
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void clearCache() {
            Log.d("HFMApp_WebView", "clearCache() called. Launching CacheCleanerActivity.");
            Intent intent = new Intent(mContext, CacheCleanerActivity.class);
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void openReader() {
            Log.d("HFMApp_WebView", "openReader() called. Launching ReaderActivity.");
            Intent intent = new Intent(mContext, ReaderActivity.class);
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void openStorageMap() {
            Log.d("HFMApp_WebView", "openStorageMap() called. Launching StorageMapActivity.");
            Intent intent = new Intent(mContext, StorageMapActivity.class);
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void onHideIconTapped() {
            Log.d(TAG, "Hide icon tapped. Checking for existing rituals...");
            RitualManager ritualManager = new RitualManager();
            List<RitualManager.Ritual> rituals = ritualManager.loadRituals(mContext);

            if (rituals == null || rituals.isEmpty()) {
                Log.d(TAG, "No rituals found. Launching FileHiderActivity.");
                Intent intent = new Intent(mContext, FileHiderActivity.class);
                mContext.startActivity(intent);
            } else {
                Log.d(TAG, rituals.size() + " rituals found. Launching RitualListActivity.");
                Intent intent = new Intent(mContext, RitualListActivity.class);
                mContext.startActivity(intent);
            }
        }

        @JavascriptInterface
        public void setTheme(final String themeName) {
            Log.d(TAG, "setTheme() called from WebView with theme: " + themeName);
            ThemeManager.setTheme(mContext, themeName);
            new android.os.Handler(mContext.getMainLooper()).post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(mContext, "Theme changed. Please restart the app to see the full effect.", Toast.LENGTH_LONG).show();
					}
				});
        }

        @JavascriptInterface
        public void openShareHub() {
            Log.d("HFMApp_WebView", "openShareHub() called. Launching ShareHubActivity.");
            Intent intent = new Intent(mContext, ShareHubActivity.class);
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void openApiKeyDialog() {
            runOnUiThread(new Runnable() {
					@Override
					public void run() {
						AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
						LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
						View dialogView = inflater.inflate(R.layout.dialog_api_key, null);
						final EditText apiKeyInput = dialogView.findViewById(R.id.edit_text_api_key);

						String currentKey = ApiKeyManager.getApiKey(mContext);
						if (currentKey != null) {
							apiKeyInput.setText(currentKey);
						}

						builder.setView(dialogView)
							.setPositiveButton("Save", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int id) {
									String newKey = apiKeyInput.getText().toString().trim();
									ApiKeyManager.saveApiKey(mContext, newKey);
									if (newKey.isEmpty()) {
										Toast.makeText(mContext, "API Key cleared.", Toast.LENGTH_SHORT).show();
									} else {
										Toast.makeText(mContext, "API Key saved.", Toast.LENGTH_SHORT).show();
									}
								}
							})
							.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int id) {
									dialog.cancel();
								}
							});
						builder.create().show();
					}
				});
        }
    }
}
package com.hfm.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ImageViewerActivity extends Activity {

    public static final String EXTRA_FILE_PATH_LIST = "file_path_list";
    public static final String EXTRA_CURRENT_INDEX = "current_index";
    public static final String RESULT_FILE_DELETED = "file_deleted";

    private ImageView fullImageView;
    private TextView fileNameTextView;
    private ImageButton deleteButton, closeButton, prevButton, nextButton;

    private ImageButton openWithButton;
    private ImageButton shareButton;

    private RelativeLayout deletionProgressLayout;
    private ProgressBar deletionProgressBar;
    private TextView deletionProgressText;

    private ArrayList<String> mFilePaths;
    private int mCurrentIndex;
    private boolean mFileDeleted = false;

    private BroadcastReceiver deleteCompletionReceiver;
    private BroadcastReceiver compressionBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        initializeViews();

        mFilePaths = getIntent().getStringArrayListExtra(EXTRA_FILE_PATH_LIST);
        mCurrentIndex = getIntent().getIntExtra(EXTRA_CURRENT_INDEX, -1);

        if (mFilePaths == null || mFilePaths.isEmpty() || mCurrentIndex == -1) {
            Toast.makeText(this, "Error: Image data not provided.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupListeners();
        setupBroadcastReceivers();
        loadImage(mCurrentIndex);
    }

    private void initializeViews() {
        fullImageView = findViewById(R.id.image_view_full);
        fileNameTextView = findViewById(R.id.file_name_image_viewer);
        deleteButton = findViewById(R.id.delete_button_image_viewer);
        closeButton = findViewById(R.id.close_button_image_viewer);
        prevButton = findViewById(R.id.prev_button_image_viewer);
        nextButton = findViewById(R.id.next_button_image_viewer);
        openWithButton = findViewById(R.id.open_with_button_image);
        shareButton = findViewById(R.id.share_button_image);
        deletionProgressLayout = findViewById(R.id.deletion_progress_layout);
        deletionProgressBar = findViewById(R.id.deletion_progress_bar);
        deletionProgressText = findViewById(R.id.deletion_progress_text);
    }

    private void setupListeners() {
        closeButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onBackPressed();
				}
			});

        deleteButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showFileActionDialog();
				}
			});

        prevButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mCurrentIndex > 0) {
						loadImage(mCurrentIndex - 1);
					}
				}
			});

        nextButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mCurrentIndex < mFilePaths.size() - 1) {
						loadImage(mCurrentIndex + 1);
					}
				}
			});

        shareButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					shareFile();
				}
			});

        openWithButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openWith();
				}
			});
    }

    private void loadImage(int index) {
        if (index < 0 || index >= mFilePaths.size()) {
            return;
        }

        mCurrentIndex = index;
        String filePath = mFilePaths.get(mCurrentIndex);
        File file = new File(filePath);
        fileNameTextView.setText(file.getName());
        fullImageView.setImageURI(Uri.fromFile(file));
        updateNavigationButtons();
    }

    private void updateNavigationButtons() {
        prevButton.setEnabled(mCurrentIndex > 0);
        nextButton.setEnabled(mCurrentIndex < mFilePaths.size() - 1);
    }

    private void showFileActionDialog() {
        final CharSequence[] options = {"Details", "Compress", "Hide", "Move to Recycle Bin", "Delete Permanently"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose an action");
        builder.setItems(options, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
						case 0: // Details
							showDetailsDialog();
							break;
						case 1: // Compress
							compressFile();
							break;
						case 2: // Hide
							hideFile();
							break;
						case 3: // Move to Recycle
							moveToRecycleBin();
							break;
						case 4: // Delete
							performFileDeletion();
							break;
					}
				}
			});
        builder.show();
    }

    private void showDetailsDialog() {
        final List<File> files = new ArrayList<>();
        files.add(new File(mFilePaths.get(mCurrentIndex)));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_details, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        final TextView basicDetailsText = dialogView.findViewById(R.id.details_text_basic);
        final TextView aiDetailsText = dialogView.findViewById(R.id.details_text_ai);
        final ProgressBar progressBar = dialogView.findViewById(R.id.details_progress_bar);
        final Button moreButton = dialogView.findViewById(R.id.details_button_more);
        final Button copyButton = dialogView.findViewById(R.id.details_button_copy);
        final Button closeButton = dialogView.findViewById(R.id.details_button_close);

        final AlertDialog dialog = builder.create();

        File file = files.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(file.getName()).append("\n");
        sb.append("Path: ").append(file.getAbsolutePath()).append("\n");
        sb.append("Size: ").append(Formatter.formatFileSize(this, file.length())).append("\n");
        sb.append("Last Modified: ").append(new Date(file.lastModified()).toString());
        basicDetailsText.setText(sb.toString());

        final GeminiAnalyzer analyzer = new GeminiAnalyzer(this, aiDetailsText, progressBar, copyButton);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        moreButton.setEnabled(ApiKeyManager.getApiKey(this) != null && isConnected);

        moreButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					analyzer.analyze(files);
				}
			});

        copyButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
					ClipData clip = ClipData.newPlainText("AI Summary", aiDetailsText.getText());
					clipboard.setPrimaryClip(clip);
					Toast.makeText(ImageViewerActivity.this, "Summary copied to clipboard.", Toast.LENGTH_SHORT).show();
				}
			});

        closeButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					dialog.dismiss();
				}
			});

        dialog.show();
    }

    private void compressFile() {
        File currentFile = new File(mFilePaths.get(mCurrentIndex));
        File parentDir = currentFile.getParentFile();
        if (parentDir != null) {
            List<File> filesToCompress = new ArrayList<>();
            filesToCompress.add(currentFile);
            ArchiveUtils.startCompression(this, filesToCompress, parentDir);
            Toast.makeText(this, "Compression started in background.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Cannot determine destination for archive.", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideFile() {
        String filePath = mFilePaths.get(mCurrentIndex);
        ArrayList<File> filesToHide = new ArrayList<>();
        filesToHide.add(new File(filePath));

        Intent intent = new Intent(this, FileHiderActivity.class);
        intent.putExtra(RitualRecordTapsActivity.EXTRA_FILES_TO_HIDE, (Serializable) filesToHide);
        startActivity(intent);

        mFileDeleted = true;
        mFilePaths.remove(mCurrentIndex);

        if (mFilePaths.isEmpty()) {
            onBackPressed();
        } else if (mCurrentIndex >= mFilePaths.size()) {
            loadImage(mFilePaths.size() - 1);
        } else {
            loadImage(mCurrentIndex);
        }
    }


    private void moveToRecycleBin() {
        String filePath = mFilePaths.get(mCurrentIndex);
        File sourceFile = new File(filePath);
        if (!sourceFile.exists()) {
            Toast.makeText(this, "File not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        File recycleBinDir = new File(Environment.getExternalStorageDirectory(), "HFMRecycleBin");
        if (!recycleBinDir.exists()) {
            if (!recycleBinDir.mkdir()) {
                Toast.makeText(this, "Failed to create Recycle Bin folder.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        File destFile = new File(recycleBinDir, sourceFile.getName());
        if (destFile.exists()) {
            String name = sourceFile.getName();
            String extension = "";
            int dotIndex = name.lastIndexOf(".");
            if (dotIndex > 0) {
                extension = name.substring(dotIndex);
                name = name.substring(0, dotIndex);
            }
            destFile = new File(recycleBinDir, name + "_" + System.currentTimeMillis() + extension);
        }

        if (sourceFile.renameTo(destFile)) {
            Toast.makeText(this, "File moved to Recycle Bin.", Toast.LENGTH_SHORT).show();
            mFileDeleted = true;
            mFilePaths.remove(mCurrentIndex);
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(sourceFile)));
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)));

            if (mFilePaths.isEmpty()) {
                onBackPressed();
            } else if (mCurrentIndex >= mFilePaths.size()) {
                loadImage(mFilePaths.size() - 1);
            } else {
                loadImage(mCurrentIndex);
            }
        } else {
            Toast.makeText(this, "Failed to move file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void performFileDeletion() {
        String filePath = mFilePaths.get(mCurrentIndex);
        ArrayList<String> filesToDelete = new ArrayList<>();
        filesToDelete.add(filePath);

        deletionProgressLayout.setVisibility(View.VISIBLE);
        deletionProgressBar.setIndeterminate(true);
        deletionProgressText.setText("Deleting...");

        Intent intent = new Intent(this, DeleteService.class);
        intent.putStringArrayListExtra(DeleteService.EXTRA_FILES_TO_DELETE, filesToDelete);
        ContextCompat.startForegroundService(this, intent);
    }

    private void shareFile() {
        String filePath = mFilePaths.get(mCurrentIndex);
        File fileToShare = new File(filePath);
        if (!fileToShare.exists()) {
            Toast.makeText(this, "File not found.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri fileUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", fileToShare);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.setType(getMimeType(fileToShare.getAbsolutePath()));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share file via"));
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Error: Could not generate a sharable link for this file.", Toast.LENGTH_LONG).show();
        }
    }

    private void openWith() {
        String filePath = mFilePaths.get(mCurrentIndex);
        File fileToOpen = new File(filePath);
        if (!fileToOpen.exists()) {
            Toast.makeText(this, "File not found.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri fileUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", fileToOpen);
            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(fileUri, getMimeType(fileToOpen.getAbsolutePath()));
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(openIntent, "Open with"));
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Error: Could not generate a link to open this file.", Toast.LENGTH_LONG).show();
        }
    }

    private String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

    @Override
    public void onBackPressed() {
        if (mFileDeleted) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(RESULT_FILE_DELETED, true);
            setResult(Activity.RESULT_OK, resultIntent);
        } else {
            setResult(Activity.RESULT_CANCELED);
        }
        super.onBackPressed();
    }

    private void setupBroadcastReceivers() {
        deleteCompletionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                deletionProgressLayout.setVisibility(View.GONE);
                int deletedCount = intent.getIntExtra(DeleteService.EXTRA_DELETED_COUNT, 0);
                if (deletedCount > 0) {
                    mFileDeleted = true;
                    mFilePaths.remove(mCurrentIndex);

                    if (mFilePaths.isEmpty()) {
                        onBackPressed();
                    } else if (mCurrentIndex >= mFilePaths.size()) {
                        loadImage(mFilePaths.size() - 1);
                    } else {
                        loadImage(mCurrentIndex);
                    }
                } else {
					Toast.makeText(ImageViewerActivity.this, "Failed to delete the file.", Toast.LENGTH_SHORT).show();
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(deleteCompletionReceiver, new IntentFilter(DeleteService.ACTION_DELETE_COMPLETE));

        compressionBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Do nothing specific on compression complete in this screen
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(compressionBroadcastReceiver, new IntentFilter(CompressionService.ACTION_COMPRESSION_COMPLETE));
    }

	@Override
    protected void onDestroy() {
        if (deleteCompletionReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(deleteCompletionReceiver);
        }
        if (compressionBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(compressionBroadcastReceiver);
        }
        super.onDestroy();
    }
}

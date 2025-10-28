package com.hfm.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RecycleBinActivity extends Activity {

    private static final String RECYCLE_BIN_FOLDER_NAME = "HFMRecycleBin";

    private ImageButton backButton;
    private TextView titleTextView;
    private RecyclerView recyclerView;
    private TextView emptyView;

    private File currentDirectory;
    private final File rootStorageDir = Environment.getExternalStorageDirectory();
    private final File recycleBinDir = new File(rootStorageDir, RECYCLE_BIN_FOLDER_NAME);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recycle_bin);

        initializeViews();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Always start by showing the root directory containing the recycle bin
        currentDirectory = rootStorageDir;
        refreshList();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button_recycle_bin);
        titleTextView = findViewById(R.id.title_recycle_bin);
        recyclerView = findViewById(R.id.recycle_bin_recycler_view);
        emptyView = findViewById(R.id.empty_view_recycle_bin);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupListeners() {
        backButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					handleBackNavigation();
				}
			});
    }

    private void refreshList() {
        createRecycleBinIfNeeded();
        listFiles(currentDirectory);
    }

    private void createRecycleBinIfNeeded() {
        if (!recycleBinDir.exists()) {
            if (recycleBinDir.mkdir()) {
                // Folder created successfully
            } else {
                Toast.makeText(this, "Failed to create Recycle Bin folder.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void listFiles(File directory) {
        currentDirectory = directory;

        if (directory.equals(rootStorageDir)) {
            // We are at the root, so we only want to show the recycle bin folder
            titleTextView.setText("Recycle Bin");
            List<File> singleFolderList = new ArrayList<>();
            if (recycleBinDir.exists()) {
                singleFolderList.add(recycleBinDir);
            }
            setupAdapter(singleFolderList);
        } else {
            // We are inside the recycle bin folder
            titleTextView.setText(directory.getName());
            File[] files = directory.listFiles();
            List<File> fileList = new ArrayList<>();
            if (files != null) {
                fileList.addAll(Arrays.asList(files));
            }
            // Sort files alphabetically
            Collections.sort(fileList, new Comparator<File>() {
					@Override
					public int compare(File f1, File f2) {
						return f1.getName().compareToIgnoreCase(f2.getName());
					}
				});
            setupAdapter(fileList);
        }
    }

    private void setupAdapter(List<File> files) {
        if (files.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        RecycleBinAdapter adapter = new RecycleBinAdapter(this, files, new RecycleBinAdapter.OnItemClickListener() {
				@Override
				public void onItemClick(File file) {
					if (file.isDirectory()) {
						listFiles(file);
					} else {
						Toast.makeText(RecycleBinActivity.this, "Cannot open files here. Restore feature coming soon.", Toast.LENGTH_SHORT).show();
					}
				}

				@Override
				public void onItemLongClick(File file) {
					if (file.equals(recycleBinDir)) {
						showDeleteConfirmationDialog(file);
					} else {
						Toast.makeText(RecycleBinActivity.this, "Long press to empty the entire bin from the main view.", Toast.LENGTH_SHORT).show();
					}
				}
			});
        recyclerView.setAdapter(adapter);
    }

    private void showDeleteConfirmationDialog(final File folder) {
        new AlertDialog.Builder(this)
			.setTitle("Empty Recycle Bin")
			.setMessage("Are you sure you want to permanently empty the recycle bin? All files inside will be deleted forever.")
			.setPositiveButton("Empty", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					deleteRecursive(folder);
					refreshList();
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private void handleBackNavigation() {
        if (currentDirectory != null && !currentDirectory.equals(rootStorageDir)) {
            File parent = currentDirectory.getParentFile();
            if (parent != null) {
                listFiles(parent);
            }
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }
}


package com.hfm.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FolderListActivity extends Activity {

    public static final String EXTRA_FOLDER_NAME = "folder_name";
    public static final String EXTRA_FILE_LIST = "file_list";
    // --- CRASH FIX: New extra to receive the temporary file name ---
    public static final String EXTRA_TEMP_FILE_NAME = "temp_file_name";
    private static final int FILE_DELETE_REQUEST_CODE = 123;

    private TextView titleTextView;
    private ImageButton backButton;
    private ListView folderListView;
    private TextView emptyView;

    private String categoryName;
    private Map<String, List<File>> folderMap;
    private List<FolderItem> folderItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_list);

        initializeViews();

        Intent intent = getIntent();
        categoryName = intent.getStringExtra(DashboardActivity.EXTRA_CATEGORY_NAME);
        // --- CRASH FIX: Get the temp file name instead of the huge serializable object ---
        String tempFileName = intent.getStringExtra(EXTRA_TEMP_FILE_NAME);

        if (categoryName == null || tempFileName == null) {
            Toast.makeText(this, "Error: Invalid data received.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // --- CRASH FIX: Load the data from the cache file ---
        folderMap = loadFolderMapFromCache(this, tempFileName);

        if (folderMap == null) {
            Toast.makeText(this, "Error: Could not load the file list.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        titleTextView.setText(categoryName);
        setupListeners();
        populateFolderList();
    }

    /**
     * CRASH FIX: This new method loads the folder map from a temporary file in the cache,
     * then deletes the file.
     * @param context The application context.
     * @param tempFileName The name of the temporary file to read.
     * @return The loaded map, or null on failure.
     */
    private Map<String, List<File>> loadFolderMapFromCache(Context context, String tempFileName) {
        File tempFile = new File(context.getCacheDir(), tempFileName);
        if (!tempFile.exists()) {
            return null;
        }
        Map<String, List<File>> loadedMap = null;
        try {
            FileInputStream fis = new FileInputStream(tempFile);
            ObjectInputStream ois = new ObjectInputStream(fis);
            loadedMap = (HashMap<String, List<File>>) ois.readObject();
            ois.close();
            fis.close();
        } catch (Exception e) {
            Log.e("FolderListActivity", "Failed to load folder map from cache", e);
        } finally {
            // Ensure the temporary file is deleted after being read or if an error occurs
            tempFile.delete();
        }
        return loadedMap;
    }

    private void initializeViews() {
        titleTextView = findViewById(R.id.title_folder_list);
        backButton = findViewById(R.id.back_button_folder_list);
        folderListView = findViewById(R.id.folder_list_view);
        emptyView = findViewById(R.id.empty_view_folder_list);
    }

    private void setupListeners() {
        backButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					finish();
				}
			});

        folderListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					FolderItem selectedFolder = folderItems.get(position);
					ArrayList<File> files = new ArrayList<>(selectedFolder.getFiles());

					Intent intent = new Intent(FolderListActivity.this, FileDeleteActivity.class);
					intent.putExtra(EXTRA_FOLDER_NAME, selectedFolder.getName());
					intent.putExtra(EXTRA_FILE_LIST, files);
					startActivityForResult(intent, FILE_DELETE_REQUEST_CODE);
				}
			});
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_DELETE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Intent resultIntent = new Intent();
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        }
    }

    private void populateFolderList() {
        folderItems = new ArrayList<>();
        for (Map.Entry<String, List<File>> entry : folderMap.entrySet()) {
            folderItems.add(new FolderItem(entry.getKey(), entry.getValue()));
        }

        Collections.sort(folderItems, new Comparator<FolderItem>() {
				@Override
				public int compare(FolderItem o1, FolderItem o2) {
					return o1.getName().compareToIgnoreCase(o2.getName());
				}
			});

        if (folderItems.isEmpty()) {
            folderListView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            folderListView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            FolderAdapter adapter = new FolderAdapter(this, folderItems);
            folderListView.setAdapter(adapter);
        }
    }

    private static class FolderItem {
        private String name;
        private List<File> files;

        FolderItem(String name, List<File> files) {
            this.name = name;
            this.files = files;
        }

        public String getName() {
            return name;
        }

        public List<File> getFiles() {
            return files;
        }

        public int getFileCount() {
            return files != null ? files.size() : 0;
        }
    }

    private class FolderAdapter extends ArrayAdapter<FolderItem> {
        public FolderAdapter(Context context, List<FolderItem> folders) {
            super(context, 0, folders);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FolderItem folderItem = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_folder, parent, false);
            }

            TextView folderName = convertView.findViewById(R.id.folder_name);
            TextView fileCount = convertView.findViewById(R.id.folder_file_count);

            if (folderItem != null) {
                folderName.setText(folderItem.getName());
                fileCount.setText("(" + folderItem.getFileCount() + " files)");
            }

            return convertView;
        }
    }
}


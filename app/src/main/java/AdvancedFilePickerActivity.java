package com.hfm.app;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AdvancedFilePickerActivity extends Activity {

    // UI Elements
    private ImageButton backButton, filterButton;
    private TextView pathTextView, selectionCountTextView;
    private EditText searchInput;
    private RecyclerView fileRecyclerView;
    private Button sendButton;
    private LinearLayout loadingView;

    private AdvancedFilePickerAdapter adapter;
    private File currentDirectory;
    private final File rootDirectory = Environment.getExternalStorageDirectory();
    private String currentFilterType = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advanced_file_picker);

        initializeViews();
        setupListeners();
        setupRecyclerView();

        // Start at the root of external storage
        navigateTo(rootDirectory);
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button_advanced_picker);
        filterButton = findViewById(R.id.filter_button_advanced_picker);
        pathTextView = findViewById(R.id.path_text_advanced_picker);
        selectionCountTextView = findViewById(R.id.selection_count_text_advanced_picker);
        searchInput = findViewById(R.id.search_input_advanced_picker);
        fileRecyclerView = findViewById(R.id.file_recycler_view_advanced);
        sendButton = findViewById(R.id.button_send_advanced_picker);
        loadingView = findViewById(R.id.loading_view_advanced_picker);
    }

    private void setupRecyclerView() {
        // The adapter is initialized with an empty list. It will be populated by navigateTo().
        adapter = new AdvancedFilePickerAdapter(this, new ArrayList<File>(), new AdvancedFilePickerAdapter.OnItemClickListener() {
				@Override
				public void onFileClicked(AdvancedFilePickerAdapter.FileItem item) {
					// This is handled by the checkbox listener in the adapter now
					updateSelectionCount();
				}

				@Override
				public void onFolderClicked(File folder) {
					navigateTo(folder);
				}

				@Override
				public void onSelectionChanged() {
					updateSelectionCount();
				}
			});
        fileRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        fileRecyclerView.setAdapter(adapter);
    }

    private void setupListeners() {
        backButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					handleBackNavigation();
				}
			});

        filterButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showFilterMenu(v);
				}
			});

        searchInput.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
					if (adapter != null) {
						adapter.getFilter().filter(s);
					}
				}

				@Override
				public void afterTextChanged(Editable s) {}
			});

        sendButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					sendSelectedFiles();
				}
			});
    }

    private void navigateTo(File directory) {
        new ListFilesTask().execute(directory);
    }

    private void handleBackNavigation() {
        if (currentDirectory != null && !currentDirectory.equals(rootDirectory)) {
            File parent = currentDirectory.getParentFile();
            if (parent != null) {
                navigateTo(parent);
            }
        } else {
            // If we are at the root, just finish the activity
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    private void updateSelectionCount() {
        int count = 0;
        if (adapter != null) {
            for (AdvancedFilePickerAdapter.FileItem item : adapter.getItems()) {
                if (!item.getFile().isDirectory() && item.isSelected()) {
                    count++;
                }
            }
        }
        selectionCountTextView.setText(count + " files selected");
    }

    private void sendSelectedFiles() {
        ArrayList<String> selectedPaths = new ArrayList<>();
        if (adapter != null) {
            for (AdvancedFilePickerAdapter.FileItem item : adapter.getItems()) {
                if (!item.getFile().isDirectory() && item.isSelected()) {
                    selectedPaths.add(item.getFile().getAbsolutePath());
                }
            }
        }

        if (selectedPaths.isEmpty()) {
            Toast.makeText(this, "No files selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent resultIntent = new Intent();
        resultIntent.putStringArrayListExtra("picked_files", selectedPaths);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void showFilterMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        // We can reuse the existing filter_menu.xml
        popup.getMenuInflater().inflate(R.menu.filter_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					int itemId = item.getItemId();
					if (itemId == R.id.filter_all) currentFilterType = "all";
					else if (itemId == R.id.filter_images) currentFilterType = "images";
					else if (itemId == R.id.filter_videos) currentFilterType = "videos";
					else if (itemId == R.id.filter_documents) currentFilterType = "documents";
					else if (itemId == R.id.filter_archives) currentFilterType = "archives";
					else if (itemId == R.id.filter_other) currentFilterType = "other";

					// Re-run the listing task to apply the filter
					navigateTo(currentDirectory);
					return true;
				}
			});
        popup.show();
    }

    private class ListFilesTask extends AsyncTask<File, Void, List<File>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loadingView.setVisibility(View.VISIBLE);
            fileRecyclerView.setVisibility(View.GONE);
        }

        @Override
        protected List<File> doInBackground(File... dirs) {
            File directory = dirs[0];
            currentDirectory = directory;

            File[] files = directory.listFiles(new FileFilter() {
					@Override
					public boolean accept(File file) {
						if (file.isHidden()) {
							return false; // Skip hidden files and folders
						}
						if (file.isDirectory()) {
							return true; // Always show directories
						}
						return isFileTypeMatch(file.getName()); // Check if file type matches filter
					}
				});

            List<File> fileList = new ArrayList<>();
            if (files != null) {
                fileList.addAll(Arrays.asList(files));
            }

            // Sort files: folders first, then alphabetically
            Collections.sort(fileList, new Comparator<File>() {
					@Override
					public int compare(File f1, File f2) {
						if (f1.isDirectory() && !f2.isDirectory()) {
							return -1;
						} else if (!f1.isDirectory() && f2.isDirectory()) {
							return 1;
						} else {
							return f1.getName().compareToIgnoreCase(f2.getName());
						}
					}
				});
            return fileList;
        }

        @Override
        protected void onPostExecute(List<File> fileList) {
            super.onPostExecute(fileList);
            pathTextView.setText(currentDirectory.getAbsolutePath());

            adapter = new AdvancedFilePickerAdapter(AdvancedFilePickerActivity.this, fileList, new AdvancedFilePickerAdapter.OnItemClickListener() {
					@Override
					public void onFileClicked(AdvancedFilePickerAdapter.FileItem item) {
						updateSelectionCount();
					}

					@Override
					public void onFolderClicked(File folder) {
						navigateTo(folder);
					}

					@Override
					public void onSelectionChanged() {
						updateSelectionCount();
					}
				});
            fileRecyclerView.setAdapter(adapter);
            updateSelectionCount(); // Reset count to 0

            loadingView.setVisibility(View.GONE);
            fileRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private boolean isFileTypeMatch(String fileName) {
        if ("all".equals(currentFilterType)) {
            return true;
        }
        String extension = "";
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1).toLowerCase();
        }

        switch (currentFilterType) {
            case "images":
                return Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp").contains(extension);
            case "videos":
                return Arrays.asList("mp4", "3gp", "mkv", "webm", "avi").contains(extension);
            case "documents":
                return Arrays.asList("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt").contains(extension);
            case "archives":
                return Arrays.asList("zip", "rar", "7z", "tar", "gz").contains(extension);
            case "other":
                boolean isImage = Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp").contains(extension);
                boolean isVideo = Arrays.asList("mp4", "3gp", "mkv", "webm", "avi").contains(extension);
                boolean isDoc = Arrays.asList("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt").contains(extension);
                boolean isArchive = Arrays.asList("zip", "rar", "7z", "tar", "gz").contains(extension);
                return !isImage && !isVideo && !isDoc && !isArchive;
            default:
                return false;
        }
    }
}


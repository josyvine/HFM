package com.hfm.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileHiderAdapter extends RecyclerView.Adapter<FileHiderAdapter.FileViewHolder> implements Filterable {

    private static final String TAG = "FileHiderAdapter"; // Added for logging
    private final Context context;
    private List<FileItem> fileList; // This is the master list for the current filter type
    private List<FileItem> fileListFiltered; // This is the list currently displayed
    private final OnItemClickListener itemClickListener;
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(4);

    public interface OnItemClickListener {
        void onItemClick(int position);
        void onSelectionChanged();
    }

    public FileHiderAdapter(Context context, List<File> files, OnItemClickListener itemClickListener) {
        this.context = context;
        this.itemClickListener = itemClickListener;
        this.fileList = new ArrayList<>();
        for (File file : files) {
            this.fileList.add(new FileItem(file));
        }
        // --- FIX: Initialize fileListFiltered as a NEW list to prevent reference issues ---
        this.fileListFiltered = new ArrayList<>(this.fileList);
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.grid_item_file_delete, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final FileViewHolder holder, final int position) {
        // --- FIX: Add a bounds check to prevent IndexOutOfBoundsException ---
        if (position >= fileListFiltered.size()) {
            Log.e(TAG, "Attempted to bind a view for a position that is out of bounds: " + position + ". List size is " + fileListFiltered.size());
            return;
        }

        final FileItem item = fileListFiltered.get(position);
        final File file = item.getFile();

        holder.fileName.setText(file.getName());
        holder.thumbnailImage.setImageResource(android.R.color.darker_gray);
        holder.thumbnailImage.setTag(file.getAbsolutePath());
        holder.selectionOverlay.setVisibility(item.isSelected() ? View.VISIBLE : View.GONE);

        // Remove previous listener to prevent unwanted calls during binding
        holder.selectionCheckbox.setOnCheckedChangeListener(null);
        holder.selectionCheckbox.setChecked(item.isSelected());

        holder.selectionCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					item.setSelected(isChecked);
					holder.selectionOverlay.setVisibility(isChecked ? View.VISIBLE : View.GONE);
					if (itemClickListener != null) {
						itemClickListener.onSelectionChanged();
					}
				}
			});

        holder.itemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (itemClickListener != null) {
						// We need to find the original index in case the list is filtered
						int originalPosition = fileList.indexOf(item);
						if (originalPosition != -1) {
							itemClickListener.onItemClick(originalPosition);
						}
					}
				}
			});

        thumbnailExecutor.execute(new Runnable() {
				@Override
				public void run() {
					final Bitmap thumbnail = createThumbnail(file);
					if (holder.thumbnailImage.getTag().equals(file.getAbsolutePath())) {
						holder.thumbnailImage.post(new Runnable() {
								@Override
								public void run() {
									if (thumbnail != null) {
										holder.thumbnailImage.setImageBitmap(thumbnail);
									} else {
										holder.thumbnailImage.setImageResource(getIconForFileType(file.getName()));
									}
								}
							});
					}
				}
			});
    }

    private Bitmap createThumbnail(File file) {
        String path = file.getAbsolutePath();
        String name = file.getName().toLowerCase();
        Bitmap bitmap = null;

        try {
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".bmp") || name.endsWith(".webp")) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, options);
                options.inSampleSize = calculateInSampleSize(options, 150, 150);
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeFile(path, options);
            } else if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".3gp") || name.endsWith(".webm")) {
                bitmap = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
            }
        } catch (Exception e) {
            Log.e("FileHiderAdapter", "Error creating thumbnail for " + path, e);
        }
        return bitmap;
    }

    private int getIconForFileType(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx") || lowerFileName.endsWith(".pdf")) return android.R.drawable.ic_menu_save;
        if (lowerFileName.endsWith(".xls") || lowerFileName.endsWith(".xlsx")) return android.R.drawable.ic_menu_agenda;
        if (lowerFileName.endsWith(".ppt") || lowerFileName.endsWith(".pptx")) return android.R.drawable.ic_menu_slideshow;
        if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".rtf") || lowerFileName.endsWith(".log")) return android.R.drawable.ic_menu_view;
        if (lowerFileName.endsWith(".html") || lowerFileName.endsWith(".xml") || lowerFileName.endsWith(".js") || lowerFileName.endsWith(".css") || lowerFileName.endsWith(".java") || lowerFileName.endsWith(".py") || lowerFileName.endsWith(".c") || lowerFileName.endsWith(".cpp")) return android.R.drawable.ic_menu_edit;
        if (lowerFileName.endsWith(".zip") || lowerFileName.endsWith(".rar") || lowerFileName.endsWith(".7z")) return android.R.drawable.ic_menu_set_as;
        if (lowerFileName.endsWith(".mp3") || lowerFileName.endsWith(".wav") || lowerFileName.endsWith(".ogg")) return android.R.drawable.ic_media_play;
        return android.R.drawable.ic_menu_info_details;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    @Override
    public int getItemCount() {
        return fileListFiltered.size();
    }

    public List<FileItem> getItems() {
        return fileListFiltered;
    }

    public List<FileItem> getOriginalItems() {
        return fileList;
    }

    public void selectAll(boolean select) {
        for (FileItem item : fileListFiltered) {
            item.setSelected(select);
        }
        notifyDataSetChanged();
        if (itemClickListener != null) {
            itemClickListener.onSelectionChanged();
        }
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String charString = constraint.toString().toLowerCase();
                // --- FIX: Always create a new list for the results ---
                List<FileItem> filteredList = new ArrayList<>();
                if (charString.isEmpty()) {
                    filteredList.addAll(fileList);
                } else {
                    for (FileItem item : fileList) {
                        if (item.getFile().getName().toLowerCase().contains(charString) || item.getFile().getParentFile().getName().toLowerCase().contains(charString)) {
                            filteredList.add(item);
                        }
                    }
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = filteredList;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                // This is the only place fileListFiltered should be reassigned.
                fileListFiltered = (ArrayList<FileItem>) results.values;
                notifyDataSetChanged();
                // After filtering, update the selection count as the visible items have changed
                if (itemClickListener != null) {
                    itemClickListener.onSelectionChanged();
                }
            }
        };
    }

    public static class FileViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnailImage;
        TextView fileName;
        View selectionOverlay;
        CheckBox selectionCheckbox;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnailImage = itemView.findViewById(R.id.thumbnail_image_delete);
            fileName = itemView.findViewById(R.id.file_name_delete);
            selectionOverlay = itemView.findViewById(R.id.selection_overlay);
            selectionCheckbox = itemView.findViewById(R.id.selection_checkbox);
        }
    }

    public static class FileItem {
        private File file;
        private boolean isSelected;

        public FileItem(File file) {
            this.file = file;
            this.isSelected = false;
        }

        public File getFile() {
            return file;
        }

        public boolean isSelected() {
            return isSelected;
        }

        public void setSelected(boolean selected) {
            isSelected = selected;
        }
    }
}


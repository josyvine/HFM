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
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaPickerAdapter extends RecyclerView.Adapter<MediaPickerAdapter.FileViewHolder> {

    private final Context context;
    private List<FileItem> fileList;
    private final OnItemClickListener itemClickListener;
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(4);

    public interface OnItemClickListener {
        void onSelectionChanged();
    }

    public MediaPickerAdapter(Context context, List<File> files, OnItemClickListener itemClickListener) {
        this.context = context;
        this.itemClickListener = itemClickListener;
        this.fileList = new ArrayList<>();
        for (File file : files) {
            this.fileList.add(new FileItem(file));
        }
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.grid_item_media_picker, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final FileViewHolder holder, final int position) {
        final FileItem item = fileList.get(position);
        final File file = item.getFile();

        holder.fileName.setText(file.getName());
        holder.thumbnailImage.setImageResource(android.R.color.darker_gray);
        holder.thumbnailImage.setTag(file.getAbsolutePath());
        holder.selectionOverlay.setVisibility(item.isSelected() ? View.VISIBLE : View.GONE);

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
					holder.selectionCheckbox.setChecked(!holder.selectionCheckbox.isChecked());
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
            } else if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".3gp") || name.endsWith(".webm") || name.endsWith(".avi")) {
                bitmap = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
            }
        } catch (Exception e) {
            Log.e("MediaPickerAdapter", "Error creating thumbnail for " + path, e);
        }
        return bitmap;
    }

    private int getIconForFileType(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx") || lowerFileName.endsWith(".pdf")) return android.R.drawable.ic_menu_save;
        if (lowerFileName.endsWith(".xls") || lowerFileName.endsWith(".xlsx")) return android.R.drawable.ic_menu_agenda;
        if (lowerFileName.endsWith(".ppt") || lowerFileName.endsWith(".pptx")) return android.R.drawable.ic_menu_slideshow;
        if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".rtf") || lowerFileName.endsWith(".log")) return android.R.drawable.ic_menu_view;
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
        return fileList.size();
    }

    public List<FileItem> getItems() {
        return fileList;
    }

    public static class FileViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnailImage;
        TextView fileName;
        View selectionOverlay;
        CheckBox selectionCheckbox;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnailImage = itemView.findViewById(R.id.thumbnail_image_media);
            fileName = itemView.findViewById(R.id.file_name_media);
            selectionOverlay = itemView.findViewById(R.id.selection_overlay_media);
            selectionCheckbox = itemView.findViewById(R.id.selection_checkbox_media);
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


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

public class StorageBrowserAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements Filterable {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final Context context;
    private List<Object> masterList; // Can hold FileItem or DateHeader
    private List<Object> filteredList;
    private final OnItemClickListener itemClickListener;
    private final OnHeaderCheckedChangeListener headerClickListener;
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(4);

    public interface OnItemClickListener {
        void onItemClick(int position, Object item);
        void onItemLongClick(int position, Object item);
        void onSelectionChanged();
    }

    public interface OnHeaderCheckedChangeListener {
        void onHeaderCheckedChanged(StorageBrowserActivity.DateHeader header, boolean isChecked);
    }

    public StorageBrowserAdapter(Context context, List<Object> items, OnItemClickListener itemClickListener, OnHeaderCheckedChangeListener headerClickListener) {
        this.context = context;
        this.masterList = items;
        this.filteredList = new ArrayList<>(items);
        this.itemClickListener = itemClickListener;
        this.headerClickListener = headerClickListener;
    }

    public void updateMasterList(List<Object> newItems) {
        masterList = new ArrayList<>(newItems);
        filteredList = new ArrayList<>(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (filteredList.get(position) instanceof StorageBrowserActivity.DateHeader) {
            return TYPE_HEADER;
        } else {
            return TYPE_ITEM;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.list_item_date_header_browser, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.grid_item_file_delete, parent, false);
            return new FileViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        if (viewType == TYPE_HEADER) {
            final HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;
            final StorageBrowserActivity.DateHeader dateHeader = (StorageBrowserActivity.DateHeader) filteredList.get(position);

            headerViewHolder.dateHeaderText.setText(dateHeader.getDateString());
            headerViewHolder.dateHeaderCheckbox.setOnCheckedChangeListener(null);
            headerViewHolder.dateHeaderCheckbox.setChecked(dateHeader.isChecked());
            headerViewHolder.dateHeaderCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						if (headerClickListener != null) {
							headerClickListener.onHeaderCheckedChanged(dateHeader, isChecked);
						}
					}
				});

        } else {
            final FileViewHolder fileViewHolder = (FileViewHolder) holder;
            final StorageBrowserAdapter.FileItem item = (StorageBrowserAdapter.FileItem) filteredList.get(position);
            final File file = item.getFile();

            fileViewHolder.fileName.setText(file.getName());
            fileViewHolder.thumbnailImage.setImageResource(android.R.color.darker_gray);
            fileViewHolder.thumbnailImage.setTag(file.getAbsolutePath());
            fileViewHolder.selectionOverlay.setVisibility(item.isSelected() ? View.VISIBLE : View.GONE);
            fileViewHolder.selectionCheckbox.setVisibility(file.isDirectory() ? View.GONE : View.VISIBLE);

            fileViewHolder.selectionCheckbox.setOnCheckedChangeListener(null);
            fileViewHolder.selectionCheckbox.setChecked(item.isSelected());
            fileViewHolder.selectionCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						item.setSelected(isChecked);
						fileViewHolder.selectionOverlay.setVisibility(isChecked ? View.VISIBLE : View.GONE);
						if (itemClickListener != null) {
							itemClickListener.onSelectionChanged();
						}
					}
				});


            fileViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (itemClickListener != null) {
							itemClickListener.onItemClick(holder.getAdapterPosition(), item);
						}
					}
				});

            fileViewHolder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						if (itemClickListener != null) {
							itemClickListener.onItemLongClick(holder.getAdapterPosition(), item);
						}
						return true;
					}
				});

            if (file.isDirectory()) {
                fileViewHolder.thumbnailImage.setImageResource(android.R.drawable.ic_menu_myplaces);
            } else {
                thumbnailExecutor.execute(new Runnable() {
						@Override
						public void run() {
							final Bitmap thumbnail = createThumbnail(file);
							if (fileViewHolder.thumbnailImage.getTag().equals(file.getAbsolutePath())) {
								fileViewHolder.thumbnailImage.post(new Runnable() {
										@Override
										public void run() {
											if (thumbnail != null) {
												fileViewHolder.thumbnailImage.setImageBitmap(thumbnail);
											} else {
												fileViewHolder.thumbnailImage.setImageResource(getIconForFileType(file.getName()));
											}
										}
									});
							}
						}
					});
            }
        }
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
            Log.e("StorageBrowserAdapter", "Error creating thumbnail for " + path, e);
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
        return filteredList.size();
    }

    public List<Object> getFilteredItems() {
        return filteredList;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String charString = constraint.toString().toLowerCase();
                List<Object> filterResultsList = new ArrayList<>();
                if (charString.isEmpty()) {
                    filterResultsList.addAll(masterList);
                } else {
                    for (Object item : masterList) {
                        if (item instanceof FileItem) {
                            File file = ((FileItem) item).getFile();
                            if (file.getName().toLowerCase().contains(charString)) {
                                filterResultsList.add(item);
                            }
                        }
                    }
                }
                FilterResults filterResults = new FilterResults();
                filterResults.values = filterResultsList;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredList = (ArrayList<Object>) results.values;
                notifyDataSetChanged();
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

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView dateHeaderText;
        CheckBox dateHeaderCheckbox;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            dateHeaderText = itemView.findViewById(R.id.date_header_text_browser);
            dateHeaderCheckbox = itemView.findViewById(R.id.date_header_checkbox_browser);
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

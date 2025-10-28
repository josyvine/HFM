package com.hfm.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
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

// Import the PdfRenderer class
import android.graphics.pdf.PdfRenderer;

import com.hfm.app.SearchActivity.DateHeader;
import com.hfm.app.SearchActivity.SearchResult;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final Context context;
    private List<Object> listItems; // Changed from SearchResult to Object
    private final OnItemClickListener itemClickListener;
    private final OnHeaderCheckedChangeListener headerClickListener; // New listener for headers
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(4);

    public interface OnItemClickListener {
        void onItemClick(SearchResult item);
        void onItemLongClick(SearchResult item);
    }

    // New interface for handling clicks on the header checkbox
    public interface OnHeaderCheckedChangeListener {
        void onHeaderCheckedChanged(DateHeader header, boolean isChecked);
    }

    public SearchAdapter(Context context, List<Object> listItems, OnItemClickListener itemClickListener, OnHeaderCheckedChangeListener headerClickListener) {
        this.context = context;
        this.listItems = listItems;
        this.itemClickListener = itemClickListener;
        this.headerClickListener = headerClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        if (listItems.get(position) instanceof DateHeader) {
            return TYPE_HEADER;
        } else {
            return TYPE_ITEM;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.list_item_date_header, parent, false);
            return new HeaderViewHolder(view);
        } else { // TYPE_ITEM
            View view = LayoutInflater.from(context).inflate(R.layout.grid_item_search_result, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        if (viewType == TYPE_HEADER) {
            HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;
            final DateHeader dateHeader = (DateHeader) listItems.get(position);

            headerViewHolder.dateHeaderText.setText(dateHeader.getDateString());
            // Set listener to null before changing checked state to prevent infinite loops
            headerViewHolder.dateHeaderCheckbox.setOnCheckedChangeListener(null);
            headerViewHolder.dateHeaderCheckbox.setChecked(dateHeader.isChecked());

            // Set the listener to handle user interaction
            headerViewHolder.dateHeaderCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						if (headerClickListener != null) {
							headerClickListener.onHeaderCheckedChanged(dateHeader, isChecked);
						}
					}
				});

        } else { // TYPE_ITEM
            final ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            final SearchResult item = (SearchResult) listItems.get(position);

            itemViewHolder.indexNumber.setText(String.valueOf(position + 1));
            itemViewHolder.exclusionOverlay.setVisibility(item.isExcluded() ? View.GONE : View.VISIBLE);
            itemViewHolder.thumbnailImage.setImageResource(android.R.drawable.ic_menu_gallery);
            itemViewHolder.thumbnailImage.setTag(item.getUri().toString());

            // --- NEW LOGIC FOR DISPLAYING FILENAME ---
            if (isMediaFile(item.getDisplayName())) {
                itemViewHolder.fileNameText.setVisibility(View.GONE);
            } else {
                itemViewHolder.fileNameText.setVisibility(View.VISIBLE);
                itemViewHolder.fileNameText.setText(item.getDisplayName());
            }

            thumbnailExecutor.execute(new Runnable() {
					@Override
					public void run() {
						final Bitmap thumbnail = createThumbnail(item);
                        final int fallbackIconResId = (thumbnail == null) ? getIconForFileType(item.getDisplayName()) : 0;

						if (itemViewHolder.thumbnailImage.getTag().equals(item.getUri().toString())) {
							itemViewHolder.thumbnailImage.post(new Runnable() {
									@Override
									public void run() {
										if (thumbnail != null) {
                                            itemViewHolder.thumbnailImage.setImageBitmap(thumbnail);
                                        } else {
                                            itemViewHolder.thumbnailImage.setImageResource(fallbackIconResId);
                                        }
									}
								});
						}
					}
				});

            itemViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (itemClickListener != null) {
							itemClickListener.onItemClick(item);
						}
					}
				});

            itemViewHolder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						if (itemClickListener != null) {
							itemClickListener.onItemLongClick(item);
						}
						return true; // Consume the long click
					}
				});
        }
    }

    // --- NEW HELPER: CHECK IF FILE IS IMAGE OR VIDEO ---
    private boolean isMediaFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerFileName = fileName.toLowerCase();
        List<String> mediaExtensions = Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", // Images
            ".mp4", ".3gp", ".mkv", ".webm", ".avi"         // Videos
        );

        for (String ext : mediaExtensions) {
            if (lowerFileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private Bitmap createThumbnail(SearchResult item) {
        Uri uri = item.getUri();
        String displayName = item.getDisplayName();

        if (displayName == null) {
            return null;
        }

        String lowerDisplayName = displayName.toLowerCase();

        if (lowerDisplayName.endsWith(".apk")) {
            String path = "file".equals(uri.getScheme()) ? uri.getPath() : null;
            if (path != null) {
                return getApkIcon(path);
            }
        }
        if (lowerDisplayName.endsWith(".pdf")) {
            return createPdfThumbnail(uri);
        }

        try {
            Bitmap bitmap = null;
            if ("content".equals(uri.getScheme())) {
                String mimeType = context.getContentResolver().getType(uri);
                if (mimeType != null) {
                    if (mimeType.startsWith("image/")) {
                        return MediaStore.Images.Thumbnails.getThumbnail(context.getContentResolver(), item.getLastModified(), MediaStore.Images.Thumbnails.MINI_KIND, null);
                    } else if (mimeType.startsWith("video/")) {
                        return MediaStore.Video.Thumbnails.getThumbnail(context.getContentResolver(), item.getLastModified(), MediaStore.Video.Thumbnails.MINI_KIND, null);
                    }
                }
            } else if ("file".equals(uri.getScheme())) {
                String path = uri.getPath();
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(path);
                    bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                } catch (Exception e) {
                    // Not a video or it failed, will try as image next
                } finally {
                    retriever.release();
                }

                if (bitmap == null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(path, options);
                    if (options.outWidth > 0 && options.outHeight > 0) { // It's a valid image
                        options.inSampleSize = calculateInSampleSize(options, 150, 150);
                        options.inJustDecodeBounds = false;
                        bitmap = BitmapFactory.decodeFile(path, options);
                    }
                }
            }
            return bitmap;

        } catch (Exception e) {
            Log.e("SearchAdapter", "Could not create standard thumbnail for URI: " + uri, e);
            return null;
        }
    }

    private int getIconForFileType(String fileName) {
        if (fileName == null) {
            return android.R.drawable.ic_menu_info_details;
        }

        String lowerFileName = fileName.toLowerCase();

        if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx")) return android.R.drawable.ic_menu_save;
        if (lowerFileName.endsWith(".xls") || lowerFileName.endsWith(".xlsx")) return android.R.drawable.ic_menu_agenda;
        if (lowerFileName.endsWith(".ppt") || lowerFileName.endsWith(".pptx")) return android.R.drawable.ic_menu_slideshow;
        if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".rtf") || lowerFileName.endsWith(".log")) return android.R.drawable.ic_menu_view;
        if (lowerFileName.endsWith(".html") || lowerFileName.endsWith(".xml") || lowerFileName.endsWith(".js") || lowerFileName.endsWith(".css") || lowerFileName.endsWith(".java") || lowerFileName.endsWith(".py") || lowerFileName.endsWith(".c") || lowerFileName.endsWith(".cpp") || lowerFileName.endsWith(".php")) return android.R.drawable.ic_menu_edit;
        if (lowerFileName.endsWith(".zip") || lowerFileName.endsWith(".rar") || lowerFileName.endsWith(".7z") || lowerFileName.endsWith(".tar") || lowerFileName.endsWith(".gz")) return android.R.drawable.ic_menu_set_as;
        if (lowerFileName.endsWith(".exe") || lowerFileName.endsWith(".msi")) return android.R.drawable.ic_dialog_dialer;
        if (lowerFileName.endsWith(".mp3") || lowerFileName.endsWith(".wav") || lowerFileName.endsWith(".ogg") || lowerFileName.endsWith(".m4a")) return android.R.drawable.ic_media_play;
        return android.R.drawable.ic_menu_info_details;
    }

    private Bitmap getApkIcon(String filePath) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(filePath, 0);

            if (pi != null) {
                ApplicationInfo appInfo = pi.applicationInfo;
                appInfo.sourceDir = filePath;
                appInfo.publicSourceDir = filePath;
                Drawable icon = appInfo.loadIcon(pm);
                return drawableToBitmap(icon);
            }
        } catch (Exception e) {
            Log.e("SearchAdapter", "Could not get APK icon for: " + filePath, e);
        }
        return null;
    }

    private Bitmap createPdfThumbnail(Uri uri) {
        PdfRenderer renderer = null;
        PdfRenderer.Page page = null;
        ParcelFileDescriptor pfd = null;
        try {
            pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) return null;

            renderer = new PdfRenderer(pfd);
            page = renderer.openPage(0);

            Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            return bitmap;

        } catch (Exception e) {
            Log.e("SearchAdapter", "Could not render PDF thumbnail for: " + uri, e);
            return null;
        } finally {
            if (page != null) {
                page.close();
            }
            if (renderer != null) {
                renderer.close();
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (IOException e) { /* ignore */ }
            }
        }
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

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        int width = drawable.getIntrinsicWidth();
        width = width > 0 ? width : 96;
        int height = drawable.getIntrinsicHeight();
        height = height > 0 ? height : 96;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    @Override
    public int getItemCount() {
        return listItems.size();
    }

    public void updateData(List<Object> newItems) {
        this.listItems = newItems;
        notifyDataSetChanged();
    }

    // MODIFIED: ViewHolder now holds a reference to the new TextView
    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnailImage;
        TextView indexNumber;
        View exclusionOverlay;
        TextView fileNameText; // NEW

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnailImage = itemView.findViewById(R.id.thumbnail_image);
            indexNumber = itemView.findViewById(R.id.index_number);
            exclusionOverlay = itemView.findViewById(R.id.exclusion_overlay);
            fileNameText = itemView.findViewById(R.id.file_name_text); // NEW
        }
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView dateHeaderText;
        CheckBox dateHeaderCheckbox;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            dateHeaderText = itemView.findViewById(R.id.date_header_text);
            dateHeaderCheckbox = itemView.findViewById(R.id.date_header_checkbox);
        }
    }
}


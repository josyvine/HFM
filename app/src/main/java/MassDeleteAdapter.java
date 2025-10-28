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
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.pdf.PdfRenderer; // Import PdfRenderer
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MassDeleteAdapter extends RecyclerView.Adapter<MassDeleteAdapter.ItemViewHolder> {

    private final Context context;
    private List<SearchResult> listItems;
    private final OnItemClickListener itemClickListener;
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(4);

    public interface OnItemClickListener {
        void onItemClick(SearchResult item);
        // The onItemLongClick is intentionally removed to resolve the conflict with DragSelectTouchListener
    }

    public MassDeleteAdapter(Context context, List<SearchResult> listItems, OnItemClickListener itemClickListener) {
        this.context = context;
        this.listItems = listItems;
        this.itemClickListener = itemClickListener;
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.grid_item_search_result, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ItemViewHolder holder, int position) {
        final SearchResult item = listItems.get(position);

        holder.indexNumber.setText(String.valueOf(position + 1));
        holder.exclusionOverlay.setVisibility(item.isExcluded() ? View.GONE : View.VISIBLE);
        holder.thumbnailImage.setImageResource(android.R.color.darker_gray);
        holder.thumbnailImage.setTag(item.getUri().toString());

        // --- NEW LOGIC FOR DISPLAYING FILENAME ---
        if (isMediaFile(item.getDisplayName())) {
            holder.fileNameText.setVisibility(View.GONE);
        } else {
            holder.fileNameText.setVisibility(View.VISIBLE);
            holder.fileNameText.setText(item.getDisplayName());
        }

        thumbnailExecutor.execute(new Runnable() {
				@Override
				public void run() {
					final Bitmap thumbnail = createThumbnail(item);
                    final int fallbackIconResId = (thumbnail == null) ? getIconForFileType(item.getDisplayName()) : 0;

					if (holder.thumbnailImage.getTag().equals(item.getUri().toString())) {
						holder.thumbnailImage.post(new Runnable() {
								@Override
								public void run() {
                                    if (thumbnail != null) {
									    holder.thumbnailImage.setImageBitmap(thumbnail);
                                    } else {
                                        holder.thumbnailImage.setImageResource(fallbackIconResId);
                                    }
								}
							});
					}
				}
			});

        holder.itemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (itemClickListener != null) {
						itemClickListener.onItemClick(item);
					}
				}
			});

        // The setOnLongClickListener is intentionally removed here.
        // The DragSelectTouchListener attached to the RecyclerView handles the long-press gesture.
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
            if (path != null) return getApkIcon(path);
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
                        bitmap = MediaStore.Images.Thumbnails.getThumbnail(
                            context.getContentResolver(), item.getMediaStoreId(),
                            MediaStore.Images.Thumbnails.MINI_KIND, null);
                    } else if (mimeType.startsWith("video/")) {
                        bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                            context.getContentResolver(), item.getMediaStoreId(),
                            MediaStore.Video.Thumbnails.MINI_KIND, null);
                    }
                }
            } else if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
                String path = uri.getPath();
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(path);
                    bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                } catch (Exception e) {
                    // Fallback to image decoding
                } finally {
                    retriever.release();
                }

                if (bitmap == null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(path, options);
                    options.inSampleSize = calculateInSampleSize(options, 150, 150);
                    options.inJustDecodeBounds = false;
                    bitmap = BitmapFactory.decodeFile(path, options);
                }
            }

            if (bitmap != null) {
                return bitmap;
            }

        } catch (Exception e) {
            Log.e("MassDeleteAdapter", "Could not create thumbnail for URI: " + item.getUri(), e);
        }
        return null;
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
            Log.e("MassDeleteAdapter", "Could not get APK icon", e);
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
            Log.e("MassDeleteAdapter", "Could not render PDF thumbnail", e);
            return null;
        } finally {
            if (page != null) page.close();
            if (renderer != null) renderer.close();
            if (pfd != null) {
                try { pfd.close(); } catch (IOException e) { /* ignore */ }
            }
        }
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
        return listItems.size();
    }

    public void updateData(List<SearchResult> newItems) {
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

    public static class SearchResult {
        private final Uri uri;
        private final long mediaStoreId;
        private final String displayName;
        private boolean isExcluded;

        public SearchResult(Uri uri, long mediaStoreId, String displayName) {
            this.uri = uri;
            this.mediaStoreId = mediaStoreId;
            this.displayName = displayName;
            this.isExcluded = true;
        }
        public Uri getUri() { return uri; }
        public long getMediaStoreId() { return mediaStoreId; }
        public String getDisplayName() { return displayName; }
        public boolean isExcluded() { return isExcluded; }
        public void setExcluded(boolean excluded) { isExcluded = excluded; }
    }
}


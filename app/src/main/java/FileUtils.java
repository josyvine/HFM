package com.hfm.app;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;

public class FileUtils {

    private static final String TAG = "FileUtils";

    /**
     * Deletes a file from internal storage using the most reliable method available.
     * It first tries to delete via the Android MediaStore's ContentResolver, which is the
     * preferred method. If that fails (e.g., the file is not in the MediaStore), it
     * falls back to a direct file system deletion and then triggers a media scan to
     * ensure the system's index is updated.
     *
     * @param context The application context.
     * @param file    The file to be deleted.
     * @return true if the file was successfully deleted, false otherwise.
     */
    public static boolean deleteFile(Context context, File file) {
        if (file == null || !file.exists()) {
            return true;
        }

        String path = file.getAbsolutePath();
        ContentResolver resolver = context.getContentResolver();
        String where = MediaStore.Files.FileColumns.DATA + " = ?";
        String[] selectionArgs = new String[]{ path };

        try {
            int rowsDeleted = resolver.delete(MediaStore.Files.getContentUri("external"), where, selectionArgs);
            if (rowsDeleted > 0) {
                Log.d(TAG, "Successfully deleted file via ContentResolver: " + path);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting file via ContentResolver for path: " + path, e);
        }

        if (file.delete()) {
            Log.d(TAG, "Successfully deleted file directly. Requesting media scan for: " + path);
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
            return true;
        }

        return false;
    }

    // The old, complex, and failing methods have been removed and replaced by the single, robust method above.
}


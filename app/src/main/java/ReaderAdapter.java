package com.hfm.app;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ReaderAdapter extends RecyclerView.Adapter<ReaderAdapter.ViewHolder> implements Filterable {

    private Context context;
    private List<ReadableFile> fileList;
    private List<ReadableFile> fileListFiltered;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ReadableFile file);
    }

    public ReaderAdapter(Context context, List<ReadableFile> fileList, OnItemClickListener listener) {
        this.context = context;
        this.fileList = fileList;
        this.fileListFiltered = fileList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_reader, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final ReadableFile file = fileListFiltered.get(position);

        holder.fileName.setText(file.getName());
        holder.filePath.setText(file.getPath());
        holder.fileSize.setText(Formatter.formatFileSize(context, file.getSize()));
        holder.fileIcon.setImageResource(getIconForFileType(file.getName()));

        holder.itemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (listener != null) {
						listener.onItemClick(file);
					}
				}
			});
    }

    @Override
    public int getItemCount() {
        return fileListFiltered.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String charString = constraint.toString();
                if (charString.isEmpty()) {
                    fileListFiltered = fileList;
                } else {
                    List<ReadableFile> filteredList = new ArrayList<>();
                    for (ReadableFile row : fileList) {
                        // name match condition. both to lower case for case insensitive match
                        if (row.getName().toLowerCase().contains(charString.toLowerCase())) {
                            filteredList.add(row);
                        }
                    }
                    fileListFiltered = filteredList;
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = fileListFiltered;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                fileListFiltered = (ArrayList<ReadableFile>) results.values;
                notifyDataSetChanged();
            }
        };
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView fileIcon;
        TextView fileName, filePath, fileSize;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            fileIcon = itemView.findViewById(R.id.file_item_icon);
            fileName = itemView.findViewById(R.id.file_item_name);
            filePath = itemView.findViewById(R.id.file_item_path);
            fileSize = itemView.findViewById(R.id.file_item_size);
        }
    }

    public static class ReadableFile {
        private String name;
        private String path;
        private long size;

        public ReadableFile(File file) {
            this.name = file.getName();
            this.path = file.getAbsolutePath();
            this.size = file.length();
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public long getSize() {
            return size;
        }
    }

    private int getIconForFileType(String fileName) {
        if (fileName == null) {
            return android.R.drawable.ic_menu_info_details;
        }
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".pdf")) return android.R.drawable.ic_menu_gallery;
        if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx")) return android.R.drawable.ic_menu_save;
        if (lowerFileName.endsWith(".xls") || lowerFileName.endsWith(".xlsx") || lowerFileName.endsWith(".csv")) return android.R.drawable.ic_menu_agenda;
        if (lowerFileName.endsWith(".ppt") || lowerFileName.endsWith(".pptx")) return android.R.drawable.ic_menu_slideshow;
        if (lowerFileName.endsWith(".html") || lowerFileName.endsWith(".xml") || lowerFileName.endsWith(".js") || lowerFileName.endsWith(".css") || lowerFileName.endsWith(".java") || lowerFileName.endsWith(".py") || lowerFileName.endsWith(".c") || lowerFileName.endsWith(".cpp") || lowerFileName.endsWith(".php") || lowerFileName.endsWith(".gradle")) return android.R.drawable.ic_menu_edit;
        if (lowerFileName.endsWith(".zip") || lowerFileName.endsWith(".rar") || lowerFileName.endsWith(".7z") || lowerFileName.endsWith(".tar") || lowerFileName.endsWith(".gz")) return android.R.drawable.ic_menu_set_as;
        if (lowerFileName.endsWith(".mp3") || lowerFileName.endsWith(".wav") || lowerFileName.endsWith(".ogg") || lowerFileName.endsWith(".m4a")) return android.R.drawable.ic_media_play;
        if (lowerFileName.endsWith(".mp4") || lowerFileName.endsWith(".mkv") || lowerFileName.endsWith(".avi")) return android.R.drawable.ic_media_play;
        if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") || lowerFileName.endsWith(".png") || lowerFileName.endsWith(".gif")) return android.R.drawable.ic_menu_gallery;

        // Default icon for text and other unknown files
        return android.R.drawable.ic_menu_view;
    }
}


package com.jirigrill.naturehome;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class AppDrawerAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<AppEntry> items;

    public AppDrawerAdapter(Context context, List<AppEntry> items) {
        this.inflater = LayoutInflater.from(context);
        this.items = items;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public AppEntry getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.drawer_item, parent, false);
        }

        AppEntry entry = items.get(position);
        ImageView icon = view.findViewById(R.id.item_icon);
        TextView label = view.findViewById(R.id.item_label);

        icon.setImageDrawable(entry.icon);
        label.setText(entry.label);
        return view;
    }
}

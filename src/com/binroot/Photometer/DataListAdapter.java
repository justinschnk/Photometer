package com.binroot.Photometer;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;

public class DataListAdapter extends BaseAdapter {

    ArrayList<String> stringList;
    Context mContext;

    public DataListAdapter(Context context, HueData hueData) {
        this.mContext = context;
        stringList = new ArrayList<String>();
        setData(hueData);
    }

    public void setData(HueData hueData) {
        stringList.clear();
        final ArrayList<Double> keys = new ArrayList<Double>(hueData.keySet());
        Collections.sort(keys);

        for (Double k : keys) {
            for(HueMeasurement hm : hueData.get(k)) {
                if (hm.getLabel().isEmpty()) {
                    stringList.add(k +" → " + hm.getVal());
                } else {
                    stringList.add(k + " " + hm.getLabel() + " → " + hm.getVal());
                }
            }
        }
    }

    @Override
    public int getCount() {
        return stringList.size();
    }

    @Override
    public Object getItem(int i) {
        return stringList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        TextView tv = new TextView(mContext);
        tv.setText(stringList.get(i));
        return tv;
    }
}

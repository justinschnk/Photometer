package com.binroot.Photometer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import com.binroot.regression.NotEnoughValues;
import com.binroot.regression.RegressionMethods;
import ij.measure.CurveFitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class HueData extends HashMap<Double, ArrayList<Double>> {
    private String id;
    private Context context;
    final private String DEBUG = "HueData";
    SharedPreferences sp;
    private double [] params;

    final private String HUE_DATA = "heuData";

    public HueData(Context context, String id) {
        this.context = context;
        this.id = id;
        sp = context.getSharedPreferences(id, 0);
    }

    public void save() {
        String strToSave = hueDataToString();
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(HUE_DATA, strToSave);
        editor.commit();
    }

    public void load() {
        String strLoaded = sp.getString(HUE_DATA, "");

        //strLoaded = "0,54.71\n10,97.14\n20,133.18\n30,143.74\n40,164.22\n50,171.79\n60,175.34\n70,180\n80,182.23\n90,184.28\n100,186.72";

        hueDataFromString(strLoaded);
    }

    public void insertAppend(Double key, Double val) {
        if(this.containsKey(key)) {
            this.get(key).add(val);
        } else {
            ArrayList<Double> rList = new ArrayList<Double>();
            rList.add(val);
            this.put(key, rList);
        }
    }

    public double[] getParams() {
        return params;
    }

    public double[] exponentialFit() {
        Pair<double[], double[]> points = averagePoints();

        CurveFitter cf = new CurveFitter(points.first, points.second);
        cf.doFit(CurveFitter.EXP_WITH_OFFSET);
        // String formula = cf.getFormula(); // y = a*exp(-bx) + c
        params = cf.getParams();
        return params;
    }

    public Pair<double[], double[]> averagePoints() {
        ArrayList<Double> theKeys = new ArrayList<Double>(this.keySet());
        Collections.sort(theKeys);

        double [] xs = new double[theKeys.size()];
        double [] ys = new double[theKeys.size()];
        int i = 0;
        for (Double k : theKeys) {
            double avg = 0.0;
            for( Double v : this.get(k) ) {
                avg += v;
            }
            avg = avg / (this.get(k).size()+0.0);

            xs[i] = k;
            ys[i] = avg;
            i++;
        }

        return new Pair<double[], double[]>(xs, ys);
    }

    public String hueDataToString() {
        try {
            StringBuilder sb = new StringBuilder();
            for(Double k : this.keySet()) {
                for(Double v : this.get(k)) {
                    sb.append(k);
                    sb.append(",");
                    sb.append(v);
                    sb.append("\n");
                }
            }
            sb.deleteCharAt(sb.length()-1);
            return sb.toString();
        } catch(Exception e) {
            return "";
        }
    }

    public void hueDataFromString(String csv) {
        this.clear();
        String[] lines = csv.split("\n");
        for(String l : lines) {
            String[] pts = l.split(",");
            try {
                double x  = Double.parseDouble(pts[0]);
                double y  = Double.parseDouble(pts[1]);
                insertAppend(x, y);
            } catch(Exception e) {
                Log.d(DEBUG, "Error parsing hueData: " + e.getMessage());
            }
        }

    }


}

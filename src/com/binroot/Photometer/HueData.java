package com.binroot.Photometer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import com.binroot.regression.NotEnoughValues;
import com.binroot.regression.RegressionMethods;
import com.parse.*;
import ij.measure.CurveFitter;

import java.util.*;

public class HueData extends HashMap<Double, ArrayList<HueMeasurement>> {
    private String id;
    private Context context;
    final private String DEBUG = "HueData";
    SharedPreferences sp;
    private double [] params;
    ICloud mCloud;
    Updatable mUpdatable;

    final private String HUE_DATA = "heuData";

    public HueData(Context context, String id, Updatable updatable) {
        this.context = context;
        this.id = id;
        this.mUpdatable = updatable;
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

    public void resync() {
        try {
            updateServer();
        } catch(Exception e) {
            Log.d(DEBUG, "error: " + e.getMessage());
        }
    }

    private void updatePhone() {
        ParseQuery<ParseObject> query = ParseQuery.getQuery("HueMeasurement");
        query.findInBackground(new FindCallback<ParseObject>() {
            @Override
            public void done(List<ParseObject> results, ParseException e) {
                StringBuilder sb = new StringBuilder();
                for( ParseObject p : results ) {

                    double k = p.getDouble("proteinConcentration");
                    double hueVal = p.getDouble("hueVal");
                    String hueLabel = p.getString("hueLabel");
                    long time = p.getLong("time");
                    String uid = p.getString("UID");

                    sb.append(k);
                    sb.append(",");
                    sb.append(hueVal);
                    sb.append(",");
                    sb.append(hueLabel);
                    sb.append(",");
                    sb.append(time);
                    sb.append(",");
                    sb.append(uid);
                    sb.append("\n");
                }
                if(sb.length() > 0) {
                    sb.deleteCharAt(sb.length()-1);
                }
                hueDataFromString(sb.toString());
            }
        });
    }


    int seen = 0;
    private void updateServer() {
        Set<Double> keys = this.keySet();
        if (keys.isEmpty()) {
            updatePhone();
        }
        for(Double k : keys) {
            final ArrayList<HueMeasurement> hueMeasurements = this.get(k);
            for(HueMeasurement hm : hueMeasurements) {

                final ParseObject hueObject = new ParseObject("HueMeasurement");
                hueObject.put("proteinConcentration", k);
                hueObject.put("hueVal", hm.getVal());
                hueObject.put("hueLabel", hm.getLabel());
                hueObject.put("time", hm.getTime());
                hueObject.put("UID", hm.getUID());
                hueObject.put("hash", hm.hashCode());

                Log.d(DEBUG, "about to save "+ hueObject.getInt("hash") + " to server");

                ParseQuery<ParseObject> query = ParseQuery.getQuery("HueMeasurement");
                query.whereEqualTo("hash", hm.hashCode());
                query.findInBackground(new FindCallback<ParseObject>() {
                    public void done(List<ParseObject> results, ParseException e) {
                        if (e == null) {
                            if (results.isEmpty()) {
                                Log.d(DEBUG, "just saved " + hueObject.getInt("hash") + " to server!");
                                hueObject.saveInBackground( new SaveCallback() {
                                    @Override
                                    public void done(ParseException e) {
                                        if(seen == hueMeasurements.size()) {
                                            Log.d(DEBUG, hueObject.getInt("hash") + " was the last object. Now updating phone!");
                                            updatePhone();
                                            seen = 0;
                                        } else {
                                            seen++;
                                        }
                                    }
                                });
                            } else {
                                Log.d(DEBUG, hueObject.getInt("hash") + " already exists");
                            }
                        } else {
                            Log.d(DEBUG, "Error: " + e.getMessage());
                        }
                    }
                });
            }
        }
    }


    public void insertAppend(Double key, Double val, String label, Long time, String uid) {
        HueMeasurement hueMeasurement = new HueMeasurement(uid, val, label, time);
        if(this.containsKey(key)) {
            this.get(key).add( hueMeasurement );
        } else {
            ArrayList<HueMeasurement> rList = new ArrayList<HueMeasurement>();
            rList.add( hueMeasurement );
            this.put(key, rList);
        }
    }

    private static String uniqueID = null;
    private static final String PREF_UNIQUE_ID = "PREF_UNIQUE_ID";
    public String getUID() {
        if (uniqueID == null) {
            SharedPreferences sharedPrefs = context.getSharedPreferences(
                    PREF_UNIQUE_ID, Context.MODE_PRIVATE);
            uniqueID = sharedPrefs.getString(PREF_UNIQUE_ID, null);
            if (uniqueID == null) {
                uniqueID = UUID.randomUUID().toString();
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString(PREF_UNIQUE_ID, uniqueID);
                editor.commit();
            }
        }
        return uniqueID;
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
            for( HueMeasurement hm : this.get(k) ) {
                avg += hm.getVal();
            }
            avg = avg / (this.get(k).size()+0.0);

            xs[i] = k;
            ys[i] = avg;
            i++;
        }

        return new Pair<double[], double[]>(xs, ys);
    }

    public void remove(double key, double val) {
        this.get(key).remove(val);
    }


    public String hueDataToString() {
        try {
            StringBuilder sb = new StringBuilder();
            for(Double k : this.keySet()) {
                for(HueMeasurement hm : this.get(k)) {
                    sb.append(k);
                    sb.append(",");
                    sb.append(hm.getVal());
                    sb.append(",");
                    sb.append(hm.getLabel());
                    sb.append(",");
                    sb.append(hm.getTime());
                    sb.append(",");
                    sb.append(hm.getUID());
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
                double x  = Double.parseDouble(pts[0]); // key
                double y  = Double.parseDouble(pts[1]); // val
                String label  = pts[2];
                long t = Long.parseLong(pts[3]);
                String uid = pts[4];
                insertAppend(x, y, label, t, uid);
            } catch(Exception e) {
                Log.d(DEBUG, "Error parsing hueData: " + e.getMessage());
            }
        }

        if(mUpdatable != null) mUpdatable.updateData(this);
    }


}

package com.binroot.Photometer;


import android.content.Context;
import android.util.Log;
import com.parse.*;

import java.util.List;


public class ParseCloud implements ICloud {

    private String DEBUG = "ParseCloud";

    public ParseCloud(Context context) {
        Parse.initialize(context, "hib7CisxjjwkLMRDhe71s9k82OthjiDkULVeDp63", "AtrfJ6RTTy8Xj8wBRp6Jereo1N5208RfAy52pjZd");
    }


    @Override
    public boolean resync(HueData hueData) {

//        try {
//            updateServer(hueData);
//
//            ParseQuery<ParseObject> query = ParseQuery.getQuery("HueMeasurement");
//            query.findInBackground(new FindCallback<ParseObject>() {
//                @Override
//                public void done(List<ParseObject> results, ParseException e) {
//                    for( ParseObject p : results ) {
//                        double k = p.getDouble("proteinConcentration");
//                        double hueVal = p.getDouble("hueVal");
//                        String hueLabel = p.getString("hueLabel");
//                        long time = p.getLong("time");
//                        String uid = p.getString("UID");
//
//                        HueMeasurement hm = new HueMeasurement(uid, hueVal, hueLabel, time);
//
//                    }
//                }
//            });
//
//            return true;
//        } catch(Exception e) {
//            Log.d(DEBUG, "error: " + e.getMessage());
//            return false;
//        }
        return false;
    }

    private void updateServer(HueData hueData) {
        for(Double k : hueData.keySet()) {
            for(HueMeasurement hm : hueData.get(k)) {
                Log.d(DEBUG, "Saving "+k+", "+hm.toString());
                final ParseObject hueObject = new ParseObject("HueMeasurement");
                final int hashCode = hm.hashCode();
                hueObject.put("proteinConcentration", k);
                hueObject.put("hueVal", hm.getVal());
                hueObject.put("hueLabel", hm.getLabel());
                hueObject.put("time", hm.getTime());
                hueObject.put("UID", hm.getUID());
                hueObject.put("hash", hm.hashCode());

                ParseQuery<ParseObject> query = ParseQuery.getQuery("HueMeasurement");
                query.whereEqualTo("hash", hm.hashCode());
                query.findInBackground(new FindCallback<ParseObject>() {
                    public void done(List<ParseObject> results, ParseException e) {
                        if (e == null) {
                            Log.d(DEBUG, hashCode + ", found "+results.size());
                            if (results.isEmpty()) {
                                hueObject.saveInBackground();
                            }
                        } else {
                            Log.d(DEBUG, "Error: " + e.getMessage());

                        }
                    }
                });
            }
        }
    }
}

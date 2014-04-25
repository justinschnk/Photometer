package com.binroot.Photometer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.binroot.regression.NotEnoughValues;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;

import java.io.*;

public class GraphActivity extends Activity {

    final String DEBUG = "GraphActivity";

    HueData hueData;

    GraphView graphView;

    GraphViewSeries hueSeries;
    GraphViewSeries expSeries;

    EditText hueQuery;
    EditText concentrationQuery;

    TextView equation;

    final int IMPORT_FILE = 0;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.graph);
        hueQuery = ((EditText) findViewById(R.id.hueQuery));
        concentrationQuery = (EditText) findViewById(R.id.concentrationQuery);
        equation = (TextView) findViewById(R.id.equation);

        hueData = new HueData(getApplicationContext(), "1");
        hueSeries = new GraphViewSeries("Actual", new GraphViewSeries.GraphViewSeriesStyle(Color.rgb(255,0,0), 1), new GraphView.GraphViewData[0]);
        expSeries = new GraphViewSeries("Predicted", new GraphViewSeries.GraphViewSeriesStyle(Color.rgb(56,192,244), 5), new GraphView.GraphViewData[0]);
        graphView = new LineGraphView(this , "Concentration vs. Hue");
        graphView.addSeries(hueSeries);
        graphView.addSeries(expSeries);
        graphView.setDisableTouch(true);
        graphView.setScalable(true);
        graphView.getGraphViewStyle().setHorizontalLabelsColor(Color.BLACK);
        graphView.getGraphViewStyle().setVerticalLabelsColor(Color.BLACK);
        graphView.getGraphViewStyle().setGridColor(Color.GRAY);
        LinearLayout layout = (LinearLayout) findViewById(R.id.graph);
        layout.addView(graphView);


        Bundle bundle = getIntent().getExtras();
        Log.d(DEBUG, "bundle "+bundle);
        if (bundle != null) {
            hueData.load();
            double hue = bundle.getDouble("hue");
            hue = (double) Math.round(hue*100000) / 100000;
            hueQuery.setText(hue+"");
            hueQueryClicked(null);
        }

        concentrationQuery.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    concentrationQueryClicked(null);
                }
                return true;
            }
        });


        hueQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {}

            @Override
            public void afterTextChanged(Editable editable) {
                hueQueryClicked(null);
            }
        });

    }

    public void concentrationQueryClicked(View v) {
        final double result;
        try {
            result = Double.parseDouble(hueQuery.getText().toString());
        } catch(Exception e) {
            return;
        }

        final EditText editText = new EditText(getApplicationContext());
        editText.setLayoutParams(new ViewGroup.MarginLayoutParams(300, 50));
        editText.setTextColor(Color.BLACK);

        AlertDialog.Builder builder = new AlertDialog.Builder(GraphActivity.this);
        builder.setMessage("Enter protein concentration value for hue "+result)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        try {
                            double key = Double.parseDouble(editText.getText().toString());
                            hueData.insertAppend(key, result);
                            drawExpGraph();
                            drawHueGraph();
                            hueQueryClicked(null);
                        } catch (NumberFormatException e) {
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .setView(editText)
                .setTitle("Feed More Data");
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void backClicked(View v) {
        this.finish();
    }

    public void importClicked(View v) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("text/*");
        startActivityForResult(i, IMPORT_FILE);
    }

    public void hueQueryClicked(View v) {

        String qStr = hueQuery.getText().toString();
        try {
            hueData.exponentialFit();
            double qVal = Double.parseDouble(qStr);
            double conc = getConcentrationFromHue(hueData.getParams()[0], hueData.getParams()[1], hueData.getParams()[2], qVal);
            conc = (double) Math.round(conc*100000) / 100000;
            concentrationQuery.setText(conc + "");
        } catch(Exception e) {
            concentrationQueryClicked(null);
            Log.d(DEBUG, "err: "+e.getMessage());
        }
    }

    public double getConcentrationFromHue(double a, double b, double c, double y) {
        return -(Math.log((-c+y)/a))/b;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMPORT_FILE && resultCode == RESULT_OK) {
            Uri selectedData = data.getData();
            try {
                String hueStr = getStringFromFile(selectedData);
                hueData.hueDataFromString(hueStr);
                drawHueGraph();
                hueData.save();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "Import error: "+e.getMessage(), Toast.LENGTH_SHORT).show();
            }

        }
    }

    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public String getStringFromFile (Uri selectedData) throws Exception {
        InputStream fin = getContentResolver().openInputStream(selectedData);
        String ret = convertStreamToString(fin);
        fin.close();
        return ret;
    }

    public void exportClicked(View v) {
        File f = new File(Environment.getExternalStorageDirectory() + "/hueData.csv");

        try {
            FileWriter fw = new FileWriter(f);
            fw.write(hueData.hueDataToString());
            fw.close();

            Intent i = new Intent(Intent.ACTION_SEND);
            i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
            i.setType("text/csv");
            startActivity(Intent.createChooser(i, "Send Data"));

        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "export error: "+e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onPause() {
        hueData.save();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        hueData.load();
        drawHueGraph();
        drawExpGraph();
    }

    public void drawExpGraph() {

        try {
            double [] coeffs = hueData.exponentialFit(); // y = a*exp(-bx) + c
            double a = coeffs[0];
            double b = coeffs[1];
            double c = coeffs[2];

            b = 1/b;
            Log.d(DEBUG, "a = "+a+ ", b = "+b+", c = "+c);
            //.makeText(getApplicationContext(), a+" * e^(-"+b+" x) + "+c, Toast.LENGTH_SHORT).show();

            double ra = (double) Math.round(a*1000) / 1000;
            double rb = -1 * (double) Math.round(b*1000) / 1000;
            double rc = (double) Math.round(c*1000) / 1000;
            equation.setText("hue = " + ra + " * exp(" + rb +" * x) + "+rc);

            int divs = 1000;
            GraphView.GraphViewData[] data = new GraphView.GraphViewData[divs];

            double max = 0;
            for(double k : hueData.keySet()) {
                if (k > max) max = k;
            }

            for(int i=0; i<divs; i++) {
                data[i] = new GraphView.GraphViewData(i * max/(divs+0.0), evalExp(a, b, c, i * max/(divs+0.0)));
            }

            expSeries.resetData(data);
        } catch(Exception e) {

        }
    }

    public double evalExp(double a, double b, double c, double x) {
        //  y = a*e^(-b*x) + c
        return a * Math.exp(-x/b) + c;
    }

    public void drawHueGraph() {
        GraphView.GraphViewData[] data = new GraphView.GraphViewData[hueData.keySet().size()];

        Pair<double[], double[]> points = hueData.averagePoints();
        for(int i=0; i<data.length; i++) {
            data[i] = new GraphView.GraphViewData(points.first[i], points.second[i]);
        }

        hueSeries.resetData(data);
        graphView.removeSeries(hueSeries);
        graphView.addSeries(hueSeries);
    }


}
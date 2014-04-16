package com.binroot.Photometer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.binroot.regression.NotEnoughValues;
import com.binroot.regression.RegressionMethods;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class MainActivity extends Activity {

    final String DEBUG = "MainActivity";

    final int CAMERA_REQUEST = 0;
    final int SELECT_PICTURE = 1;


    ImageView mPreview;
    ImageView mCrop;
    Button mCropButton;
    View.OnTouchListener mOnTouchListener;

    ProgressBar mProgressPercent;

    Intent mCameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
    Intent mPicIntent = new Intent(Intent.ACTION_PICK,android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

    HueData hueData;
    Uri mCapturedImageURI;

    Bitmap bmp = null;


    SharedPreferences sp;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);


        hueData = new HueData(getApplicationContext(), "1");
        sp = getSharedPreferences("Photometer", Context.MODE_PRIVATE);


        mPreview = (ImageView) findViewById(R.id.preview);
        mCrop = (ImageView) findViewById(R.id.croprect);
        mCropButton = (Button) findViewById(R.id.cropButton);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Image File name");
        mCapturedImageURI = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        mCameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedImageURI);

        mProgressPercent = (ProgressBar) findViewById(R.id.progressPercent);

        mOnTouchListener = new View.OnTouchListener() {
            private float cropXstart = -1;
            private float cropYstart = -1;
            private float cropXstartPrev = -1;
            private float cropYstartPrev = -1;
            private float cropXend = -1;
            private float cropYend = -1;

            private int previousAction = 0;

            ImageView cropRect = new ImageView(getApplicationContext());

            public int getWidth() {
                return Math.abs((int) (this.cropXend - this.cropXstart));
            }

            public int getHeight() {
                return Math.abs((int) (this.cropYend - this.cropYstart));
            }

            private int getOldWidth() {
                return Math.abs((int) (this.cropXend - this.cropXstartPrev));
            }

            private int getOldHeight() {
                return Math.abs((int) (this.cropYend - this.cropYstartPrev));
            }

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    Log.d(DEBUG, "ACTION DOWN!");
                    //Log.d(DEBUG, "drag start "+motionEvent.getX()+", "+motionEvent.getY());
                    cropXstartPrev = cropXstart;
                    cropYstartPrev = cropYstart;
                    cropXstart = motionEvent.getX();
                    cropYstart = motionEvent.getY();

                    if (cropRect.getWidth() == 0) {
                        cropRect.setImageResource(R.drawable.croprect);
                    }
                    previousAction = MotionEvent.ACTION_DOWN;
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                    Log.d(DEBUG, "ACTION MOVE!");
                    //Log.d(DEBUG, "drag move "+motionEvent.getX()+", "+motionEvent.getY());
                    cropXend = motionEvent.getX();
                    cropYend = motionEvent.getY();

                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(this.getWidth(), this.getHeight());
                    params.leftMargin = (int) cropXstart;
                    params.topMargin = (int) cropYstart;

                    mCrop.setLayoutParams(params);

                    if (this.getWidth() > 0 && this.getHeight() > 0) {
                        mCropButton.setVisibility(View.VISIBLE);
                    } else {
                        mCropButton.setVisibility(View.GONE);
                    }
                    previousAction = MotionEvent.ACTION_MOVE;
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    if (previousAction == MotionEvent.ACTION_DOWN) {
                        if (motionEvent.getX() > cropXstartPrev && motionEvent.getX() < cropXstartPrev+this.getOldWidth()
                                && motionEvent.getY() > cropYstartPrev && motionEvent.getY() < cropYstartPrev+this.getOldHeight()) {
                            cropClicked(null);
                        } else {
                            cropXstart = -1;
                            cropYstart = -1;
                            cropXend = -1;
                            cropYend = -1;
                            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(0, 0);
                            mCrop.setLayoutParams(params);
                            mCropButton.setVisibility(View.GONE);
                        }
                    }
                    Log.d(DEBUG, "ACTION UP!");
                    previousAction = MotionEvent.ACTION_UP;
                }
                return true;
            }
        };

        mPreview.setOnTouchListener(mOnTouchListener);
        mPreview.setEnabled(false);
    }

    public void galClicked(View v) {
        startActivityForResult(mPicIntent, SELECT_PICTURE);
    }

    public void camClicked(View v) {
        startActivityForResult(mCameraIntent, CAMERA_REQUEST);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            loadPhoto(getPath(mCapturedImageURI));
            mPreview.setEnabled(true);
        } else if (requestCode == SELECT_PICTURE && resultCode == RESULT_OK) {
            Uri selectedImageUri = data.getData();
            loadPhoto(getPath(selectedImageUri));
            mPreview.setEnabled(true);
        }
    }

    public String getPath(Uri uri) {
        String[] filePathColumn = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(uri,filePathColumn, null, null, null);
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        String picturePath = cursor.getString(columnIndex);
        cursor.close();
        return picturePath;
    }


    public void loadPhoto(String imageLocation) {
        if (imageLocation != null) {
            Log.d(DEBUG, "image location is "+imageLocation);

            // Get the dimensions of the View
            int targetW = mPreview.getWidth();
            int targetH = mPreview.getHeight();

            Log.d(DEBUG, "imageView: "+ targetW+" x "+targetH);

            // Get the dimensions of the bitmap
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imageLocation, bmOptions);
            int photoW = bmOptions.outWidth;
            int photoH = bmOptions.outHeight;

            if (targetW == 0) {
                targetW = 500;
                targetH = photoH/photoW * targetW;
            }

            // Determine how much to scale down the image
            int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

            // Decode the image file into a Bitmap sized to fill the View
            bmOptions.inJustDecodeBounds = false;
            bmOptions.inSampleSize = scaleFactor;
            bmOptions.inPurgeable = true;

            bmp = BitmapFactory.decodeFile(imageLocation, bmOptions);
            Log.d(DEBUG, bmp.getWidth() +" x " + bmp.getHeight());

            mPreview.setImageBitmap(bmp);


        } else {
            Toast.makeText(this, "Error, image location is null", Toast.LENGTH_SHORT).show();
        }
    }

    public void dataClicked(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        final ArrayList<Double> keys = new ArrayList<Double>(hueData.keySet());
        Collections.sort(keys);
        final ArrayList<String> displayDataList = new ArrayList<String>();

        for (Double k : keys) {
            for(Double h : hueData.get(k)) {
                displayDataList.add(k +" → " + h);
            }
        }

        String [] lines = new String[displayDataList.size()];
        displayDataList.toArray(lines);
        builder.setTitle("Data")
                .setPositiveButton("Graph", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent graphIntent = new Intent(getApplicationContext(), GraphActivity.class);
                        startActivity(graphIntent);
                    }
                })
                .setNegativeButton("Clear All", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        hueData.clear();
                        hueData.save();
                    }
                })
                .setItems(lines, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        AlertDialog.Builder b2 = new AlertDialog.Builder(MainActivity.this);
                        b2.setTitle("Delete Data Point?")
                                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {

                                    }
                                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                            }
                        });
                        b2.show();
                    }
                });
        builder.create().show();
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
    }


    public void hueClicked(View v) {
        if (bmp != null) {
            new HueTask().execute(bmp);
        }
    }


    private class HueTask extends AsyncTask<Bitmap, Integer, Double> {

        protected void onPreExecute() {
            mProgressPercent.setVisibility(View.VISIBLE);
        }

        protected Double doInBackground(Bitmap... b) {

            double h = 0;
            double i = 0;
            double height = b[0].getHeight();
            double width = b[0].getWidth();

            double totalSize = width * height;
            for(int y=0; y<height; y++) {
                for(int x=0; x<width; x++) {
                    float [] hsv = new float[3];
                    Color.colorToHSV(b[0].getPixel(x,y), hsv);
                    h += hsv[0];
                }
                publishProgress((int)(100 * (i++)/height));
            }
            return h/totalSize;
        }

        protected void onProgressUpdate(Integer... progress) {
            mProgressPercent.setProgress(progress[0]);
        }

        protected void onPostExecute(final Double result) {

            final EditText editText = new EditText(getApplicationContext());
            editText.setLayoutParams(new ViewGroup.MarginLayoutParams(300, 50));
            editText.setTextColor(Color.BLACK);
            mProgressPercent.setVisibility(View.GONE);
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage("Enter id number:")
                    .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            try {
                                double key = Double.parseDouble(editText.getText().toString());
                                hueData.insertAppend(key, result);
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
                    .setTitle("Hue = " + result);
            AlertDialog dialog = builder.create();
            dialog.show();

        }
    }

    public void cropClicked(View v) {
        mCropButton.setVisibility(View.GONE);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mCrop.getLayoutParams();

        Log.d(DEBUG, "bitmap dimension "+bmp.getWidth() +" x "+bmp.getHeight());
        Log.d(DEBUG, "preview dimension "+mPreview.getWidth() + " x " + mPreview.getHeight());

        double widthScale = ((double)mPreview.getWidth()) / ((double)bmp.getWidth());
        double heightScale = ((double)mPreview.getHeight()) / ((double)bmp.getHeight());

        Log.d(DEBUG, "width is scaled by "+widthScale);
        Log.d(DEBUG, "height is scaled by "+heightScale);
        try {
            bmp = Bitmap.createBitmap(bmp, (int)(lp.leftMargin * (1/widthScale)), (int)(lp.topMargin * (1/heightScale)), (int)(lp.width * (1/widthScale)), (int)(lp.height * (1/heightScale)));
            mPreview.setImageBitmap(bmp);
        } catch (IllegalArgumentException e) {

        }

        mCrop.setLayoutParams(new RelativeLayout.LayoutParams(0, 0));

    }

}

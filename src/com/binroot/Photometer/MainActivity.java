package com.binroot.Photometer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.*;
import com.binroot.regression.NotEnoughValues;
import com.binroot.regression.RegressionMethods;
import com.google.code.ekmeans.EKmeans;

import java.io.File;
import java.util.*;

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
    TextView mBloodText;

    Bitmap bmp = null;

    Stack<Dimension> dimensions = new Stack<Dimension>();

    MyApplication app;

    SharedPreferences sp;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        app = (MyApplication) getApplication();

        hueData = new HueData(getApplicationContext(), "1", null);
        sp = getSharedPreferences("Photometer", Context.MODE_PRIVATE);


        mPreview = (ImageView) findViewById(R.id.preview);
        mCrop = (ImageView) findViewById(R.id.croprect);
        mCropButton = (Button) findViewById(R.id.cropButton);
        mBloodText = (TextView) findViewById(R.id.bloodRatio);

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

    @Override
    public void onBackPressed() {
        if (dimensions.size() < 2) {
            new AlertDialog.Builder(this).setTitle("Are you sure you want to quit?")
                    .setPositiveButton("Quit", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    finish();
                }
            }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.cancel();
                }
            }).show();
        } else {
            dimensions.pop();
            loadPhoto(getPath(mCapturedImageURI), dimensions.peek());
            mPreview.setEnabled(true);
        }
    }

    public void galClicked(View v) {

        Animation a = new ScaleAnimation(0,100,0,100);
        findViewById(R.id.galButton).startAnimation(a);
        startActivityForResult(mPicIntent, SELECT_PICTURE);
    }

    public void camClicked(View v) {
        Animation a = new ScaleAnimation(0,100,0,100);
        findViewById(R.id.camButton).startAnimation(a);
        startActivityForResult(mCameraIntent, CAMERA_REQUEST);
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            loadPhoto(getPath(mCapturedImageURI));
            dimensions.clear();
            dimensions.add(new Dimension(0, 0, bmp.getWidth(), bmp.getHeight()));
            mPreview.setEnabled(true);
        } else if (requestCode == SELECT_PICTURE && resultCode == RESULT_OK) {
            Uri selectedImageUri = data.getData();
            mCapturedImageURI = selectedImageUri;
            loadPhoto(getPath(selectedImageUri));
            dimensions.clear();
            dimensions.add(new Dimension(0, 0, bmp.getWidth(), bmp.getHeight()));
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

    public void loadPhoto(String imageLocation, Dimension d) {
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


            bmp = Bitmap.createBitmap(bmp, d.x, d.y, d.width, d.height);

            mPreview.setImageBitmap(bmp);


        } else {
            Toast.makeText(this, "Error, image location is null", Toast.LENGTH_SHORT).show();
        }
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
        Animation a = new ScaleAnimation(0,100,0,100);
        findViewById(R.id.dataButton).startAnimation(a);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        final ArrayList<Double> keys = new ArrayList<Double>(hueData.keySet());
        Collections.sort(keys);
        final ArrayList<String> displayDataList = new ArrayList<String>();

        for (Double k : keys) {
            for(HueMeasurement hm : hueData.get(k)) {
                if (hm.getLabel().isEmpty()) {
                    displayDataList.add(k +" → " + hm.getVal());
                } else {
                    displayDataList.add(k + " " + hm.getLabel() + " → " + hm.getVal());
                }
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
                    public void onClick(DialogInterface dialog, final int which) {
                        AlertDialog.Builder b2 = new AlertDialog.Builder(MainActivity.this);
                        b2.setTitle("Delete Data Point?")
                                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        Log.d(DEBUG, "displayDataList.get(i) = "+displayDataList.get(which));
                                        Double key = Double.parseDouble(displayDataList.get(i).split(" → ")[0]);
                                        Double val = Double.parseDouble(displayDataList.get(i).split(" → ")[1]);
                                        hueData.remove(key, val);
                                    }
                                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();
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



    public void bloodClicked(View v) {
        if (bmp != null) {
            new BloodTask3().execute(bmp);
        }
    }

    private class BloodTask3 extends AsyncTask<Bitmap, Integer, Pair<Bitmap, Long>> {
        protected void onPreExecute() {
            mBloodText.setText("CALCULATING...");
            mBloodText.setVisibility(View.VISIBLE);
            mProgressPercent.setVisibility(View.VISIBLE);
        }

        protected void onProgressUpdate(Integer... progress) {
            mProgressPercent.setProgress(progress[0]);
        }

        protected Pair<Bitmap, Long> doInBackground(Bitmap... b) {
            int height = b[0].getHeight();
            int width = b[0].getWidth();
            int totalSize = width * height;

            int [] pixels = new int[totalSize];
            b[0].getPixels(pixels, 0, width, 0, 0, width, height);

            long bloodPixels = 0;

            double thdb = 0.1;
            double thds = 0.8;

            int pixelIndex = 0;

            for(int y=0; y<height; y++) {
                for(int x=0; x<width; x++) {
                    float [] hsv = new float[3];
                    Color.colorToHSV(b[0].getPixel(x, y), hsv);
                    //Log.d(DEBUG, "HSV: "+hsv[0]+", "+hsv[1]+", "+hsv[2]);
                    if (hsv[2] < thdb) {
                        pixels[pixelIndex] = Color.BLACK;
                    } else {
                        if (hsv[1] > thds) {
                            pixels[pixelIndex] = Color.RED;
                            bloodPixels++;
                        } else {
                            pixels[pixelIndex] = Color.BLACK;
                        }
                    }
                    pixelIndex++;
                }
                publishProgress((int)(100.0 * (y+1.0)/(height+0.0)));
            }

            return new Pair<Bitmap, Long>(Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_4444), bloodPixels);
        }

        @Override
        protected void onPostExecute(final Pair<Bitmap, Long> result) {
            mProgressPercent.setVisibility(View.GONE);
            long numPixels = result.second;
            mBloodText.setText( numPixels +" pixels");
            bmp = result.first;
            mPreview.setImageBitmap(bmp);
        }
    }

    private class BloodTask2 extends AsyncTask<Bitmap, Integer, Pair<Bitmap, Double>> {

        protected void onPreExecute() {
            mBloodText.setText("CALCULATING...");
            mBloodText.setVisibility(View.VISIBLE);
            mProgressPercent.setVisibility(View.VISIBLE);
        }

        protected Pair<Bitmap, Double> doInBackground(Bitmap... b) {
            int height = b[0].getHeight();
            int width = b[0].getWidth();
            int totalSize = width * height;

            int [] pixels = new int[totalSize];
            b[0].getPixels(pixels, 0, width, 0, 0, width, height);


            double[][] points = new double[totalSize][3];

            for (int i=0; i<pixels.length; i++) {
                points[i][0] = Color.red(pixels[i]);
                points[i][1] = Color.green(pixels[i]);
                points[i][2] = Color.blue(pixels[i]);
            }

            double[][] centroids = new double[5][3];
            // red centroid
            centroids[0][0] = 255; centroids[0][1] = 0; centroids[0][2] = 0;
            // green centroid
            centroids[1][0] = 0; centroids[1][1] = 255; centroids[1][2] = 0;
            // blue centroid
            centroids[2][0] = 0; centroids[2][1] = 0; centroids[2][2] = 255;
            // black centroid
            centroids[3][0] = 0; centroids[3][1] = 0; centroids[3][2] = 0;
            // white centroid
            centroids[4][0] = 255; centroids[4][1] = 255; centroids[4][2] = 255;

            EKmeans eKmeans = new EKmeans(centroids, points);
            eKmeans.setDistanceFunction(EKmeans.MANHATTAN_DISTANCE_FUNCTION);
            Log.d(DEBUG, "starting k-means clustering");
            eKmeans.run();


            int rCount = 0;
            int gCount = 0;
            int wCount = 0;
            int[] assignments = eKmeans.getAssignments();
            for (int i=0; i<totalSize; i++) {
                //Log.d(DEBUG, "point "+i+" is assigned to cluster "+assignments[i]);
                switch (assignments[i]) {
                    case 0: pixels[i] = Color.RED; rCount++; break;
                    case 1: pixels[i] = Color.GREEN; gCount++; break;
                    case 2: pixels[i] = Color.BLUE; break;
                    case 3: pixels[i] = Color.BLACK; break;
                    case 4: pixels[i] = Color.WHITE; wCount++; break;
                    default: break;
                }
            }

            double bloodRatio = (rCount+0.0) / ( rCount + gCount - wCount + 0.0 );

            return new Pair<Bitmap, Double>(Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_4444), bloodRatio);
        }

        protected void onProgressUpdate(Integer... progress) {
            mProgressPercent.setProgress(progress[0]);
        }

        protected void onPostExecute(final Pair<Bitmap, Double> result) {
            mProgressPercent.setVisibility(View.GONE);
            double percent = result.second * 100;
            mBloodText.setText( String.format("%.3f", percent) +"% blood");
            bmp = result.first;
            mPreview.setImageBitmap(bmp);
        }
    }

    public void bloodRatioClicked(View view) {
        mBloodText.setVisibility(View.GONE);
    }

    private class BloodTask extends AsyncTask<Bitmap, Integer, Bitmap> {

        protected void onPreExecute() {
            mProgressPercent.setVisibility(View.VISIBLE);
        }

        private int pixels[];
        private int width, height;
        private boolean visited[];

        protected Bitmap doInBackground(Bitmap... b) {

            height = b[0].getHeight();
            width = b[0].getWidth();
            int totalSize = width * height;

            long rCount = 0;
            long gCount = 0;
            long bCount = 0;

            pixels = new int[totalSize];
            visited = new boolean[totalSize];
            b[0].getPixels(pixels, 0, width, 0, 0, width, height);

            /*
             * Find the average red, green, and blue values from all pixels
             */
            for (int i=0; i<pixels.length; i++) {
                rCount += Color.red(pixels[i]);
                gCount += Color.green(pixels[i]);
                bCount += Color.blue(pixels[i]);
            }

            int rAvg = (int) (rCount/totalSize);
            int gAvg = (int) (gCount/totalSize);
            int bAvg = (int) (bCount/totalSize);


             /*
             * Normalize each pixel, and amplify the strongest color
             *
             * Also, Compute the center of mass of each color
             *
             */
            int avgColVal = (rAvg+gAvg+bAvg)/3;
		    int diffR = avgColVal - rAvg;
		    int diffG = avgColVal - gAvg;
		    int diffB = avgColVal - bAvg;

            int avgRedX = 0;
            int avgRedY = 0;
            int totalRed = 0;
            int avgGreenX = 0;
            int avgGreenY = 0;
            int totalGreen = 0;
            int avgBlueX = 0;
            int avgBlueY = 0;
            int totalBlue = 0;

            for (int i=0; i<pixels.length; i++) {
                int newR = Color.red(pixels[i]) + diffR;
                int newG = Color.green(pixels[i]) + diffG;
                int newB = Color.blue(pixels[i]) + diffB;


                if(newR >= newG && newR>=newB) {
                    pixels[i] = Color.RED;
                    avgRedX += i%width;
                    avgRedY += i/width;
                    totalRed++;
				}
				else if(newG >= newR && newG>=newB) {
                    pixels[i] = Color.GREEN;
                    avgGreenX += i%width;
                    avgGreenY += i/width;
                    totalGreen++;
				}
				else if(newB >= newR && newB>=newG) {
                    pixels[i] = Color.BLUE;
                    avgBlueX += i%width;
                    avgBlueY += i/width;
                    totalBlue++;
				}
            }

            avgRedX /= totalRed;
            avgRedY /= totalRed;
            avgGreenX /= totalGreen;
            avgGreenY /= totalGreen;
            avgBlueX /= totalBlue;
            avgBlueY /= totalBlue;

            pixels[avgRedX + avgRedY*width] = Color.YELLOW;
            pixels[avgRedX+1 + avgRedY*width] = Color.YELLOW;
            pixels[avgRedX-1 + avgRedY*width] = Color.YELLOW;
            pixels[avgRedX + (avgRedY+1)*width] = Color.YELLOW;
            pixels[avgRedX + (avgRedY-1)*width] = Color.YELLOW;

            pixels[avgGreenX + avgGreenY*width] = Color.YELLOW;
            pixels[avgGreenX+1 + avgGreenY*width] = Color.YELLOW;
            pixels[avgGreenX-1 + avgGreenY*width] = Color.YELLOW;
            pixels[avgGreenX + (avgGreenY+1)*width] = Color.YELLOW;
            pixels[avgGreenX + (avgGreenY-1)*width] = Color.YELLOW;

            pixels[avgBlueX + avgBlueY*width] = Color.YELLOW;
            pixels[avgBlueX+1 + avgBlueY*width] = Color.YELLOW;
            pixels[avgBlueX-1 + avgBlueY*width] = Color.YELLOW;
            pixels[avgBlueX + (avgBlueY+1)*width] = Color.YELLOW;
            pixels[avgBlueX + (avgBlueY-1)*width] = Color.YELLOW;


//            /**
//             * Find the largest green mass
//             */
//            HashMap<Integer, Integer> areaMap = new HashMap<Integer, Integer>();
//            Arrays.fill(visited, false);
//            int maxGreenMass = 0;
//            int maxGreenMassI = -1;
//            for(int i=0; i<pixels.length; i++) {
//                if (Color.green(pixels[i]) > 0 && !visited[i]) {
//                    int greenMass = count(i);
//                    if (greenMass > maxGreenMass) {
//                        maxGreenMass = greenMass;
//                        maxGreenMassI = i;
//                    }
//                    areaMap.put(i, count(i));
//                }
//            }

//            Arrays.fill(visited, false);
//            for(int pixelIndex : areaMap.keySet()) {
//                if(pixelIndex != maxGreenMassI) {
//                    fill(pixelIndex, Color.BLUE);
//                }
//            }


            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_4444);
        }

        private void fill(int pixelIndex, int col) {
            // Ignore the 4 edges of an image
            if (pixelIndex%width == 0 || pixelIndex%width == width-1 ||
                    pixelIndex/width == 0 || pixelIndex/width == height-1) {
                return;
            }

            int oldColor = pixels[pixelIndex];

            pixels[pixelIndex] = col;

            visited[pixelIndex] = true;

            if (!visited[north(pixelIndex)] && pixels[north(pixelIndex)] == oldColor) fill(north(pixelIndex), col);
            if (!visited[northeast(pixelIndex)] && pixels[northeast(pixelIndex)] == pixels[pixelIndex]) fill(northeast(pixelIndex), col);
            if (!visited[east(pixelIndex)] && pixels[east(pixelIndex)] == pixels[pixelIndex]) fill(east(pixelIndex), col);
            if (!visited[southeast(pixelIndex)] && pixels[southeast(pixelIndex)] == pixels[pixelIndex]) fill(southeast(pixelIndex), col);
            if (!visited[south(pixelIndex)] && pixels[south(pixelIndex)] == pixels[pixelIndex]) fill(south(pixelIndex), col);
            if (!visited[southwest(pixelIndex)] && pixels[southwest(pixelIndex)] == pixels[pixelIndex]) fill(southwest(pixelIndex), col);
            if (!visited[west(pixelIndex)] && pixels[west(pixelIndex)] == pixels[pixelIndex]) fill(west(pixelIndex), col);
            if (!visited[northwest(pixelIndex)] && pixels[northwest(pixelIndex)] == pixels[pixelIndex]) fill(northwest(pixelIndex), col);
        }

        /**
         * Count all pixels of a specific color neighboring the pixelIndex
         * @param pixelIndex
         * @return number of pixels
         */
        private int count(int pixelIndex) {
            int sum = 0;

            // Ignore the 4 edges of an image
            if (pixelIndex%width == 0 || pixelIndex%width == width-1 ||
                    pixelIndex/width == 0 || pixelIndex/width == height-1) {
                return sum;
            }

            visited[pixelIndex] = true;

            if (!visited[north(pixelIndex)] && pixels[north(pixelIndex)] == pixels[pixelIndex]) sum += count(north(pixelIndex));
            if (!visited[northeast(pixelIndex)] && pixels[northeast(pixelIndex)] == pixels[pixelIndex]) sum += count(northeast(pixelIndex));
            if (!visited[east(pixelIndex)] && pixels[east(pixelIndex)] == pixels[pixelIndex]) sum += count(east(pixelIndex));
            if (!visited[southeast(pixelIndex)] && pixels[southeast(pixelIndex)] == pixels[pixelIndex]) sum += count(southeast(pixelIndex));
            if (!visited[south(pixelIndex)] && pixels[south(pixelIndex)] == pixels[pixelIndex]) sum += count(south(pixelIndex));
            if (!visited[southwest(pixelIndex)] && pixels[southwest(pixelIndex)] == pixels[pixelIndex]) sum += count(southwest(pixelIndex));
            if (!visited[west(pixelIndex)] && pixels[west(pixelIndex)] == pixels[pixelIndex]) sum += count(west(pixelIndex));
            if (!visited[northwest(pixelIndex)] && pixels[northwest(pixelIndex)] == pixels[pixelIndex]) sum += count(northwest(pixelIndex));

            return sum;
        }

        private int north(int pixelIndex) { return pixelIndex-width; }
        private int northeast(int pixelIndex) { return pixelIndex-width + 1; }
        private int east(int pixelIndex) { return pixelIndex + 1; }
        private int southeast(int pixelIndex) { return pixelIndex+width + 1; }
        private int south(int pixelIndex) { return pixelIndex+width; }
        private int southwest(int pixelIndex) { return pixelIndex+width - 1; }
        private int west(int pixelIndex) { return pixelIndex - 1; }
        private int northwest(int pixelIndex) { return pixelIndex-width - 1; }

        protected void onProgressUpdate(Integer... progress) {
            mProgressPercent.setProgress(progress[0]);
        }

        protected void onPostExecute(final Bitmap result) {
            mProgressPercent.setVisibility(View.GONE);
            //Toast.makeText(getApplicationContext(), "Blood ratio: "+result, Toast.LENGTH_SHORT).show();

            bmp = result;
            mPreview.setImageBitmap(bmp);
        }
    }


    private class HueTask extends AsyncTask<Bitmap, Integer, Double> {

        protected void onPreExecute() {
            (findViewById(R.id.hueButton)).setBackgroundResource(android.R.drawable.star_on);
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
            (findViewById(R.id.hueButton)).setBackgroundResource(android.R.drawable.star_off);
            mProgressPercent.setVisibility(View.GONE);

            // start GraphActivity
            Intent graphIntent = new Intent(getApplicationContext(), GraphActivity.class);
            graphIntent.putExtra("hue", result);
            startActivity(graphIntent);

//            final EditText editText = new EditText(getApplicationContext());
//            editText.setLayoutParams(new ViewGroup.MarginLayoutParams(300, 50));
//            editText.setTextColor(Color.BLACK);
//
//            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
//            builder.setMessage("Enter id number:")
//                    .setPositiveButton("Save", new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialogInterface, int i) {
//                            try {
//                                double key = Double.parseDouble(editText.getText().toString());
//                                hueData.insertAppend(key, result);
//                            } catch (NumberFormatException e) {
//                            }
//                        }
//                    })
//                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialogInterface, int i) {
//
//                        }
//                    })
//                    .setView(editText)
//                    .setTitle("Hue = " + result);
//            AlertDialog dialog = builder.create();
//            dialog.show();

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
            int newX = (int)(lp.leftMargin * (1/widthScale));
            int newY = (int)(lp.topMargin * (1/heightScale));
            int newWidth = (int)(lp.width * (1/widthScale));
            int newHeight = (int)(lp.height * (1/heightScale));

            bmp = Bitmap.createBitmap(bmp, newX, newY, newWidth, newHeight);
            dimensions.push(new Dimension(newX, newY, newWidth, newHeight));
            mPreview.setImageBitmap(bmp);
        } catch (IllegalArgumentException e) {
            Log.d(DEBUG, "crop err: "+e.getMessage());
        }

        mCrop.setLayoutParams(new RelativeLayout.LayoutParams(0, 0));

    }

    class Dimension {
        public int x;
        public int y;
        public int width;
        public int height;
        public Dimension(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

}

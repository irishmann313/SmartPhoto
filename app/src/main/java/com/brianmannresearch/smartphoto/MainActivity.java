package com.brianmannresearch.smartphoto;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, View.OnClickListener {

    private static final int CAMERA_INTENT = 1, LOCATION_REQUEST = 2, WRITE_STORAGE_REQUEST = 4, READ_STORAGE_REQUEST = 5, CAMERA_REQUEST = 6, GALLERY = 7;
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private static final String usernames = "saved_users.txt";

    private Button startButton, exitButton, deleteButton;
    private LinearLayout linearLayout;
    private TextView[] tv;
    private String username, Filename;
    private String[] filename;
    private List<String> users = new ArrayList<>();
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private Location mCurrentLocation;
    private File tripfolder, directory, imagesFolder;
    private FileOutputStream fos, afos;
    private File[] folders;

    private int tripnumber;

    // function that is called once the application is launched
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // check if location services are enabled
        // if not, show an alert dialog
        if (!isLocationEnabled(this)) {
            showSettingsAlert();
        }

        // check if application has permission to use location services
        // if not, request permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
        }

        // check if application has permission to write to external storage
        // if not, request permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_STORAGE_REQUEST);
        }

        // check if application has permission to read from external storage
        // if not, request permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_STORAGE_REQUEST);
            }
        }

        // check if application has permission to use the camera
        // if not, request permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        }

        // try to open file containing existing usernames
        try{
            FileInputStream fis = openFileInput(usernames);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader bufferedReader = new BufferedReader(isr);
            String line;
            users.add("");
            while ((line = bufferedReader.readLine()) != null){
                users.add(line);
            }
            fis.close();
            afos = openFileOutput(usernames, Context.MODE_APPEND);
        // otherwise create the file
        }catch (Exception e){
            try {
                fos = openFileOutput(usernames, Context.MODE_PRIVATE);
            }catch (Exception er){
                er.printStackTrace();
            }
            e.printStackTrace();
        }

        // get the username
        showUsernameAlert();

        // generate the buttons for the layout
        startButton = (Button) findViewById(R.id.startButton);
        exitButton = (Button) findViewById(R.id.exitButton);
        deleteButton = (Button) findViewById(R.id.deleteButton);

        // setup how to handle button clicks
        startButton.setOnClickListener(this);
        exitButton.setOnClickListener(this);
        deleteButton.setOnClickListener(this);

        // create googleapi request
        if (mGoogleApiClient == null){
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        createLocationRequest();

        // create layout that displays previous trips
        linearLayout = (LinearLayout) findViewById(R.id.history_linear);

        // check for previous trips in public directory
        directory = new File(String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)));
        folders = directory.listFiles();
        int size = folders.length;
        tv = new TextView[size];
        TextView temp;
        String textview = "Existing Trips:";
        temp = new TextView(this);
        temp.setText(textview);
        int i = 0;
        // loop through each file found in the public directory
        // if it's a trip, add it to the list to be displayed
        for (File file : folders) {
            if (file.toString().matches("\\S*Trip_\\d*")) {
                temp = new TextView(this);
                temp.setId(i);
                String[] split = file.toString().split("/");
                int index = split.length;
                textview = split[index-1];
                temp.setText(textview);
                temp.setTextColor(Color.BLUE);
                temp.setTextSize(20);
                temp.setClickable(true);
                temp.setOnClickListener(this);
                linearLayout.addView(temp);
                tv[i] = temp;
                i++;
            }
        }
    }

    private void createLocationRequest(){
        mLocationRequest = new LocationRequest();
        // desired interval for updates
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    // function that allows user to specify username
    private void showUsernameAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();

        View myView = inflater.inflate(R.layout.text_dialog, null);

        Spinner spinner = (Spinner) myView.findViewById(R.id.usernamespinner);
        final EditText editText = (EditText) myView.findViewById(R.id.text);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, users);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                editText.setText(adapterView.getItemAtPosition(pos).toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        alertDialog.setMessage("Please enter a username:")
                .setCancelable(false)
                .setView(myView)
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Dialog f = (Dialog) dialogInterface;
                        EditText text = (EditText) f.findViewById(R.id.text);
                        String input = text.getText().toString();
                        // check if username field is blank
                        if (input.matches("")){
                            showUsernameAlert();
                            Toast.makeText(MainActivity.this, "Please enter a username", Toast.LENGTH_LONG).show();
                        }else {
                            username = input;
                            // check if this is first time the application has been run and write to the empty username file
                            if (fos != null){
                                try {
                                    fos.write((username+"\n").getBytes());
                                    fos.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                             // otherwise, append to the existing file
                            }else if (afos != null){
                                try {
                                    Boolean match = false;
                                    for (String user : users){
                                        if (username.matches(user)){
                                            match = true;
                                            break;
                                        }
                                    }
                                    // if it is a new username, add it to the list
                                    // otherwise, do not
                                    if (!match) {
                                        afos.write((username+"\n").getBytes());
                                        afos.close();
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }else{
                                Toast.makeText(MainActivity.this, "Username not stored", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // do nothing
                        finish();
                    }
                });
        final AlertDialog alert = alertDialog.create();
        alert.show();
    }

    // function that handles all click events, specifically buttons or previous trips
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.startButton:
                // check for previous trips in order to know which trip number should be instantiated
                folders = directory.listFiles();
                tripnumber = 1;
                for (File file : folders) {
                    filename = file.toString().split("/");
                    if (filename[filename.length - 1].matches(username + "\\S*Trip_\\d*")) {
                        tripnumber++;
                    }
                }
                // check to see if trip is properly numbered
                String foldername = username+ "_Trip_" + tripnumber;
                imagesFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), foldername);
                while (imagesFolder.exists()){
                    tripnumber++;
                    foldername = username+ "_Trip_" + tripnumber;
                    imagesFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), foldername);
                }
                // must pass variables from this activity to camera activity
                Intent cameraIntent = new Intent(MainActivity.this, CameraActivity.class);
                cameraIntent.putExtra("folder", foldername);
                cameraIntent.putExtra("mode", "new");
                startActivityForResult(cameraIntent, CAMERA_INTENT);
                break;
            case R.id.exitButton:
                showFinishAlert();
                break;
            case R.id.deleteButton:
                showDeleteAlert();
                break;
            default:
                // this default case handles when the user clicks a trip name
                TextView textView = (TextView) findViewById(view.getId());
                Filename = textView.getText().toString();
                imagesFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), Filename);
                // check if the trip is empty or if it is being incorrectly displayed
                if (imagesFolder.listFiles().length == 0 || !imagesFolder.exists()){
                    showEmptyAlert();
                }else{
                    Toast.makeText(this, Filename, Toast.LENGTH_LONG).show();
                    Intent galleryIntent = new Intent(MainActivity.this, GalleryActivity.class);
                    galleryIntent.putExtra("foldername", Filename);
                    startActivityForResult(galleryIntent, GALLERY);
                    break;
                }
        }
    }

    // if a trip is empty or doesn't exist, run this function
    // this function basically resets the list of existing trips being displayed
    private void showEmptyAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setMessage("This trip is empty!")
                .setCancelable(false)
                .setNegativeButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        deleteRecursive(imagesFolder);
                        for (TextView textView : tv){
                            linearLayout.removeView(textView);
                        }
                        // check through public directory to update list of existing trips
                        directory = new File(String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)));
                        folders = directory.listFiles();
                        int size = folders.length;
                        tv = new TextView[size];
                        TextView temp;
                        String textview = "Existing Trips:";
                        temp = new TextView(MainActivity.this);
                        temp.setText(textview);
                        int j = 0;
                        for (File file : folders) {
                            if (file.toString().matches("\\S*Trip_\\d*")) {
                                temp = new TextView(MainActivity.this);
                                temp.setId(j);
                                String[] split = file.toString().split("/");
                                int index = split.length;
                                textview = split[index-1];
                                temp.setText(textview);
                                temp.setTextColor(Color.BLUE);
                                temp.setTextSize(20);
                                temp.setClickable(true);
                                temp.setOnClickListener(MainActivity.this);
                                linearLayout.addView(temp);
                                tv[j] = temp;
                                j++;
                            }
                        }
                    }
                });
        final AlertDialog alert = alertDialog.create();
        alert.show();
    }

    private void startLocationUpdates(){
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    // when the user hits the back button, ask if they want to exit the app
    @Override
    public void onBackPressed(){
        showFinishAlert();
    }

    // prompt user if they want to exit the app
    private void showFinishAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        alertDialog.setMessage("Are you sure you want to exit?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // do nothing
                    }
                });
        final AlertDialog alert = alertDialog.create();
        alert.show();
    }

    private void showConfirmDeleteAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setMessage("Are you sure you want to delete this trip?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (!tripfolder.exists() && !tripfolder.isDirectory()) {
                            showExistsAlert();
                        } else {
                            deleteRecursive(tripfolder);
                            // clear the layout that contains each trip on the device
                            for (TextView textView : tv){
                                linearLayout.removeView(textView);
                            }
                            // update the layout to properly display existing trips
                            directory = new File(String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)));
                            folders = directory.listFiles();
                            int size = folders.length;
                            tv = new TextView[size];
                            TextView temp;
                            String textview = "Existing Trips:";
                            temp = new TextView(MainActivity.this);
                            temp.setText(textview);
                            int j = 0;
                            for (File file : folders) {
                                if (file.toString().matches("\\S*Trip_\\d*")) {
                                    temp = new TextView(MainActivity.this);
                                    temp.setId(j);
                                    String[] split = file.toString().split("/");
                                    int index = split.length;
                                    textview = split[index-1];
                                    temp.setText(textview);
                                    temp.setTextColor(Color.BLUE);
                                    temp.setTextSize(20);
                                    temp.setClickable(true);
                                    temp.setOnClickListener(MainActivity.this);
                                    linearLayout.addView(temp);
                                    tv[j] = temp;
                                    j++;
                                }
                            }
                        }
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });
        final AlertDialog alert = alertDialog.create();
        alert.show();
    }

    // function that requires the user to type out, in full, the trip they want to delete
    private void showDeleteAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        final LayoutInflater inflater = this.getLayoutInflater();

        alertDialog.setMessage("What trip do you want to delete?")
                .setCancelable(false)
                .setView(inflater.inflate(R.layout.text_dialog, null))
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Dialog f = (Dialog) dialogInterface;
                        EditText text = (EditText) f.findViewById(R.id.text);
                        String input = text.getText().toString();
                        tripfolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), input);
                        if (input.matches("")){
                            Toast.makeText(MainActivity.this, "Please enter a trip", Toast.LENGTH_LONG).show();
                        }else {
                            showConfirmDeleteAlert();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // do nothing
                    }
                });
        final AlertDialog alert = alertDialog.create();
        alert.show();
    }

    // function that recursively deletes the files in a directory
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        String dir = fileOrDirectory.getAbsolutePath();
        fileOrDirectory.delete();
        callBroadCast(dir);
    }

    // function that forces the device to check for the existence of a given file path
    // has been problematic on older devices
    private void callBroadCast(String dir) {
        MediaScannerConnection.scanFile(this, new String[]{dir}, null, new MediaScannerConnection.OnScanCompletedListener() {
            @Override
            public void onScanCompleted(String path, Uri uri) {
                Toast.makeText(MainActivity.this, "Scanned", Toast.LENGTH_LONG).show();
                Log.e("ExternalStorage", "Scanned " + path + ":");
                Log.e("ExternalStorage", "-> uri=" + uri);
            }
        });
    }

    // alert the user that selected trip does not actually exist on the phone
    // occasionally happens when a trip is deleted but still is detected by the mediascanner
    private void showExistsAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setMessage("This trip does not exist!")
                .setCancelable(false)
                .setNegativeButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });
        final AlertDialog alert = alertDialog.create();
        alert.show();
    }

    // function that is called when the app returns to the main activity from another one
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // if returning from CameraActivity
        if (requestCode == CAMERA_INTENT && resultCode == RESULT_OK) {
            // reset the list of trips to include the one that was just created
            for (TextView textView : tv){
                linearLayout.removeView(textView);
            }
            directory = new File(String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)));
            folders = directory.listFiles();
            int size = folders.length;
            tv = new TextView[size];
            TextView temp;
            String textview = "Existing Trips:";
            temp = new TextView(this);
            temp.setText(textview);
            int i = 0;
            for (File file : folders) {
                if (file.toString().matches("\\S*Trip_\\d*")) {
                    temp = new TextView(this);
                    temp.setId(i);
                    String[] split = file.toString().split("/");
                    int index = split.length;
                    textview = split[index-1];
                    temp.setText(textview);
                    temp.setTextColor(Color.BLUE);
                    temp.setTextSize(20);
                    temp.setClickable(true);
                    temp.setOnClickListener(this);
                    linearLayout.addView(temp);
                    tv[i] = temp;
                    i++;
                }
            }
         // if returning from GalleryActivity
        }else if (requestCode == GALLERY && resultCode == RESULT_OK){
            // reset the list of trips in case the selected one had all contents deleted
            for (TextView textView : tv){
                linearLayout.removeView(textView);
            }
            directory = new File(String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)));
            folders = directory.listFiles();
            int size = folders.length;
            tv = new TextView[size];
            TextView temp;
            String textview = "Existing Trips:";
            temp = new TextView(this);
            temp.setText(textview);
            int i = 0;
            for (File file : folders) {
                if (file.toString().matches("\\S*Trip_\\d*")) {
                    temp = new TextView(this);
                    temp.setId(i);
                    String[] split = file.toString().split("/");
                    int index = split.length;
                    textview = split[index-1];
                    temp.setText(textview);
                    temp.setTextColor(Color.BLUE);
                    temp.setTextSize(20);
                    temp.setClickable(true);
                    temp.setOnClickListener(this);
                    linearLayout.addView(temp);
                    tv[i] = temp;
                    i++;
                }
            }
        }
    }

    // checks if device has GPS services enabled
    private static boolean isLocationEnabled(Context context){
        int locationMode;
        String locationProviders;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            try{
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);

            }catch (Settings.SettingNotFoundException e){
                e.printStackTrace();
                return false;
            }
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        }else{
            locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }
    }

    // function that prompts user to turn on location
    private void showSettingsAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        alertDialog.setTitle("Location Services");
        alertDialog.setMessage("Your GPS seems to be disabled. This application requires GPS to be turned on. Do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener(){
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id){
                        dialog.cancel();
                        finish();
                    }
                });
        final AlertDialog alert = alertDialog.create();
        alert.show();
    }

    // remaining functions are all needed for location services
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (mCurrentLocation == null){
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        }
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
    }

    @Override
    protected void onStart(){
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onResume(){
        super.onResume();
        if (mGoogleApiClient.isConnected()){
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onStop(){
        mGoogleApiClient.disconnect();
        super.onStop();
    }
}


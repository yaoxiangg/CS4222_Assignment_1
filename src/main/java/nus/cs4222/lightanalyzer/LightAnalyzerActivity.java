package nus.cs4222.lightanalyzer;

import java.io.*;
import java.util.*;
import java.text.*;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.hardware.*;
import android.util.*;
import android.location.*;
import android.provider.*;
import android.net.*;
/**
   Activity that logs Light sensor readings to the sdcard.

   <p> The light sensor is sampled at the 'normal' sampling rate.
   Note that this app does not use a wake lock, so turning off the
   screen may idle the CPU and stop data collection. The screen MUST
   be on during data collection.

   <p> The sensor readings are logged into the sdcard
   under the folder 'LightAnalyzer' to the file 'Light.csv'. 
   The format is as follows --
   'Light.csv':
     Reading Number, Unix timestamp, Human Readable Time, 
      Light reading (lux)
   Remember to reboot the phone before copying the log file 
   from the phone to the laptop (this is to make sure that the 
   log file is flushed from the RAM to the sdcard).

   @author  Kartik S
 */
public class LightAnalyzerActivity 
    extends Activity 
    implements SensorEventListener, LocationListener {

    @Override
    public void onLocationChanged(Location arg0) {
        String lat = String.valueOf(arg0.getLatitude());
        String lon = String.valueOf(arg0.getLongitude());
        Log.e("GPS", "location changed: lat=" + lat + ", lon=" + lon);
        // Light sensor reading details
        final StringBuilder sb = new StringBuilder();
        sb.append( "\nLocation --" );
        sb.append( "\nLatitude: " + lat );
        sb.append( "\nLongitude: " + lon );

        // Update the text view in the main UI thread
        handler.post(new Runnable() {
            @Override
            public void run() {
                placeTextView.setText("<< Outdoor >>");
                locationTextView.setText(sb.toString());
            }
        });

    }
    @Override
    public void onProviderDisabled(String arg0) {
        Log.e("GPS", "provider disabled " + arg0);
    }

    @Override
    public void onProviderEnabled(String arg0) {
        Log.e("GPS", "provider enabled " + arg0);
    }

    @Override
    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        Log.e("GPS", "status changed to " + arg0 + " [" + arg1 + "]");
    }

    /** Called when the activity is created. */
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );

        // Create a handler to the main thread
        handler = new Handler();

        try {

            // Set up the GUI
            setUpGUI();

            // Open the log file
            openLogFile();
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( TAG , "Unable to create activity" , e );
            // Tell the user
            createToast ( "Unable to create activity: " + e.toString() );
        }
    }

    /** Called when the activity is destroyed. */
    @Override
    public void onDestroy() {
        super.onDestroy();

        try {

            // Close the log file
            closeLogFile();

            // Stop sensor sampling (in case the user didn't stop)
            stopLightSampling();
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( TAG , "Unable to destroy activity" , e );
            // Tell the user
            createToast ( "Unable to destroy activity: " + e.toString() );
        }
    }

    /** Helper method that starts light sensor sampling. */
    private void startLightSampling() {

        try {
            // Check the flag
            if ( isLightSamplingOn )
                return;

            // Get the sensor manager
            sensorManager = 
                (SensorManager) getSystemService( Context.SENSOR_SERVICE );
            // Get the light sensor
            lightSensor = 
                (Sensor) sensorManager.getDefaultSensor( Sensor.TYPE_LIGHT );
            if ( lightSensor == null ) {
                createToast( "Light sensor not available" );
                throw new Exception( "Light sensor not available" );
            }

            // Initialise reading count
            numLightReadings = 0;

            // Start light sensor sampling (at normal sampling rate)
            sensorManager.registerListener(this,
                    lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);

            // Set the flag
            isLightSamplingOn = true;
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( TAG , "Unable to start light sampling" , e );
            // Tell the user
            createToast ( "Unable to start light sampling: " + e.toString() );
        }
    }

    /** Helper method that stops light sensor sampling. */
    private void stopLightSampling() {

        try {

            // Check the flag
            if ( ! isLightSamplingOn )
                return;

            // Set the flag
            isLightSamplingOn = false;

            // Stop light sensor sampling
            sensorManager.unregisterListener( this ,
                                              lightSensor );
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( TAG , "Unable to stop light sensor sampling" , e );
            // Tell the user
            createToast ( "Unable to stop light sensor sampling: " + e.toString() );
        }
        finally {
            sensorManager = null;
            lightSensor = null;
        }
    }

    /** Called when the light sensor value has changed. */
    @Override
    public void onSensorChanged( SensorEvent event ) {

        // SensorEvent's timestamp is the device uptime, 
        //  but for logging we use UTC time
        long timestamp = System.currentTimeMillis();

        // Validity check: This must be the light sensor
        if ( event.sensor.getType() != Sensor.TYPE_LIGHT ) 
            return;

        // Update the reading count
        ++numLightReadings;

        // Get the ambient light level in lux
        float lux = event.values[0];

        // Turn GPS on base on lux threshold
        int LIGHT_THRESHOLD = 800;
        if (lux > LIGHT_THRESHOLD) {
            lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, this);
            placeTextView.setText("<< Outdoor >>");
        } else {
            if (lm != null) {
                lm.removeUpdates(this);
            }
            lm=null;
            placeTextView.setText("<< Indoor >>");
        }
        // Log the reading
        logLightReading(timestamp, lux);

        // Update the GUI
        updateLightTextView( timestamp , lux );

        //onLocationChanged(lm.getLastKnownLocation(LocationManager.GPS_PROVIDER));
    }

    /** Called when the light sensor accuracy changes. */
    @Override
    public void onAccuracyChanged( Sensor sensor , 
                                   int accuracy ) {
        // Ignore
    }

    /** Helper method that sets up the GUI. */
    private void setUpGUI() {

        // Set the GUI content to the XML layout specified
        setContentView( R.layout.main );

        // Get references to GUI widgets
        startLightButton = 
            (Button) findViewById( R.id.PA1Activity_Button_StartLight );
        stopLightButton = 
            (Button) findViewById( R.id.PA1Activity_Button_StopLight );
        lightTextView = 
            (TextView) findViewById( R.id.PA1Activity_TextView_Light );
        locationTextView =
            (TextView) findViewById( R.id.PA1Activity_TextView_Location );
        placeTextView =
            (TextView) findViewById( R.id.PA1Activity_TextView_Place );
        // Disable the stop button
        stopLightButton.setEnabled(false);

        // Set up button listeners
        setUpButtonListeners();
    }

    private int promptGPS() {
        int state = 0;
        try {
            state = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        if (state == 0){
            Intent onGPS = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(onGPS);
        }
        return state;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (GPS_STATE == 0) {
            GPS_STATE = promptGPS();
        }
    }

    /** Helper method that sets up button listeners. */
    private void setUpButtonListeners() {

        // Start light sampling
        startLightButton.setOnClickListener( new View.OnClickListener() {
                public void onClick ( View v ) {
                    //StartGPS
                    GPS_STATE = promptGPS();
                    if (GPS_STATE == 0) {
                        return;
                    }
                    // Start light sampling
                    startLightSampling();

                    // Disable the start button and enable the stop button
                    startLightButton.setEnabled( false );
                    stopLightButton.setEnabled(true);
                    // Inform the user
                    lightTextView.setText( "\nAwaiting Light readings...\n" );
                    locationTextView.setText( "\nAwaiting Location readings...\n");
                    createToast( "Light sensor sampling started" );
                }
            } );

        // Stop light sampling
        stopLightButton.setOnClickListener( new View.OnClickListener() {
                public void onClick ( View v ) {
                    // Stop light sampling
                    stopLightSampling();
                    // Disable the stop button and enable the start button
                    startLightButton.setEnabled( true );
                    stopLightButton.setEnabled( false );
                    // Inform the user
                    createToast( "Light sensor sampling stopped" );
                }
            } );
    }

    /** Helper method that updates the light text view. */
    private void updateLightTextView( long timestamp , 
                                      float lux ) {

        // Light sensor reading details
        final StringBuilder sb = new StringBuilder();
        sb.append( "\nLight--" );
        sb.append( "\nNumber of readings: " + numLightReadings );
        sb.append( "\nAmbient light level (lux): " + lux + "\n");

        // Update the text view in the main UI thread
        handler.post ( new Runnable() {
                @Override
                public void run() {
                    lightTextView.setText( sb.toString() );
                }
            } );
    }

    /** Helper method to create toasts for the user. */
    private void createToast( final String toastMessage ) {

        // Post a runnable in the Main UI thread
        handler.post ( new Runnable() {
                @Override
                public void run() {
                    Toast.makeText ( getApplicationContext() , 
                                     toastMessage , 
                                     Toast.LENGTH_SHORT ).show();
                }
            } );
    }

    /** Helper method to make the log file ready for writing. */
    public void openLogFile() 
        throws IOException {

        // First, check if the sdcard is available for writing
        String externalStorageState = Environment.getExternalStorageState();
        if ( ! externalStorageState.equals ( Environment.MEDIA_MOUNTED ) &&
             ! externalStorageState.equals ( Environment.MEDIA_SHARED ) ) {
            throw new IOException ( "sdcard is not mounted on the filesystem" );
        }

        // Second, create the log directory
        File logDirectory = new File( Environment.getExternalStorageDirectory() , 
                                      "LightAnalyzer" );
        logDirectory.mkdirs();
        if ( ! logDirectory.isDirectory() ) {
            throw new IOException( "Unable to create log directory" );
        }

        // Third, create output streams for the log file (APPEND MODE!)
        File logFile = new File( logDirectory , "Light.csv" );
        FileOutputStream fout = new FileOutputStream( logFile , true );
        lightLogFileOut = new PrintWriter( fout );
    }

    /** Helper method that closes the log file. */
    public void closeLogFile() {

        // Close the light sensor log file
        try {
            lightLogFileOut.close();
        }
        catch( Exception e ) {
            Log.e( TAG , "Unable to close light sensor log file" , e );
        }
        finally {
            lightLogFileOut = null;
        }
    }

    /** Helper method that logs the light sensor reading. */
    private void logLightReading( long timestamp , 
                                  float lux ) {

        // Light sensor reading details
        final StringBuilder sb = new StringBuilder();
        sb.append( numLightReadings + "," );
        sb.append( timestamp + "," );
        sb.append( getHumanReadableTime( timestamp ) + "," );
        sb.append( lux );

        // Log to the file (and flush it)
        lightLogFileOut.println( sb.toString() );
        lightLogFileOut.flush();
    }

    /** Helper method to get the human readable time from unix time. */
    private static String getHumanReadableTime( long unixTime ) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd-h-mm-ssa" );
        return sdf.format( new Date( unixTime ) );
    }

    /** Start light sensor sampling button. */
    private Button startLightButton;
    /** Stop light sensor sampling button. */
    private Button stopLightButton;
    /** Light sensor reading textview. */
    private TextView lightTextView;

    /** Sensor Manager. */
    private SensorManager sensorManager;
    /** Light sensor. */
    private Sensor lightSensor;

    /** Number of light sensor readings so far. */
    private int numLightReadings;
    /** Flag to indicate that light sensing is going on. */
    private boolean isLightSamplingOn;

    /** Handler to the main thread. */
    private Handler handler;

    /** Light sensor log file output stream. */
    public PrintWriter lightLogFileOut;

    /** DDMS Log Tag. */
    private static final String TAG = "LightAnalyzerActivity";

    //GPS
    private LocationManager lm;
    private TextView locationTextView;
    private TextView placeTextView;
    private int GPS_STATE;
}

package com.tricodia.bcontroller;


import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.prefs.Preferences;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {
    BluetoothAdapter bluetoothAdapter;
    public static final int REQUEST_WRITE_STORAGE = 112;
    final int REQUEST_ENABLE_BT = 99;
    ConnectThread currentThread;
    InputStream iStream;
    Button btn;
    TextView txt,mainText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn = findViewById(R.id.btn);
        txt = findViewById(R.id.text);
        mainText = findViewById(R.id.mainTextView);
        btn.setEnabled(false);
        boolean hasPermission = (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        if (!hasPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        } else {
            btn.setEnabled(true);
        }
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (btn.getText().equals("Start")) {
                    startBluetooth();
                    btn.setText("Stop");
                } else {
                    mainText.setText("");
                    if (currentThread != null) {
                        currentThread.cancel();
                    }
                    btn.setText("Start");
                    txt.setText("Searching");
                }
            }
        });


    }


    private void startBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device is not compatible", Toast.LENGTH_SHORT).show();
        }
        else {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            String s = "";
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    String deviceName = device.getName();
//                    s = s + "\n" + deviceName;
                    if (deviceName.equals("HC-05")) {
                        currentThread = new ConnectThread(device);
                        currentThread.start();
                    }
                }
                Log.i("Devices ", s);
            }

        }
    }


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                Log.e("Ouch", "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            bluetoothAdapter.cancelDiscovery();

            try {
                mmSocket.connect();
                manageMyConnectedSocket(mmSocket);
            } catch (IOException connectException) {

                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e("Ouch", "Could not close the client socket", closeException);
                }
            }

        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("Ouch", "Could not close the client socket", e);
            }
        }
    }

    private void manageMyConnectedSocket (BluetoothSocket mmSocket){
        try {
            if(mmSocket!=null) {

                txt.setText("Connected!");

                iStream = mmSocket.getInputStream();
                String mainString = "";
                while (iStream != null) {


                    String s = read();
                    Log.i("GOT ++++++++++++",s);
                    int i = s.indexOf(';');
                    if(i<0){
                        mainString = mainString+s;
                    }
                    else {
                        String[] strList = s.split(";");
                        if (strList.length > 0) {
                            String finalString = mainString+strList[0];

                            for(int k=1;k<strList.length-1;k++){
                                finalString=finalString+"\n"+strList[k];
                            }

                            if(s.lastIndexOf(';')==(s.length()-1)){
                                mainString = "";
                            }
                            else{
                                mainString=strList[strList.length-1];
                            }
                            if (finalString.length() > 0) {
                                Log.i("String ++++++++++++", finalString);
                                logMessage(finalString);
                            }

                        }
                        else{
                            Log.i("String ++++++++++++",mainString);
                            logMessage(mainString);
                            mainString="";
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logMessage(final String s) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                mainText.append("\n"+s);
                Date c = Calendar.getInstance().getTime();
                SimpleDateFormat df = new SimpleDateFormat("dd_MMM_yyyy");
                SimpleDateFormat df1 = new SimpleDateFormat("hh:mm:ss");
                String fileName = df.format(c);
                String time = df1.format(c);
                writetoFile(fileName,s,time);
            }
        });

    }

    private void writetoFile(String fileName,String data,String time) {
        File root = android.os.Environment.getExternalStorageDirectory();
        File dir = new File (root.getAbsolutePath() + "/PROJECT/");
        if(!dir.exists())
            dir.mkdirs();
        File file = new File(dir, fileName+".txt");
        try {
            FileOutputStream fileInput = new FileOutputStream(file,true);
            PrintStream printstream = new PrintStream(fileInput);
            printstream.print(time+"\t:\t"+data+"\n");
            fileInput.close();
        } catch (Exception e) {
            Log.i("ERR",e.getMessage());
        }
    }

    private String read() {

        String s = "";

        try {

            if (iStream!=null) {

                byte[] inBuffer = new byte[1024];
                int bytesRead = iStream.read(inBuffer);
                s = new String(inBuffer, "ASCII");
                s = s.substring(0, bytesRead);
            }
            else{
                Log.i("Err+++++++++++", "ISTREAM failed!");
            }

        } catch (Exception e) {
            Log.i("Fail", "Read failed!", e);
        }

        return s;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_WRITE_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "The app was allowed to write to your storage!", Toast.LENGTH_LONG).show();
                    btn.setEnabled(true);
                    // Reload the activity with permission granted or use the features what required the permission
                } else {
                    Toast.makeText(this, "The app was not allowed to write to your storage. Hence, it cannot function properly. Please consider granting it this permission", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}

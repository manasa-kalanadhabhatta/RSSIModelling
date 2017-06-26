package com.insideout.rssimodelling;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.opencsv.CSVWriter;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {

    private static int NUM_SCANS, NUM_EXPERIMENTS;
    private static long SCAN_FREQUENCY;

    private static ArrayList<String[]> sRecordArrayList;

    BluetoothAdapter mBluetoothAdapter;
    BluetoothAdapter.LeScanCallback mLeScanCallback;

    CSVWriter mScanWriter, mExperimentWriter;

    static int count = 0, invisible = 0, visible = 0, expNumber;
    double[] mRssiArray;
    private String mBeaconName, mDistance;
    long mScanTime, mStartTime, mFinishTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }

        sRecordArrayList = new ArrayList<>();
    }

    private void initialize(){
        final Button scanButton = (Button)findViewById(R.id.scanButton);
        scanButton.setEnabled(false);

        EditText beaconText = (EditText)findViewById(R.id.beacon);
        mBeaconName = beaconText.getText().toString();

        EditText numScansText = (EditText) findViewById(R.id.numberOfScans);
        NUM_SCANS = Integer.parseInt(numScansText.getText().toString());

        EditText numExpText = (EditText)findViewById(R.id.numberOfExperiments);
        NUM_EXPERIMENTS = Integer.parseInt(numExpText.getText().toString());

        EditText frequencyText = (EditText)findViewById(R.id.frequency);
        SCAN_FREQUENCY = Long.parseLong(frequencyText.getText().toString());

        RadioGroup distanceGroup = (RadioGroup)findViewById(R.id.distanceGroup);

        RadioButton button = (RadioButton)findViewById(distanceGroup.getCheckedRadioButtonId());
        mDistance = button.getText().toString();
    }

    private void createFiles(){
        File folder = new File(Environment.getExternalStorageDirectory()+"/RSSIModels");
        Log.d("Info:","Folder:"+ folder.toString());
        boolean var = false;
        if (!folder.exists())
            var = folder.mkdir();

        //timestamp_e_t_d
        try {
            String experimentsFile = folder.toString()+"/"+mScanTime+"_"+ mBeaconName +"_"+NUM_EXPERIMENTS+"_"+SCAN_FREQUENCY+"_"+ mDistance +".csv";
            mExperimentWriter = new CSVWriter(new FileWriter(experimentsFile));
            String[] firstLine = new String[]{"Experiment Number","Beacon Name","Scans Visible","Scans Invisible","Time Taken","Mean","Standard Deviation"};
            mExperimentWriter.writeNext(firstLine);

            String scansFile = folder.toString()+"/"+mScanTime+"_"+ mBeaconName +"_"+NUM_SCANS+"_"+NUM_EXPERIMENTS+"_"+SCAN_FREQUENCY+"_"+ mDistance +".csv";
            //timestamp_s_e_t_d
            mScanWriter = new CSVWriter(new FileWriter(scansFile));
            String[] title = new String[]{"MAC Address","RSSI"};
            mScanWriter.writeNext(title);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void scan(View view) throws IOException {
        mScanTime = System.currentTimeMillis();

        initialize();
        createFiles();

        mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
                String[] record = new String[3];
                record[0] = device.getAddress();    //MAC Address
                record[1] = String.valueOf(rssi);   //RSSI
                sRecordArrayList.add(record);
            }
        };

        mBluetoothAdapter.startLeScan(mLeScanCallback);

        mRssiArray = new double[NUM_SCANS];
        count = 0; visible = 0; invisible = 0;
        mStartTime = System.currentTimeMillis();
        expNumber = 1;

        new CountDownTimer(NUM_SCANS*SCAN_FREQUENCY, SCAN_FREQUENCY){

            @Override
            public void onTick(long pL) {
                if(count<=NUM_SCANS){
                    if(sRecordArrayList.size()>0){
                        //if anything visible
                        String scanRecord[] = new String[2];
                        scanRecord[0] = sRecordArrayList.get(0)[0];

                        int rssiSum = 0;
                        for(int j=0; j<sRecordArrayList.size(); j++){
                            rssiSum += Integer.parseInt(sRecordArrayList.get(j)[1]);
                        }
                        scanRecord[1] = String.valueOf(rssiSum/sRecordArrayList.size());

                        mRssiArray[visible] = rssiSum/sRecordArrayList.size();
                        visible++;
                        mScanWriter.writeNext(scanRecord);

                    }else {
                        //nothing visible
                        invisible++;
                    }
                    sRecordArrayList = new ArrayList<>();
                    count++;
                }
            }

            @Override
            public void onFinish() {
                Log.i("Handler","This code here");

                mFinishTime = System.currentTimeMillis();

                DescriptiveStatistics prob = new DescriptiveStatistics(mRssiArray);
                double mean = prob.getMean();
                Log.d("Info:","mean: "+mean);
                double standardDeviation = prob.getStandardDeviation();
                Log.d("Info:", "sd: "+standardDeviation);

                String[] input = new String[]{String.valueOf(expNumber),mBeaconName,String.valueOf(visible), String.valueOf(invisible), String.valueOf((mFinishTime - mStartTime)/1000), String.valueOf(mean), String.valueOf(standardDeviation)};
                mExperimentWriter.writeNext(input);

                expNumber++;
                if(expNumber<=NUM_EXPERIMENTS) {
                    mRssiArray = new double[NUM_SCANS];
                    count = 0; visible = 0; invisible = 0;
                    mStartTime = System.currentTimeMillis();
                    this.start();
                }else {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    try {
                        mScanWriter.close();
                        mExperimentWriter.close();
                    } catch (IOException pE) {
                        pE.printStackTrace();
                    }
                    Toast.makeText(getApplicationContext(), "Done!", Toast.LENGTH_LONG).show();
                    findViewById(R.id.scanButton).setEnabled(true);
                }
            }
        }.start();
    }

}

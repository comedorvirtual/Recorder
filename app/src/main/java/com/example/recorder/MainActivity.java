package com.example.recorder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
        private static int MICROPHONE_PERMISSION_CODE = 200;
        private static final int RECORDER_SAMPLERATE = 8000;
    //int sampleRate = 44100;
        private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
        private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        private AudioRecord recorder = null;
        private AudioTrack track = null;
        private Thread recordingThread = null;
        private boolean isRecording = false;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            if (isMicrophonePresent()) getMicrophonePermission();

            setButtonHandlers();
            enableButtons(false);

            int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                    RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
        }

        private void setButtonHandlers() {
            (findViewById(R.id.button_iniciar)).setOnClickListener(btnClick);
            ( findViewById(R.id.button_parar)).setOnClickListener(btnClick);
            (findViewById(R.id.button_reproducir)).setOnClickListener(btnClick);
        }

        private void enableButton(int id, boolean isEnable) {
            ( findViewById(id)).setEnabled(isEnable);
        }

        private void enableButtons(boolean isRecording) {
            enableButton(R.id.button_iniciar, !isRecording);
            enableButton(R.id.button_parar, isRecording);
        }

        int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
        int BytesPerElement = 2; // 2 bytes in 16bit format

        private void startRecording() {

            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);

            recorder.startRecording();
            isRecording = true;
            recordingThread = new Thread(new Runnable() {
                public void run() {
                    writeAudioDataToFile();
                }
            }, "AudioRecorder Thread");
            recordingThread.start();
        }

        //convert short to byte
        private byte[] short2byte(short[] sData) {
            int shortArrsize = sData.length;
            byte[] bytes = new byte[shortArrsize * 2];
            for (int i = 0; i < shortArrsize; i++) {
                bytes[i * 2] = (byte) (sData[i] & 0x00FF);
                bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
                sData[i] = 0;
            }
            return bytes;

        }

        private void writeAudioDataToFile() {
            // Write the output audio in byte

            String filePath = getRecordingFilePath();
            short sData[] = new short[BufferElements2Rec];

            FileOutputStream os = null;
            try {
                os = new FileOutputStream(filePath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            while (isRecording) {
                // gets the voice output from microphone to byte format

                recorder.read(sData, 0, BufferElements2Rec);
                System.out.println("Short writing to file" + sData.toString());
                try {
                    // // writes the data to file from buffer
                    // // stores the voice buffer
                    byte[] bData = short2byte(sData);
                    os.write(bData, 0, BufferElements2Rec * BytesPerElement);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }
    }

    private View.OnClickListener btnClick = v -> {
        switch (v.getId()) {
            case R.id.button_iniciar: {
                enableButtons(true);
                startRecording();
                break;
            }
            case R.id.button_parar: {
                enableButtons(false);
                stopRecording();
                break;
            }
            case R.id.button_reproducir: {
                playRecord();
                break;
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isMicrophonePresent(){
        if(this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE))
            return true;
        return false;
    }
    private void getMicrophonePermission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this,new String[]
                    {Manifest.permission.RECORD_AUDIO},MICROPHONE_PERMISSION_CODE);
        }
    }
    public String getRecordingFilePath(){
        ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
        File musicDIrectory = contextWrapper.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File file = new File(musicDIrectory, "testRecording" + ".pcm");
        return  file.getPath();
    }

    public void playRecord()
    {
        File recordFile = new File(getRecordingFilePath());

        int shortSizeInBytes = Short.SIZE / Byte.SIZE;
        int bufferSizeInBytes = (int) (recordFile.length() / shortSizeInBytes);
        short[] audioData = new short[bufferSizeInBytes];
        try
        {
            InputStream inputStream = new FileInputStream(recordFile);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            DataInputStream dataInputStream = new DataInputStream(bufferedInputStream);

            int i = 0;
            while (dataInputStream.available() > 0)
            {
                audioData[i] = dataInputStream.readShort();
                i++;
            }

            dataInputStream.close();

            track = new AudioTrack(AudioManager.STREAM_MUSIC, RECORDER_SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeInBytes, AudioTrack.MODE_STREAM);

            track.play();
            track.write(audioData, 0, bufferSizeInBytes);
        }
        catch (FileNotFoundException e)
        {
            Log.e("activity main","error al leer archivo");
            e.printStackTrace();
        }
        catch (IOException e)
        {
            Log.e("activity main","error de io");
            e.printStackTrace();
        }
    }


}
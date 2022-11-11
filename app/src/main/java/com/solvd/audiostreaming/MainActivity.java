package com.solvd.audiostreaming;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private final static int MEDIA_PROJECTION_REQUEST_CODE = 13;
    private final static int RECORD_AUDIO_PERMISSION_REQUEST_CODE = 42;

    private MediaProjectionManager mediaProjectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startStreaming();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStreaming();
    }


    private void startStreaming() {
        if(!isRecordingAccessGranted()) {
            requestAccessForRecording();
        } else {
            makeMediaProjectionRequest();
        }
    }

    private boolean isRecordingAccessGranted() {
        return ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAccessForRecording() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                RECORD_AUDIO_PERMISSION_REQUEST_CODE
        );
    }

    private void makeMediaProjectionRequest() {
        mediaProjectionManager = (MediaProjectionManager) getApplicationContext()
                .getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                MEDIA_PROJECTION_REQUEST_CODE
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            if(resultCode == Activity.RESULT_OK) {
                Intent intent = new Intent(this, AudioStreamingService.class);
                intent.setAction(AudioStreamingService.ACTION_START);
                intent.putExtra(AudioStreamingService.EXTRA_RESULT_DATA , data);
                startForegroundService(intent);
            }
        }

    }

    private void stopStreaming() {
        Intent intent = new Intent(this, AudioStreamingService.class);
        intent.setAction(AudioStreamingService.ACTION_STOP);
        startService(intent);
    }
}
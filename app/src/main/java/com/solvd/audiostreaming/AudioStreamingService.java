package com.solvd.audiostreaming;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class AudioStreamingService extends Service {
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private Thread audioStreamingThread;
    private AudioRecord audioRecord;

    private final static int SERVICE_ID = 123;
    private final static String NOTIFICATION_CHANNEL_ID = "AudioStreaming channel";
    public final static String ACTION_START = "AudioCaptureService:Start";
    public final static String ACTION_STOP = "AudioCaptureService:Stop";
    public final static String EXTRA_RESULT_DATA = "AudioCaptureService:Extra:ResultData";
    private final static String HOST = "<SET YOUR HOST THERE>";
    private final static int PORT = 5999;
    private final static int SAMPLE_RATE = 8000;
    private final static int MIN_BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
    ) * 2;

    @Override
    public void onCreate() {
        super.onCreate();
        configureNotificationChannel();

        startForeground(SERVICE_ID,
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).build());
        mediaProjectionManager = (MediaProjectionManager) getApplicationContext()
                .getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    private void configureNotificationChannel() {
        NotificationChannel notificationChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Audion Streaming service channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(notificationChannel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (intent.getAction()) {
                case ACTION_START:
                    mediaProjection = mediaProjectionManager.getMediaProjection(
                            Activity.RESULT_OK,
                            intent.getParcelableExtra(EXTRA_RESULT_DATA)

                    );
                    startStreaming();
                    return Service.START_STICKY;
                case ACTION_STOP:
                    stopStreaming();
                    return Service.START_NOT_STICKY;
                default:
                    throw new IllegalArgumentException("Unexpected intent action: " + intent.getAction());
            }
        } else {
            return Service.START_NOT_STICKY;
        }
    }

    private void startStreaming() {
        AudioPlaybackCaptureConfiguration audioPlaybackCaptureConfiguration =
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(MIN_BUFFER_SIZE)
                .setAudioPlaybackCaptureConfig(audioPlaybackCaptureConfiguration)
                .build();
        audioRecord.startRecording();
        audioStreamingThread = new Thread(this::sendStreamingThroughWebsocket);
        audioStreamingThread.start();
    }

    private void stopStreaming() {
        audioStreamingThread.interrupt();
        audioRecord.stop();
        audioRecord.release();
        mediaProjection.stop();
        stopSelf();
    }

    public void sendStreamingThroughWebsocket() {
        try {
            DatagramSocket socket = new DatagramSocket();
            byte[] buffer = new byte[MIN_BUFFER_SIZE];
            Log.d("AS", "BUFFER SIZE IS: " + MIN_BUFFER_SIZE);
            final InetAddress target = InetAddress.getByName(HOST);
            DatagramPacket pack;
            while (!audioStreamingThread.isInterrupted()) {
                audioRecord.read(buffer, 0, buffer.length);
                pack = new DatagramPacket(buffer, buffer.length, target, PORT);
                socket.send(pack);
            }
        } catch (SocketException e) {
            Log.d("AS", "SocketException find while trying to stream audio", e);
        } catch (UnknownHostException e) {
            Log.d("AS", "UnknownHostException find while trying to stream audio", e);
        } catch (IOException e) {
            Log.d("AS", "Failed to send DataPacket to host: IOException", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

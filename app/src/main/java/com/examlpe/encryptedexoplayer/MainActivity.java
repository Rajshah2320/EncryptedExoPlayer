package com.examlpe.encryptedexoplayer;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.crypto.AesCipherDataSink;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import okhttp3.OkHttpClient;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final String KEY = "passwordpassword";

    private File mFile;
    private File[] files;

    private PlayerView mPlayerView;
    private Button mEncryptButton;
    private ListView listView;
    private ArrayList<File> fileArrayList;
    private ArrayList<String> fileNames, filePaths;
    private SimpleExoPlayer player;
    private InputStream inputStream;
    private Boolean isYoutubeLink=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();
        Boolean root=RootUtil.isDeviceRooted();
        Boolean emulator=CommonUtils.isEmulator(this);
        Log.i(TAG, "onCreate: Rooted "+root +" emu "+emulator);
        /*
        storageRef.child("do.mp4").getBytes(1024 * 1024 * 10).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                encryptFile(bytes, KEY);
            }
        });
*/
        listView = findViewById(R.id.listView);
        mPlayerView = findViewById(R.id.player_view);

        fileArrayList = new ArrayList<>();
        fileNames = new ArrayList<>();
        filePaths = new ArrayList<>();
        files = getExternalFilesDir("").listFiles();

        for (File file : files) {
            fileArrayList.add(file);
            filePaths.add(file.getAbsolutePath());
            fileNames.add(file.getName());
        }

        ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, fileNames);
        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (player != null)
                    player.release();
                mFile = new File(filePaths.get(i));
                initPlayer(mFile);
                // encryptFile(mFile,KEY);
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.release();
    }

    @Override
    protected void onPause() {
        super.onPause();
        player.release();
    }

    private void initPlayer(File file) {

        /*
        DataSource.Factory dataSourceFactory =
                new CryptedDefaultDataSourceFactory(
                        KEY,
                        this,
                        new OkHttpDataSourceFactory(new OkHttpClient(), Util.getUserAgent(this, "exoPlayerTest"))
                );

        MediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                .setExtractorsFactory(new DefaultExtractorsFactory())
                .createMediaSource(Uri.parse(file.getAbsolutePath()));

         */
      
        player=new SimpleExoPlayer.Builder(MainActivity.this).build();
        MediaSource mediaSource=buildMediaSource(Uri.fromFile(file));
        mPlayerView.setPlayer(player);
        mPlayerView.setControllerShowTimeoutMs(0);
        mPlayerView.setControllerAutoShow(false);

        player.prepare(mediaSource);
        player.setPlayWhenReady(true);
    }

    private boolean hasFile(File mFile) {
        return mFile != null
                && mFile.exists()
                && mFile.length() > 0;
    }

    private void encryptFile(byte[] inputStreamBytes, String secretKey) {
        try {
            // Fully read the input stream.
            //FileInputStream inputStream =new FileInputStream(file);
            File mFile = new File(getExternalFilesDir("").getAbsolutePath() + "/encrypted.mp4");

            // byte[] inputStreamBytes = toByteArray(inputStream);
            // inputStream.close();

            // Create a sink that will encrypt and write to file.
            AesCipherDataSink encryptingDataSink = new AesCipherDataSink(
                    Util.getUtf8Bytes(secretKey),
                    new DataSink() {
                        private FileOutputStream fileOutputStream;

                        @Override
                        public void open(DataSpec dataSpec) throws IOException {
                            fileOutputStream = new FileOutputStream(mFile);
                        }

                        @Override
                        public void write(byte[] buffer, int offset, int length) throws IOException {
                            fileOutputStream.write(buffer, offset, length);
                        }

                        @Override
                        public void close() throws IOException {
                            fileOutputStream.close();
                        }
                    });

            // Push the data through the sink, and close everything.
            encryptingDataSink.open(new DataSpec(Uri.fromFile(mFile)));
            encryptingDataSink.write(inputStreamBytes, 0, inputStreamBytes.length);
            encryptingDataSink.close();

            Toast.makeText(this, "File encrypted", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MediaSource buildMediaSource(Uri uri) {
        DataSource.Factory dataSourceFactory = null;
        if (isYoutubeLink) {
            dataSourceFactory = new DefaultDataSourceFactory
                    (this, Util.getUserAgent(getApplicationContext(), "App"));
        } else {
            // file based media. Assume encryption enabled
            dataSourceFactory = new CryptedDefaultDataSourceFactory(
                    KEY,
                    this,
                    new DefaultBandwidthMeter.Builder(getApplicationContext()).build(),
                    new OkHttpDataSourceFactory(
                            new OkHttpClient(),
                            Util.getUserAgent(getApplicationContext(),
                                    "App")
                    ));
        }
        return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
    }
}

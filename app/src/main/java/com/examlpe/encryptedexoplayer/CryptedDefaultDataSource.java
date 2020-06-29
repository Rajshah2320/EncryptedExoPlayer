package com.examlpe.encryptedexoplayer;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.crypto.AesCipherDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CryptedDefaultDataSource implements DataSource {

    private static final String TAG = "CryptedDefaultDataSource";

    private final Context context;
    private final List<TransferListener> transferListeners;
    private final DataSource baseDataSource;

    // Lazily initialized.
    private @Nullable
    DataSource fileDataSource;
    private @Nullable
    DataSource aesCipherDataSource;

    private @Nullable
    DataSource dataSource;

    private String key;

    public CryptedDefaultDataSource(String key, Context context, DataSource baseDataSource) {
        this.key = key;
        this.context = context.getApplicationContext();
        this.baseDataSource = Assertions.checkNotNull(baseDataSource);
        transferListeners = new ArrayList<>();
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        baseDataSource.addTransferListener(transferListener);
        transferListeners.add(transferListener);
        maybeAddListenerToDataSource(fileDataSource, transferListener);
        maybeAddListenerToDataSource(aesCipherDataSource, transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        Assertions.checkState(dataSource == null);
        if (Util.isLocalFileUri(dataSpec.uri)) {
            dataSource = getCryptedDataSource(key, getFileDataSource());
        } else {
            dataSource = getCryptedDataSource(key, baseDataSource);
        }
        // Open the source and return.
        return dataSource.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return Assertions.checkNotNull(dataSource).read(buffer, offset, readLength);
    }

    @Nullable
    @Override
    public Uri getUri() {
        return dataSource == null ? null : dataSource.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return dataSource == null
                ? DataSource.super.getResponseHeaders()
                : dataSource.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        if (dataSource != null) {
            try {
                dataSource.close();
            } finally {
                dataSource = null;
            }
        }
    }

    private DataSource getFileDataSource() {
        if (fileDataSource == null) {
            fileDataSource = new FileDataSource();
            addListenersToDataSource(fileDataSource);
        }
        return fileDataSource;
    }

    private DataSource getCryptedDataSource(String key, DataSource upstreamDataSource) {
        if (aesCipherDataSource == null) {
          aesCipherDataSource = new AesCipherDataSource(key.getBytes(), upstreamDataSource);

            addListenersToDataSource(aesCipherDataSource);
        }
        return aesCipherDataSource;
    }

    private void addListenersToDataSource(DataSource dataSource) {
        for (int i = 0; i < transferListeners.size(); i++) {
            dataSource.addTransferListener(transferListeners.get(i));
        }
    }

    private void maybeAddListenerToDataSource(
            @Nullable DataSource dataSource, TransferListener listener) {
        if (dataSource != null) {
            dataSource.addTransferListener(listener);
        }
    }

}

package com.examlpe.encryptedexoplayer;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

public class CryptedDefaultDataSourceFactory implements DataSource.Factory {

    private final Context context;
    private final @Nullable
    TransferListener listener;
    private final DataSource.Factory baseDataSourceFactory;
    private final String key;

    public CryptedDefaultDataSourceFactory(String key, Context context, DataSource.Factory baseDataSourceFactory) {
        this(key, context, /* listener= */ null, baseDataSourceFactory);
    }

    public CryptedDefaultDataSourceFactory(
            String key,
            Context context,
            @Nullable TransferListener listener,
            DataSource.Factory baseDataSourceFactory) {
        this.key = key;
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.baseDataSourceFactory = baseDataSourceFactory;
    }

    @Override
    public DataSource createDataSource() {
        CryptedDefaultDataSource dataSource = new CryptedDefaultDataSource(key, context, baseDataSourceFactory.createDataSource());
        if (listener != null) {
            dataSource.addTransferListener(listener);
        }
        return dataSource;
    }

}

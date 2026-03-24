package com.fourtwo.blockertemplate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class BlockerActivity extends Activity {

    private static final String TAG = "BlockerActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        finish();
        overridePendingTransition(0, 0);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Log.d(TAG, "Blocked uri: " + intent.getDataString());
    }
}
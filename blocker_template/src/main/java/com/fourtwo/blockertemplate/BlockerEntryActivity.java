package com.fourtwo.blockertemplate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class BlockerEntryActivity extends Activity {

    private static final String TAG = "BlockerEntryActivity";
    public static final String EXTRA_ORIGINAL_INTENT = "extra_original_intent";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            finishSilently();
            return;
        }

        String uri = intent.getDataString();
        if (uri == null || uri.trim().isEmpty()) {
            try {
                uri = intent.toUri(Intent.URI_INTENT_SCHEME);
            } catch (Exception ignored) {
            }
        }

        Log.d(TAG, "Intercepted uri: " + uri);

        if (RuleStore.matches(this, uri)) {
            Log.d(TAG, "Rule matched, silently blocked");
            finishSilently();
            return;
        }

        Intent decisionIntent = new Intent(this, DecisionActivity.class);
        decisionIntent.putExtra(EXTRA_ORIGINAL_INTENT, intent);
        decisionIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(decisionIntent);
        overridePendingTransition(R.anim.decision_enter, 0);

        finishSilently();
    }

    private void finishSilently() {
        finish();
        overridePendingTransition(0, 0);
    }
}
package android.app;

import android.content.Intent;
import android.os.Bundle;

interface IActivityManager {
    int startActivityAsUser(in IBinder caller, String callingPackage, in Intent intent, String resolvedType, in IBinder resultTo, String resultWho, int requestCode, int flags, in Bundle profilerInfo, in Bundle options, int userId);

    int startActivityAsUserWithFeature(in IBinder caller, String callingPackage, String callingFeatureId, in Intent intent, String resolvedType, in IBinder resultTo, String resultWho, int requestCode, int flags, in Bundle profilerInfo, in Bundle options, int userId);
}

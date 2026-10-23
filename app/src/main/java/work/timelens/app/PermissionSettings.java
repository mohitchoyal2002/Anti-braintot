package work.timelens.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** Public Settings contracts only. OEMs may omit a detail screen or ignore its package URI. */
final class PermissionSettings {
    enum Target { USAGE, OVERLAY, NOTIFICATIONS, NOTIFICATION_CHANNEL, APP_INFO }

    static Intent[] intents(Context context, Target target) {
        String pkg = context.getPackageName();
        Uri packageUri = Uri.parse("package:" + pkg);
        Intent appInfo = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
        Intent settings = new Intent(Settings.ACTION_SETTINGS);
        Intent notifications = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
        switch (target) {
            case USAGE:
                return new Intent[]{new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri),
                        new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), appInfo, settings};
            case OVERLAY:
                return new Intent[]{new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), appInfo, settings};
            case NOTIFICATION_CHANNEL:
                return new Intent[]{new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, "timer"), notifications, appInfo, settings};
            case NOTIFICATIONS:
                return new Intent[]{notifications, appInfo, settings};
            case APP_INFO:
                return new Intent[]{appInfo, new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), settings};
            default:
                throw new IllegalArgumentException("Unknown Settings target: " + target);
        }
    }

    static boolean open(Activity activity, Intent[] candidates) {
        // Don't gate this on resolveActivity(): package visibility can hide a valid Settings handler.
        for (Intent intent : candidates) {
            try {
                activity.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                // Some manufacturers don't provide the package-specific screen.
            }
        }
        return false;
    }

    static Target restore(String name) {
        if (name == null) return null;
        try { return Target.valueOf(name); }
        catch (IllegalArgumentException unknown) { return null; }
    }

    private PermissionSettings() {}
}

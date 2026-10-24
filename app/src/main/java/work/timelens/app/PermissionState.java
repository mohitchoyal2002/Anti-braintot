package work.timelens.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** Reads actual Android grants. A settings visit never counts as permission. */
final class PermissionState {
    enum Step { USAGE, OVERLAY, NOTIFICATIONS, READY }

    final boolean usage, overlay, notifications, notificationChannelBlocked;

    private PermissionState(boolean usage, boolean overlay, boolean notifications, boolean channelBlocked) {
        this.usage = usage;
        this.overlay = overlay;
        this.notifications = notifications;
        this.notificationChannelBlocked = channelBlocked;
    }

    static PermissionState read(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel("timer");
        boolean notificationGrant = Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        boolean channelBlocked = channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE;
        boolean notifications = notificationGrant && manager.areNotificationsEnabled() && !channelBlocked;
        return new PermissionState(UsageRepository.get(context).hasUsageAccess(),
                Settings.canDrawOverlays(context), notifications, channelBlocked);
    }

    boolean canTrack() { return usage && overlay; }
    boolean complete() { return canTrack() && notifications; }

    Step next(boolean skipNotifications) {
        if (!usage) return Step.USAGE;
        if (!overlay) return Step.OVERLAY;
        if (!notifications && !skipNotifications) return Step.NOTIFICATIONS;
        return Step.READY;
    }

    int requiredGrantedCount() { return (usage ? 1 : 0) + (overlay ? 1 : 0); }

    int grantMask() { return (usage ? 1 : 0) | (overlay ? 2 : 0) | (notifications ? 4 : 0) | (notificationChannelBlocked ? 8 : 0); }

    String requiredStatus() {
        if (!usage && !overlay) return "Still needed: Usage access and Display over apps.";
        if (!usage) return "Still needed: Usage access.";
        if (!overlay) return "Still needed: Display over apps.";
        return "Required access is ready. Start the timer when you want.";
    }

    boolean sameAs(PermissionState other) {
        return other != null && usage == other.usage && overlay == other.overlay
                && notifications == other.notifications
                && notificationChannelBlocked == other.notificationChannelBlocked;
    }
}

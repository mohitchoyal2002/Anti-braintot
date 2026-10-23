package work.timelens.app;

import android.app.*;
import android.content.*;

/** A user tap resumes tracking after reboot; never starts a background FGS. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || !Ui.prefs(c).getBoolean("enabled",false))return;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("resume","Resume after restart",NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open=PendingIntent.getActivity(c,2,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{nm.notify(2,new Notification.Builder(c,"resume").setSmallIcon(R.drawable.ic_timer).setContentTitle("Resume your TimeLens timer").setContentText("Phone restarted. Tap to open TimeLens and start the overlay.").setContentIntent(open).setAutoCancel(true).build());}catch(SecurityException ignored){}
    }
}

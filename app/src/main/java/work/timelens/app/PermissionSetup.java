package work.timelens.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A scrollable, recoverable explanation before Android's own permission screens. */
final class PermissionSetup {
    interface Actions {
        void usage();
        void overlay();
        void notifications();
        void appInfo();
        void recovery();
        void recheck();
        void skipNotifications();
        void dashboard();
        void start();
    }

    static void build(Context c, LinearLayout page, PermissionState state,
                      boolean recovery, boolean skipNotifications, String feedback, Actions actions) {
        PermissionState.Step step = state.next(skipNotifications);
        boolean ready = step == PermissionState.Step.READY;
        page.addView(Ui.text(c, ready ? "READY WHEN YOU ARE" : "SET UP TIMELENS", 12, Ui.MUTED, true));
        Ui.gap(page, 12);
        page.addView(Ui.text(c, ready ? "A little awareness.\nMore choice." : "Make time visible.", 32, Ui.INK, true));
        Ui.gap(page, 12);
        page.addView(Ui.text(c, "Screen time, at a glance. Your data stays on this phone.", 17, Ui.MUTED, false));
        Ui.gap(page, 24);

        page.addView(Ui.text(c, state.requiredGrantedCount() + " of 2 required permissions allowed", 14, Ui.MUTED, true));
        Ui.gap(page, 8);
        page.addView(Ui.text(c, state.requiredStatus(), 15, Ui.MUTED, false));
        Ui.gap(page, 12);

        LinearLayout detail = Ui.card(c);
        if (feedback != null) {
            TextView message = Ui.text(c, feedback, 15, Ui.INK, false);
            message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            detail.addView(message);
            Ui.gap(detail, 18);
        }
        switch (step) {
            case USAGE:
                if (recovery) {
                    title(c, detail, "Is the switch blocked?", "If Android says “App was denied access” or “Controlled by restricted setting”, confirm restricted settings on your phone.");
                    detail.addView(Ui.text(c, "App info → ⋮ → Allow restricted settings", 16, Ui.INK, true));
                    Ui.gap(detail, 10);
                    detail.addView(Ui.text(c, "Follow Android’s confirmation, then return here and open Usage access again. Allowing restricted settings does not turn on Usage access.", 17, Ui.MUTED, false));
                    Ui.gap(detail, 18);
                    detail.addView(Ui.button(c, "Open App info", true, actions::appInfo));
                    Ui.gap(detail, 10);
                    detail.addView(Ui.button(c, "Open usage access", false, actions::usage));
                    Ui.gap(detail, 16);
                    detail.addView(Ui.text(c, "Only allow apps you trust. If the menu is missing, your phone’s security settings or administrator may control access. TimeLens cannot change that setting.", 14, Ui.MUTED, false));
                } else {
                    title(c, detail, "Know where your day goes", "Usage access lets TimeLens read which app was active and for how long. It does not read messages or record your screen.");
                    detail.addView(Ui.text(c, "On the next screen, select TimeLens if shown, then turn on Allow usage access.", 17, Ui.MUTED, false));
                    Ui.gap(detail, 18);
                    detail.addView(Ui.button(c, "Open usage access", true, actions::usage));
                    Ui.gap(detail, 10);
                    detail.addView(Ui.button(c, "Usage switch disabled?", false, actions::recovery));
                }
                Ui.gap(detail, 14);
                detail.addView(Ui.text(c, "If Android opens a list, select TimeLens. You can also find it in Settings → Apps → Special app access → Usage access (or Usage data access).", 14, Ui.MUTED, false));
                break;
            case OVERLAY:
                title(c, detail, "Keep the total in sight", "Display over other apps lets a small clock and today’s total stay in view. Tap the timer when you want your current app’s time.");
                detail.addView(Ui.text(c, "On the next screen, choose TimeLens and turn on Allow display over other apps. You can stop the timer at any time.", 17, Ui.MUTED, false));
                Ui.gap(detail, 18);
                detail.addView(Ui.button(c, "Open display settings", true, actions::overlay));
                Ui.gap(detail, 14);
                detail.addView(Ui.text(c, "If the app page does not open, use Settings → Apps → Special app access → Display over other apps → TimeLens.", 14, Ui.MUTED, false));
                break;
            case NOTIFICATIONS:
                title(c, detail, "A quick way to stop", "Allow the timer notification to see that TimeLens is running and stop it from your notification shade.");
                detail.addView(Ui.text(c, "Recommended, but optional. You can also stop the timer from Today.", 17, Ui.MUTED, false));
                Ui.gap(detail, 18);
                detail.addView(Ui.button(c, "Set up notifications", true, actions::notifications));
                Ui.gap(detail, 10);
                detail.addView(Ui.button(c, "Continue without notifications", false, actions::skipNotifications));
                break;
            case READY:
                title(c, detail, "Your timer is ready", "Start when you want. A small clock shows today’s total. Drag to move it; tap to see the current app’s time.");
                if (!state.notifications) {
                    detail.addView(Ui.text(c, "Notifications are off. Use Today to stop the timer.", 17, Ui.MUTED, false));
                    Ui.gap(detail, 18);
                }
                detail.addView(Ui.button(c, "Start floating timer", true, actions::start));
                break;
        }
        Ui.gap(detail, 10);
        detail.addView(Ui.button(c, "Check permissions again", false, actions::recheck));
        page.addView(detail);
        Ui.gap(page, 14);
        page.addView(Ui.button(c, ready ? "View dashboard" : "Not now · view dashboard", false, actions::dashboard));
        Ui.gap(page, 24);
        LinearLayout checklist = Ui.card(c);
        checklist.addView(Ui.text(c, "Your access", 18, Ui.INK, true));
        Ui.gap(checklist, 8);
        permissionRow(c, checklist, "1", "Usage access", state.usage, false);
        permissionRow(c, checklist, "2", "Display over apps", state.overlay, false);
        permissionRow(c, checklist, "3", "Timer notifications", state.notifications, true);
        page.addView(checklist);
        Ui.gap(page, 18);
        LinearLayout privacy = Ui.row(c);
        ImageView icon = new ImageView(c);
        icon.setImageResource(R.drawable.ic_shield);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.MUTED));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        privacy.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)));
        TextView note = Ui.text(c, "No account. No ads. No internet access.", 13, Ui.MUTED, false);
        note.setPadding(Ui.dp(c, 10), 0, 0, 0);
        privacy.addView(note, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(privacy);
    }

    private static void title(Context c, LinearLayout parent, String title, String description) {
        parent.addView(Ui.text(c, title, 22, Ui.INK, true));
        Ui.gap(parent, 10);
        parent.addView(Ui.text(c, description, 17, Ui.MUTED, false));
        Ui.gap(parent, 18);
    }

    private static void permissionRow(Context c, LinearLayout parent, String number,
                                      String label, boolean allowed, boolean optional) {
        LinearLayout row = Ui.row(c);
        row.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
        TextView badge = Ui.text(c, allowed ? "✓" : number, 16, Ui.ACCENT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.rounded(Ui.TINT, 18, c));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(badge, new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        LinearLayout words = Ui.column(c);
        words.setPadding(Ui.dp(c, 14), 0, 0, 0);
        words.addView(Ui.text(c, label, 16, Ui.INK, true));
        Ui.gap(words, 4);
        words.addView(Ui.text(c, allowed ? "Allowed" : optional ? "Optional · not allowed" : "Required · not allowed", 13, Ui.MUTED, false));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(row);
    }

    private PermissionSetup() {}
}

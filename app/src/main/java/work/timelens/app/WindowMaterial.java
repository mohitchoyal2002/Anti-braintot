package work.timelens.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.PowerManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import java.util.function.Consumer;

/** Native, bounded background blur. The fallback is opaque, never clear text on an app. */
final class WindowMaterial {
    private final Context context;
    private final Window window;
    private final WindowManager manager;
    private final GradientDrawable background;
    private final boolean modal;
    private final Consumer<Boolean> blurListener=enabled->refresh();
    private boolean listening;

    static boolean allowed(Context c){
        if(Build.VERSION.SDK_INT<31||Ui.prefs(c).getBoolean("reduce_transparency",false))return false;
        PowerManager power=c.getSystemService(PowerManager.class);
        if(power!=null&&power.isPowerSaveMode())return false;
        android.app.UiModeManager mode=c.getSystemService(android.app.UiModeManager.class);
        return Build.VERSION.SDK_INT<34||mode==null||mode.getContrast()<0.5f;
    }
    WindowMaterial(Context c,Window w,boolean modal){
        context=c;window=w;this.modal=modal;manager=c.getSystemService(WindowManager.class);
        background=Ui.rounded(Ui.CARD,24,c);background.setStroke(Ui.dp(c,1),Ui.LINE);
        window.setBackgroundDrawable(background);
        window.setElevation(0);
        window.getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View view){
                if(Build.VERSION.SDK_INT>=31&&!listening){listening=true;manager.addCrossWindowBlurEnabledListener(context.getMainExecutor(),blurListener);}
                refresh();
            }
            public void onViewDetachedFromWindow(View view){
                if(Build.VERSION.SDK_INT>=31&&listening){manager.removeCrossWindowBlurEnabledListener(blurListener);listening=false;}
            }
        });
        refresh();
    }
    void refresh(){
        boolean glass=allowed(context)&&manager.isCrossWindowBlurEnabled();
        background.setColor(Ui.CARD);background.setAlpha(glass?235:255);
        if(Build.VERSION.SDK_INT>=31){
            window.setBackgroundBlurRadius(glass?80:0);
            WindowManager.LayoutParams attributes=window.getAttributes();
            attributes.setBlurBehindRadius(modal&&glass?20:0);
            if(modal)attributes.flags|=WindowManager.LayoutParams.FLAG_BLUR_BEHIND|WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            else attributes.flags&=~(WindowManager.LayoutParams.FLAG_BLUR_BEHIND|WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            attributes.dimAmount=modal?(glass?0.12f:0.28f):0;
            window.setAttributes(attributes);
        }
        if(modal)window.setWindowAnimations(Ui.animationsEnabled()?R.style.SheetMotion:0);
    }
}

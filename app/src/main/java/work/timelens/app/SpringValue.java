package work.timelens.app;

import android.animation.ValueAnimator;
import android.view.Choreographer;

/** Small, interruptible, critically damped spring driven by display frames. */
final class SpringValue implements Choreographer.FrameCallback {
    interface Update { void set(float value); }
    private final Update update;
    private final Runnable settled;
    private double value,velocity,target;
    private long lastFrame;
    private boolean running;
    private final double tolerance;
    SpringValue(float initial,double tolerance,Update update,Runnable settled) {
        value=target=initial;this.tolerance=tolerance;this.update=update;this.settled=settled;
    }
    void stop() {running=false;lastFrame=0;Choreographer.getInstance().removeFrameCallback(this);}
    void jump(float position) {stop();value=target=position;velocity=0;update.set(position);}
    void target(float next) {target=next;begin();}
    void release(float position,float next,float releaseVelocity) {stop();value=position;target=next;velocity=releaseVelocity;begin();}
    private void begin() {
        if(!Ui.animationsEnabled()){jump((float)target);if(settled!=null)settled.run();return;}
        if(!running){running=true;lastFrame=0;Choreographer.getInstance().postFrameCallback(this);}
    }
    @Override public void doFrame(long at) {
        if(!running)return;
        double elapsed=lastFrame==0?1.0/60:Math.min(0.032,(at-lastFrame)/1_000_000_000.0);lastFrame=at;
        // Independent scalar springs retain velocity across target changes.
        double omega=2*Math.PI/0.4,step=elapsed/4;
        for(int i=0;i<4;i++){double acceleration=omega*omega*(target-value)-2*omega*velocity;velocity+=acceleration*step;value+=velocity*step;}
        if(Math.abs(target-value)<tolerance&&Math.abs(velocity)<tolerance*8){value=target;velocity=0;update.set((float)value);stop();if(settled!=null)settled.run();}
        else {update.set((float)value);Choreographer.getInstance().postFrameCallback(this);}
    }
}

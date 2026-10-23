package work.timelens.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

/** Blurs a small copy of our own scrolling content; labels and icons remain sharp. */
final class FrostedBar extends FrameLayout {
    private final View source,backdrop,fill;
    private Bitmap sample;
    private boolean captureSupported=true;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    FrostedBar(Context c,View source,View controls){
        super(c);this.source=source;
        setBackground(Ui.rounded(0x00ffffff,28,c));setClipToOutline(true);
        backdrop=new View(c){@Override protected void onDraw(Canvas canvas){if(sample!=null)canvas.drawBitmap(sample,null,new android.graphics.Rect(0,0,getWidth(),getHeight()),paint);}};
        if(Build.VERSION.SDK_INT>=31)backdrop.setRenderEffect(RenderEffect.createBlurEffect(Ui.dp(c,16),Ui.dp(c,16),Shader.TileMode.CLAMP));
        backdrop.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(backdrop,new LayoutParams(-1,-1));
        fill=new View(c);fill.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(fill,new LayoutParams(-1,-1));
        addView(controls,new LayoutParams(-1,-2));
        addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->refresh());
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        View controls=getChildAt(getChildCount()-1);
        measureChild(controls,widthSpec,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(controls.getMeasuredHeight(),MeasureSpec.EXACTLY));
    }
    void refresh(){
        boolean glass=captureSupported&&WindowMaterial.allowed(getContext());
        fill.setBackground(Ui.rounded(glass?(Ui.CARD&0x00ffffff)|0xf0000000:Ui.CARD,28,getContext()));
        backdrop.setVisibility(glass?VISIBLE:GONE);
        if(!glass||getWidth()==0||getHeight()==0)return;
        int width=Math.max(1,getWidth()/2),height=Math.max(1,getHeight()/2);
        if(sample==null||sample.getWidth()!=width||sample.getHeight()!=height){
            if(sample!=null)sample.recycle();sample=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        }
        try{
            Canvas canvas=new Canvas(sample);canvas.drawColor(Ui.BG);canvas.scale(0.5f,0.5f);
            canvas.translate(source.getLeft()-getLeft(),source.getTop()-getTop());source.draw(canvas);backdrop.invalidate();
        }catch(RuntimeException unsupported){captureSupported=false;backdrop.setVisibility(GONE);fill.setBackground(Ui.rounded(Ui.CARD,28,getContext()));}
    }
    @Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();if(sample!=null){sample.recycle();sample=null;}}
}

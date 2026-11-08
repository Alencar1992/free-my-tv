package io.freemytv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/** Small independent layer: one update per second without repainting video cards or the wallpaper. */
final class LaunchCountdownView extends View {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Paint panel=new Paint(Paint.ANTI_ALIAS_FLAG),outline=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number,label,slash,message;
    private final RectF box=new RectF(1.5f,1.5f,558.5f,90.5f);
    private final int[] inks={0xFFFF68BF,0xFFC9A0FF,0xFFFFD6A3,0xFFFFFFFF};
    private static final String[] UNITS={"DIAS","HORAS","MIN","SEG"};
    private LaunchCountdown.State state;
    private String[] values={"00","00","00","00"};
    private boolean running;
    private float homeAlpha=1;
    private final Runnable tick=()->update();

    LaunchCountdownView(Context context,Ui.Fonts fonts){
        super(context);
        setFocusable(false);setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(LAYER_TYPE_HARDWARE,null);
        panel.setColor(0xD921192F);
        outline.setStyle(Paint.Style.STROKE);outline.setStrokeWidth(1.5f);
        outline.setShader(new LinearGradient(0,0,560,0,new int[]{inks[0],inks[1],inks[2]},null,Shader.TileMode.CLAMP));
        number=fonts.numbers(fonts.displaySemibold,42,inks[0]);number.setTextAlign(Paint.Align.CENTER);
        label=Ui.text(fonts.mono,15,0xFFF1DFF0);label.setTextAlign(Paint.Align.CENTER);
        slash=Ui.text(fonts.displaySemibold,30,0xFF8E739D);slash.setTextAlign(Paint.Align.CENTER);
        message=Ui.text(fonts.mono,21,0xFFFFD6A3);message.setTextAlign(Paint.Align.CENTER);
        setVisibility(INVISIBLE);
    }
    void resume(){if(running)return;running=true;update();}
    void pause(){running=false;handler.removeCallbacks(tick);}
    void homeTransform(float alpha,float translationY){
        homeAlpha=alpha;setAlpha(alpha);setTranslationY(translationY);visibility();
    }
    private void update(){
        handler.removeCallbacks(tick);
        if(!running)return;
        long now=System.currentTimeMillis();
        state=LaunchCountdown.at(now);
        visibility();
        if(state.phase==LaunchCountdown.Phase.HIDDEN){
            // A TV correcting an incorrect future clock can restore the badge without leaving Home.
            // This low-frequency check is cancelled by pause(), and draws nothing after expiry.
            handler.postDelayed(tick,60_000);return;
        }
        values=state.digits().split("/");invalidate();
        handler.postDelayed(tick,1000-Math.floorMod(now,1000));
    }
    private void visibility(){setVisibility(state!=null&&state.phase!=LaunchCountdown.Phase.HIDDEN&&homeAlpha>0.002f?VISIBLE:INVISIBLE);}
    @Override protected void onDetachedFromWindow(){pause();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        if(state==null||state.phase==LaunchCountdown.Phase.HIDDEN)return;
        int save=canvas.save();canvas.scale(getWidth()/560f,getHeight()/92f);
        canvas.drawRoundRect(box,14,14,panel);canvas.drawRoundRect(box,14,14,outline);
        for(int i=0;i<4;i++){
            float center=85+130*i;
            number.setColor(inks[i]);canvas.drawText(values[i],center,56,number);
            if(i<3)canvas.drawText("/",150+130*i,53,slash);
            if(state.phase==LaunchCountdown.Phase.COUNTING)canvas.drawText(UNITS[i],center,79,label);
        }
        if(state.phase==LaunchCountdown.Phase.LAUNCH_DAY)canvas.drawText("HOJE É O GRANDE DIA!",280,81,message);
        canvas.restoreToCount(save);
    }
}

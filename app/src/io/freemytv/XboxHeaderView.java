package io.freemytv;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import java.util.Collections;

/** Cached Xbox summary; the root home owns DPAD focus and opens the full native picker. */
final class XboxHeaderView extends View {
 private final XboxSource source;private final XboxArt art;
 private final Paint panel=new Paint(Paint.ANTI_ALIAS_FLAG),rim=new Paint(Paint.ANTI_ALIAS_FLAG),image=new Paint(Paint.FILTER_BITMAP_FLAG);
 private final Paint name,detail,hint,emoji;
 private final RectF box=new RectF(2,2,648,116),cover=new RectF(12,10,82,108);
 private final Path clip=new Path();
 private XboxSource.Snapshot snapshot;private boolean running,focused;private float homeAlpha=1;
 private final Runnable changed=this::update,artChanged=this::invalidate;
 XboxHeaderView(Context context,Ui.Fonts fonts){
  super(context);source=XboxSource.get(context);art=XboxArt.get(context);
  setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setLayerType(LAYER_TYPE_HARDWARE,null);
  panel.setColor(0xE021192F);rim.setStyle(Paint.Style.STROKE);
  rim.setShader(new LinearGradient(0,0,650,0,new int[]{0xFFFF68BF,0xFFC9A0FF,0xFFFFD6A3},null,Shader.TileMode.CLAMP));
  name=Ui.text(fonts.displaySemibold,25,0xFFFFFFFF);detail=fonts.numbers(fonts.medium,21,0xFFF1DFF0);
  hint=Ui.text(fonts.mono,15,0xFFD7BEDB);emoji=Ui.text(Typeface.DEFAULT,22,Color.WHITE);
  update();
 }
 void resume(){if(running)return;running=true;source.addListener(changed);art.addListener(artChanged);update();source.refresh(false);}
 void pause(){running=false;source.removeListener(changed);art.removeListener(artChanged);art.prefetch(Collections.emptyList());}
 void refreshIfStale(){if(running)source.refresh(false);}
 void homeTransform(float alpha,float dy){homeAlpha=alpha;setAlpha(alpha);setTranslationY(dy);setVisibility(alpha>.002f?VISIBLE:INVISIBLE);}
 void panelFocus(boolean value){if(focused!=value){focused=value;invalidate();}}
 private void update(){snapshot=source.snapshot();XboxData.Game selected=snapshot.selected();if(running)art.prefetch(selected==null?Collections.emptyList():Collections.singletonList(selected));invalidate();}
 String description(){
  XboxData.Game game=snapshot==null?null:snapshot.selected();XboxData.Summary summary=snapshot==null?null:snapshot.summary;
  return game==null?"Xbox, conectar e escolher jogo":"Xbox, "+game.name+(summary==null?"":", conquistas "+count(summary.unlocked)+" de "+count(summary.total)+", tempo "+summary.time())+", OK para escolher outro jogo";
 }
 private static String count(int value){return value<0?"—":Integer.toString(value);}
 private void text(Canvas c,String value,float x,float y,float width,Paint paint){String s=TextUtils.ellipsize(value,new TextPaint(paint),width,TextUtils.TruncateAt.END).toString();c.drawText(s,x,y,paint);}
 @Override protected void onDetachedFromWindow(){pause();super.onDetachedFromWindow();}
 @Override protected void onDraw(Canvas c){
  if(snapshot==null||homeAlpha<=.002f)return;int save=c.save();c.scale(getWidth()/650f,getHeight()/118f);
  c.drawRoundRect(box,14,14,panel);rim.setStrokeWidth(focused?3.5f:1.3f);rim.setAlpha(focused?255:130);c.drawRoundRect(box,14,14,rim);
  XboxData.Game game=snapshot.selected();Bitmap bitmap=game==null?null:art.get(game);
  clip.reset();clip.addRoundRect(cover,9,9,Path.Direction.CW);c.save();c.clipPath(clip);
  if(bitmap!=null&&!bitmap.isRecycled()){
   float[] fitted=ImageSizing.cover(bitmap.getWidth(),bitmap.getHeight(),Math.round(cover.width()),Math.round(cover.height()));
   RectF target=new RectF(cover.left+fitted[0],cover.top+fitted[1],cover.left+fitted[2],cover.top+fitted[3]);c.drawBitmap(bitmap,null,target,image);
  }else{c.drawColor(0xFF423452);c.drawText("🎮",cover.left+18,cover.centerY()+8,emoji);}
  c.restore();
  text(c,game==null?"Xbox · escolher jogo":game.name,98,30,534,name);
  XboxData.Summary s=snapshot.summary;
  if(game==null){text(c,snapshot.loading?"Conectando…":snapshot.configured?"Atualize seu histórico de jogos":"Conecte sua conta OpenXBL",98,61,530,detail);}
  else{
   c.drawText("🏆",98,61,emoji);text(c,s==null?"— / —":count(s.unlocked)+" / "+count(s.total),130,61,172,detail);
   c.drawText("⏱️",322,61,emoji);text(c,s==null?"—":s.time(),356,61,275,detail);
   c.drawText("📊",98,86,emoji);text(c,s==null?"Progresso indisponível":s.progress()+" das conquistas",130,86,490,detail);
  }
  String status=snapshot.loading?"🔄 Sincronizando…":snapshot.stale?"Dados salvos · OK para abrir":"↑ Foco · OK: buscar e escolher jogo";
  if(game==null&&!snapshot.message.isEmpty())status=snapshot.message;
  text(c,status,98,108,532,hint);c.restoreToCount(save);
 }
}

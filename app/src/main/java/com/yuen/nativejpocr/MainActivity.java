package com.yuen.nativejpocr;

import android.Manifest;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.RemoteViews;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {

    private boolean pendingProjectionSetup=false;

    static final Map<String,String[]> WORDS = new LinkedHashMap<>();
    static {
        WORDS.put("守る", new String[]{"まもる","mamoru","vt","守护；遵守；保护"});
        WORDS.put("守り", new String[]{"まもり","mamori","n","守护；保护；防守"});
        WORDS.put("漏る", new String[]{"もる","moru","vi","漏出；渗漏；从缝隙滴下"});
        WORDS.put("漏れる", new String[]{"もれる","moreru","vi","漏；泄漏；遗漏"});
        WORDS.put("曇る", new String[]{"くもる","kumoru","vi","阴天；变模糊；起雾"});
        WORDS.put("見る", new String[]{"みる","miru","vt","看；观看；观察"});
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=base();
        TextView title=title("日语悬浮搜索");
        root.addView(title);

        TextView buildInfo=text("当前版本：" + BuildConfig.VERSION_NAME
                + "  ·  versionCode " + BuildConfig.VERSION_CODE);
        buildInfo.setTextSize(14);
        buildInfo.setTextColor(0xff6f7780);
        root.addView(buildInfo);
        root.addView(button("🟠 开启浮动取词（悬浮 + OCR）", v->enableFloatingBubble()));
        root.addView(button("⛔ 关闭桌面悬浮球", v->stopService(new Intent(this,FloatingService.class))));
        root.addView(button("🔍 打开悬浮搜索页", v->startActivity(new Intent(this,SearchOverlayActivity.class))));
        root.addView(button("📷 屏幕 OCR", v->startActivity(new Intent(this,OcrActivity.class))));
        TextView note=text("这是自己的浮动取词功能，不调用欧路。\n"
                +"轻点浮动按钮：打开深色悬浮搜索。\n"
                +"按住拖动：如果尚未授权屏幕共享，会立即呼出系统授权；授权后再次拖动即可区域 OCR 取词。\n"
                +"屏幕共享只请求“共享整个屏幕”。");
        root.addView(note);
        setContentView(root);
        if(Settings.canDrawOverlays(this)) startFloatingBubble();
    }

    void enableFloatingBubble(){
        if(Settings.canDrawOverlays(this)){
            startFloatingBubble();
            Toast.makeText(this,
                    ScreenCaptureService.READY
                            ? "浮动取词已开启：拖动取词，轻点搜索"
                            : "浮动取词已开启：首次拖动时会直接申请整屏共享权限",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        pendingProjectionSetup=true;
        Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()));
        startActivity(i);
        Toast.makeText(this,"请允许“显示在其他应用上层”；返回后即可拖动浮标取词",Toast.LENGTH_LONG).show();
    }

    void startFloatingBubble(){
        Intent i=new Intent(this,FloatingService.class);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
    }

    @Override protected void onResume(){
        super.onResume();
        if(Settings.canDrawOverlays(this)){
            startFloatingBubble();
            if(pendingProjectionSetup){
                pendingProjectionSetup=false;
                Toast.makeText(this,
                        "悬浮球已开启；首次拖动时会直接申请整屏共享权限",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    static LinearLayout base(){
        LinearLayout l=new LinearLayout(App.get());
        l.setOrientation(LinearLayout.VERTICAL);
        int p=dp(20);
        l.setPadding(p,p,p,p);
        l.setBackgroundColor(0xfff7f7f7);
        return l;
    }
    static TextView title(String s){ TextView v=text(s); v.setTextSize(28); v.setPadding(0,0,0,dp(18)); return v; }
    static TextView text(String s){ TextView v=new TextView(App.get()); v.setText(s); v.setTextColor(0xff222222); v.setTextSize(17); v.setPadding(0,dp(8),0,dp(8)); return v; }
    static Button button(String s, View.OnClickListener l){ Button b=new Button(App.get()); b.setText(s); b.setAllCaps(false); b.setOnClickListener(l); return b; }
    static int dp(int v){ return Math.round(v*App.get().getResources().getDisplayMetrics().density); }

    public static class SearchActivity extends Activity {
        @Override public void onCreate(Bundle b){
            super.onCreate(b);
            route(getIntent());
        }

        @Override protected void onNewIntent(Intent i){
            super.onNewIntent(i);
            setIntent(i);
            route(i);
        }

        void route(Intent i){
            String q=readIncoming(i);
            Intent next;
            if(q!=null && !q.trim().isEmpty()){
                next=new Intent(this,QuickLookupActivity.class).putExtra("query",q.trim());
            }else{
                next=new Intent(this,SearchOverlayActivity.class);
            }
            startActivity(next);
            finish();
        }

        String readIncoming(Intent i){
            if(i==null)return null;
            if(Intent.ACTION_PROCESS_TEXT.equals(i.getAction())){
                CharSequence p=i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
                return p==null?null:p.toString();
            }
            if(Intent.ACTION_SEND.equals(i.getAction())) return i.getStringExtra(Intent.EXTRA_TEXT);
            if(Intent.ACTION_SEARCH.equals(i.getAction())) return i.getStringExtra("query");
            return i.getStringExtra("query");
        }
    }

    public static class OcrActivity extends Activity {
        static final int REQ_CAPTURE=7001, REQ_NOTIFY=7002;
        @Override public void onCreate(Bundle b){ super.onCreate(b); ask(); }
        void ask(){
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFY); return;
            }
            MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent captureIntent;
            if(Build.VERSION.SDK_INT>=34){
                MediaProjectionConfig config=MediaProjectionConfig.createConfigForDefaultDisplay();
                captureIntent=m.createScreenCaptureIntent(config);
            }else{
                captureIntent=m.createScreenCaptureIntent();
            }
            startActivityForResult(captureIntent,REQ_CAPTURE);
        }
        @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
            super.onRequestPermissionsResult(r,p,g);
            if(r==REQ_NOTIFY){
                if(g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) ask();
                else { Toast.makeText(this,"需要通知权限，才能在通知栏显示“识别当前屏幕”按钮",Toast.LENGTH_LONG).show(); finish(); }
            }
        }
        @Override protected void onActivityResult(int r,int c,Intent data){
            super.onActivityResult(r,c,data);
            if(r==REQ_CAPTURE && c==RESULT_OK && data!=null){
                Intent s=new Intent(this,ScreenCaptureService.class).putExtra("code",c).putExtra("data",data);
                if(Build.VERSION.SDK_INT>=26) startForegroundService(s); else startService(s);
                Toast.makeText(this,"录屏 OCR 已启动；可从通知栏点“识别当前屏幕”",Toast.LENGTH_LONG).show();
            }
            finish();
        }
    }

    public static class ScreenCaptureService extends Service {
        static final String CH="ocr_capture", ACT_OCR="ocr_now", ACT_STOP="ocr_stop";
        public static final String ACT_OCR_REGION="ocr_region";
        public static final String ACTION_RESULT="com.yuen.nativejpocr.OCR_RESULT";
        public static volatile boolean READY=false;
        MediaProjection projection; ImageReader reader; VirtualDisplay vd; int w,h,dpi;
        @Override public void onCreate(){ super.onCreate(); ensureChannel(); }
        @Override public int onStartCommand(Intent in,int flags,int id){
            if(in==null)return START_NOT_STICKY;
            String a=in.getAction();
            if(ACT_STOP.equals(a)){ stopCapture(); stopSelf(); return START_NOT_STICKY; }
            if(ACT_OCR.equals(a)){ captureOnce(null); return START_STICKY; }
            if(ACT_OCR_REGION.equals(a)){ captureOnce(in); return START_STICKY; }
            Intent data=(Intent)in.getParcelableExtra("data"); int code=in.getIntExtra("code",Activity.RESULT_CANCELED);
            if(data!=null){
                Notification n=notification("录屏 OCR 正在启动","准备屏幕捕获…");
                if(Build.VERSION.SDK_INT>=29) startForeground(42,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                else startForeground(42,n);
                startProjection(code,data);
            }
            return START_STICKY;
        }
        void startProjection(int code,Intent data){
            MediaProjectionManager mm=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=mm.getMediaProjection(code,data);
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){ stopCapture(); stopSelf(); }
            }, new Handler(Looper.getMainLooper()));
            DisplayMetrics dm=getResources().getDisplayMetrics(); dpi=dm.densityDpi;
            if(Build.VERSION.SDK_INT>=30){
                android.view.WindowMetrics m=((WindowManager)getSystemService(WINDOW_SERVICE)).getMaximumWindowMetrics();
                w=m.getBounds().width(); h=m.getBounds().height();
            } else { w=dm.widthPixels; h=dm.heightPixels; }
            reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
            vd=projection.createVirtualDisplay("jp-ocr",w,h,dpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,null);
            READY=true;
            Notification n=notification("录屏 OCR 已开启","可拖动悬浮球取词，或点通知识别整屏");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(42,n);
        }
        Notification notification(String title,String body){
            PendingIntent o=PendingIntent.getService(this,1,new Intent(this,ScreenCaptureService.class).setAction(ACT_OCR),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            PendingIntent s=PendingIntent.getService(this,2,new Intent(this,ScreenCaptureService.class).setAction(ACT_STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            return new Notification.Builder(this,CH).setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle(title).setContentText(body).setOngoing(true)
                    .addAction(new Notification.Action.Builder(null,"识别当前屏幕",o).build())
                    .addAction(new Notification.Action.Builder(null,"停止",s).build()).build();
        }
        void ensureChannel(){
            if(Build.VERSION.SDK_INT>=26) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CH,"屏幕 OCR",NotificationManager.IMPORTANCE_DEFAULT));
        }
        void captureOnce(Intent request){
            if(reader==null){
                if(request!=null) sendOcrResult(null,"屏幕捕获尚未就绪，请重新授权录屏");
                else Toast.makeText(this,"录屏会话尚未建立",Toast.LENGTH_SHORT).show();
                return;
            }
            final boolean region=request!=null && ACT_OCR_REGION.equals(request.getAction());
            final int rx=region?request.getIntExtra("x",0):0;
            final int ry=region?request.getIntExtra("y",0):0;
            final int rw=region?request.getIntExtra("w",w):w;
            final int rh=region?request.getIntExtra("h",h):h;

            new Handler(Looper.getMainLooper()).postDelayed(()->{
                Image image=reader.acquireLatestImage();
                if(image==null){
                    if(region) sendOcrResult(null,"暂时没有取得屏幕图像，请再拖动一次");
                    else Toast.makeText(this,"暂时未取得屏幕图像，请再点一次",Toast.LENGTH_SHORT).show();
                    return;
                }
                Bitmap bmp=null;
                try{
                    Image.Plane p=image.getPlanes()[0];
                    ByteBuffer buf=p.getBuffer();
                    int px=p.getPixelStride(), row=p.getRowStride(), pad=row-px*w;
                    Bitmap tmp=Bitmap.createBitmap(w+pad/px,h,Bitmap.Config.ARGB_8888);
                    tmp.copyPixelsFromBuffer(buf);
                    Bitmap full=Bitmap.createBitmap(tmp,0,0,w,h);
                    tmp.recycle();

                    if(region){
                        int left=Math.max(0,Math.min(rx,w-1));
                        int top=Math.max(0,Math.min(ry,h-1));
                        int cw=Math.max(1,Math.min(rw,w-left));
                        int ch=Math.max(1,Math.min(rh,h-top));
                        bmp=Bitmap.createBitmap(full,left,top,cw,ch);
                        full.recycle();
                    }else{
                        bmp=full;
                    }
                } catch(Exception e){
                    if(region) sendOcrResult(null,"截图裁剪失败："+e.getMessage());
                    return;
                } finally { image.close(); }

                recognize(bmp,region);
            },280);
        }

        void recognize(Bitmap bmp, boolean region){
            TextRecognizer r=TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
            r.process(InputImage.fromBitmap(bmp,0)).addOnSuccessListener(t->{
                String q=firstJapaneseToken(t.getText());
                if(q==null) q=t.getText().trim();
                if(q.length()>40) q=q.substring(0,40);

                if(region){
                    sendOcrResult(q,null);
                }else{
                    Intent i=new Intent(this,SearchActivity.class).putExtra("query",q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    try{ startActivity(i); }catch(Exception e){ postResult(q); }
                }
            }).addOnFailureListener(e->{
                if(region) sendOcrResult(null,"OCR 失败："+e.getMessage());
                else Toast.makeText(this,"OCR 失败："+e.getMessage(),Toast.LENGTH_LONG).show();
            }).addOnCompleteListener(x->{
                r.close();
                try{ bmp.recycle(); }catch(Exception ignored){}
            });
        }

        void sendOcrResult(String text,String error){
            Intent out=new Intent(ACTION_RESULT).setPackage(getPackageName());
            if(text!=null) out.putExtra("text",text);
            if(error!=null) out.putExtra("error",error);
            sendBroadcast(out);
        }
        void postResult(String q){
            Intent i=new Intent(this,SearchActivity.class).putExtra("query",q);
            PendingIntent p=PendingIntent.getActivity(this,3,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification n=new Notification.Builder(this,CH).setSmallIcon(android.R.drawable.ic_menu_search).setContentTitle("OCR 识别结果").setContentText(q).setContentIntent(p).setAutoCancel(true).build();
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(43,n);
        }
        static String firstJapaneseToken(String s){
            if(s==null)return null;
            Matcher m=Pattern.compile("[\\u3040-\\u30ff\\u3400-\\u9fff々〆ヵヶー]{1,24}").matcher(s);
            return m.find()?m.group():null;
        }
        void stopCapture(){
            VirtualDisplay v=vd; vd=null;
            ImageReader r=reader; reader=null;
            MediaProjection p=projection; projection=null;
            READY=false;
            if(v!=null) v.release();
            if(r!=null) r.close();
            if(p!=null) try{ p.stop(); }catch(Exception ignored){}
        }
        @Override public void onDestroy(){ stopCapture(); super.onDestroy(); }
        @Override public android.os.IBinder onBind(Intent i){ return null; }
    }

    public static class SearchWidgetProvider extends AppWidgetProvider {
        @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
            for(int id:ids){
                RemoteViews rv=new RemoteViews(c.getPackageName(),R.layout.widget_search);
                PendingIntent p=PendingIntent.getActivity(c,10,new Intent(c,SearchOverlayActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                rv.setOnClickPendingIntent(R.id.widget_root,p); m.updateAppWidget(id,rv);
            }
        }
    }

    public static final class App extends Application {
        static Context c;
        @Override public void onCreate(){ super.onCreate(); c=this; }
        static Context get(){
            if(c!=null)return c;
            throw new IllegalStateException("App context not initialized");
        }
    }
}

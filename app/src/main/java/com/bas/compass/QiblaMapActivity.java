package com.bas.compass;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/** An offline coordinate map of the great-circle to Mecca: never reads compass sensors. */
public final class QiblaMapActivity extends Activity {
    private static final double KLAT=21.4224779, KLON=39.8251832;
    private double lat,lon,bearing,distanceKm;
    private String place;
    private final int bg=Color.rgb(8,14,23),panel=Color.rgb(18,31,43);
    private final int gold=Color.rgb(235,197,123),ink=Color.rgb(238,242,248);
    private final int muted=Color.rgb(159,175,190);
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        lat=getIntent().getDoubleExtra("latitude",Double.NaN);
        lon=getIntent().getDoubleExtra("longitude",Double.NaN);
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180){
            finish();return;
        }
        place=getIntent().getStringExtra("place");
        if(place==null||place.trim().isEmpty())place="موقعي";
        bearing=bearingTo(lat,lon,KLAT,KLON);
        distanceKm=distance(lat,lon,KLAT,KLON);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(16),dp(18),dp(16),dp(24));
        scroll.addView(root);setContentView(scroll);
        root.addView(label("التحقق من القبلة بالخريطة",23,gold,true));
        root.addView(label("خريطة إحداثيات هندسية تعمل دون إنترنت أو بوصلة",13,muted,false));
        space(root,12);
        TextView angle=label(String.format(Locale.US,
            "زاوية القبلة من الشمال الحقيقي: %.1f°",bearing),20,gold,true);
        angle.setBackgroundColor(panel);
        root.addView(angle,new LinearLayout.LayoutParams(-1,dp(66)));
        space(root,12);
        root.addView(new CoordinateMap(),new LinearLayout.LayoutParams(-1,dp(375)));
        space(root,12);
        TextView details=label(String.format(Locale.US,
            "من %s: %.4f, %.4f\nإلى الكعبة: %.4f, %.4f\nالمسافة الجغرافية: %,.0f كم",
            place,lat,lon,KLAT,KLON,distanceKm),15,ink,false);
        details.setBackgroundColor(panel);
        root.addView(details);
        space(root,10);
        root.addView(label("الخط الذهبي هو أقصر مسار على سطح الأرض (الدائرة العظمى). "
            +"هذه خريطة هندسية وليست خريطة شوارع أو صور أقمار صناعية. "
            +"تحسب اتجاه القبلة جغرافياً، لكنها لا تستطيع التأكد من الاتجاه الذي يشير إليه هاتفك. "
            +"للتأكد ميدانياً، يلزم تحديد الشمال الحقيقي بوسيلة مستقلة.",14,muted,false));
        space(root,10);
        TextView back=label("العودة إلى البوصلة",17,gold,true);
        back.setBackgroundColor(panel);
        back.setOnClickListener(v->finish());
        root.addView(back,new LinearLayout.LayoutParams(-1,dp(52)));
    }
    private TextView label(String s,int size,int c,boolean bold){
        TextView v=new TextView(this);
        v.setGravity(Gravity.CENTER);v.setText(s);v.setTextColor(c);v.setTextSize(size);
        v.setPadding(dp(10),dp(9),dp(10),dp(9));
        if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT,1);
        return v;
    }
    private void space(LinearLayout root,int n){
        root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));
    }
    private static double radians(double n){return Math.toRadians(n);}
    private static double wrap(double n){return (n+540)%360-180;}
    private static double bearingTo(double a,double b,double c,double d){
        double la=radians(a),lc=radians(c),diff=radians(d-b);
        double y=Math.sin(diff)*Math.cos(lc);
        double x=Math.cos(la)*Math.sin(lc)-Math.sin(la)*Math.cos(lc)*Math.cos(diff);
        return (Math.toDegrees(Math.atan2(y,x))+360)%360;
    }
    private static double distance(double a,double b,double c,double d){
        double dy=radians(c-a),dx=radians(d-b);
        double h=Math.pow(Math.sin(dy/2),2)
            +Math.cos(radians(a))*Math.cos(radians(c))*Math.pow(Math.sin(dx/2),2);
        return 12742.0176*Math.asin(Math.min(1,Math.sqrt(h)));
    }
    private static double[] vector(double a,double b){
        double x=radians(a),y=radians(b);
        return new double[]{Math.cos(x)*Math.cos(y),Math.cos(x)*Math.sin(y),Math.sin(x)};
    }
    // Spherical linear interpolation ensures route is geodesic, not a road path.
    private double[] position(double t){
        double[] a=vector(lat,lon),b=vector(KLAT,KLON);
        double dot=Math.max(-1,Math.min(1,a[0]*b[0]+a[1]*b[1]+a[2]*b[2]));
        double omega=Math.acos(dot),den=Math.sin(omega);
        if(Math.abs(den)<1e-8)return new double[]{
            lat+(KLAT-lat)*t,lon+wrap(KLON-lon)*t};
        double w1=Math.sin((1-t)*omega)/den,w2=Math.sin(t*omega)/den;
        double x=a[0]*w1+b[0]*w2,y=a[1]*w1+b[1]*w2,z=a[2]*w1+b[2]*w2;
        double geoLon=Math.toDegrees(Math.atan2(y,x));
        return new double[]{Math.toDegrees(Math.atan2(z,Math.hypot(x,y))),
            lon+wrap(geoLon-lon)};
    }
    private final class CoordinateMap extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final double[][] route=new double[121][2];
        private double minLat=1e9,maxLat=-1e9,minLon=1e9,maxLon=-1e9;
        private float left,top,right,bottom;
        CoordinateMap(){
            super(QiblaMapActivity.this);
            for(int i=0;i<route.length;i++){
                double[] p=position(i/(double)(route.length-1));route[i]=p;
                minLat=Math.min(minLat,p[0]);maxLat=Math.max(maxLat,p[0]);
                minLon=Math.min(minLon,p[1]);maxLon=Math.max(maxLon,p[1]);
            }
            double padY=Math.max(0.4,(maxLat-minLat)*0.15);
            double padX=Math.max(0.4,(maxLon-minLon)*0.15);
            minLat-=padY;maxLat+=padY;minLon-=padX;maxLon+=padX;
        }
        private float x(double longitude){
            return left+(float)((longitude-minLon)/(maxLon-minLon))*(right-left);
        }
        private float y(double latitude){
            return bottom-(float)((latitude-minLat)/(maxLat-minLat))*(bottom-top);
        }
        private void paint(int color,float width,Paint.Style style){
            paint.setColor(color);paint.setStrokeWidth(width);paint.setStyle(style);
            paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        }
        private void caption(Canvas c,String text,float x,float y,int color,int size){
            paint(color,0,Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(size));
            c.drawText(text,x,y,paint);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            canvas.drawColor(panel);
            left=dp(42);top=dp(39);right=getWidth()-dp(21);bottom=getHeight()-dp(45);
            paint(Color.rgb(68,90,109),dp(1),Paint.Style.STROKE);
            canvas.drawRect(left,top,right,bottom,paint);
            for(int i=1;i<5;i++){
                float xx=left+(right-left)*i/5f,yy=top+(bottom-top)*i/5f;
                canvas.drawLine(xx,top,xx,bottom,paint);
                canvas.drawLine(left,yy,right,yy,paint);
            }
            caption(canvas,"↑ شمال",right-dp(32),top-dp(14),gold,13);
            caption(canvas,String.format(Locale.US,"%.1f° E",wrap(minLon)),
                left+dp(33),bottom+dp(24),muted,10);
            caption(canvas,String.format(Locale.US,"%.1f° E",wrap(maxLon)),
                right-dp(33),bottom+dp(24),muted,10);
            caption(canvas,String.format(Locale.US,"%.1f° N",maxLat),
                left+dp(20),top-dp(14),muted,10);
            Path line=new Path();line.moveTo(x(route[0][1]),y(route[0][0]));
            for(int i=1;i<route.length;i++)line.lineTo(x(route[i][1]),y(route[i][0]));
            paint(gold,dp(3),Paint.Style.STROKE);
            canvas.drawPath(line,paint);
            float x1=x(route[0][1]),y1=y(route[0][0]);
            float x2=x(route[120][1]),y2=y(route[120][0]);
            paint(Color.rgb(74,180,151),0,Paint.Style.FILL);
            canvas.drawCircle(x1,y1,dp(8),paint);
            paint(Color.rgb(255,246,216),0,Paint.Style.FILL);
            canvas.drawCircle(x2,y2,dp(8),paint);
            paint(bg,dp(2),Paint.Style.STROKE);
            canvas.drawCircle(x1,y1,dp(8),paint);
            canvas.drawCircle(x2,y2,dp(8),paint);
            caption(canvas,"الموقع",Math.max(left+dp(27),Math.min(right-dp(27),x1)),
                Math.max(top+dp(15),y1-dp(14)),ink,12);
            caption(canvas,"الكعبة",Math.max(left+dp(27),Math.min(right-dp(27),x2)),
                Math.min(bottom-dp(7),y2+dp(23)),gold,12);
        }
    }
}

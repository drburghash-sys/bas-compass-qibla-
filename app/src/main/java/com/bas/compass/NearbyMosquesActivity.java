package com.bas.compass;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

/**
 * Optional nearby mosque search powered by public OpenStreetMap/Overpass data.
 * Network access occurs only after explicitly tapping Search on this screen.
 * Distances are straight-line approximations, not walking/driving distances.
 */
public final class NearbyMosquesActivity extends Activity {
    private static final int[] RADII={1000,3000,5000,10000};
    private static final int MAX_DISPLAY=30;
    private static final String OVERPASS_URL="https://overpass-api.de/api/interpreter";
    private double latitude,longitude;
    private boolean manualLocation, searching, night;
    private String place;
    private int selectedRadius=1;
    private int bg,panel,textColor,muted,gold,stroke;
    private TextView searchButton, statusLabel;
    private ProgressBar progress;
    private LinearLayout results;
    private TextView[] radiusTabs=new TextView[RADII.length];

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        latitude=getIntent().getDoubleExtra("latitude",Double.NaN);
        longitude=getIntent().getDoubleExtra("longitude",Double.NaN);
        if(!valid(latitude,longitude)) {finish();return;}
        manualLocation=getIntent().getBooleanExtra("manual",false);
        place=getIntent().getStringExtra("place");
        if(place==null||place.trim().isEmpty())place="الموقع المحدد";
        SharedPreferences prefs=getSharedPreferences("bas_compass",MODE_PRIVATE);
        night=prefs.getBoolean("night",true);
        selectedRadius=prefs.getInt("mosqueRadius",1);
        if(selectedRadius<0||selectedRadius>=RADII.length)selectedRadius=1;
        bg=Color.parseColor(night?"#080E17":"#F0F3F8");
        panel=Color.parseColor(night?"#121F2B":"#FFFFFF");
        textColor=Color.parseColor(night?"#F5F1E8":"#172333");
        muted=Color.parseColor(night?"#9DAEBF":"#637588");
        gold=Color.parseColor(night?"#EBC57B":"#987024");
        stroke=Color.parseColor(night?"#304353":"#DFE5EC");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(
            night?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildUi();
    }

    private int dp(float amount) {
        return Math.round(amount*getResources().getDisplayMetrics().density);
    }
    private static boolean valid(double lat,double lon){
        return Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=-90&&lat<=90&&lon>=-180&&lon<=180;
    }
    private GradientDrawable shape(boolean active){
        GradientDrawable d=new GradientDrawable();
        d.setColor(panel);
        d.setCornerRadius(dp(17));
        d.setStroke(dp(1),active?gold:stroke);
        return d;
    }
    private LinearLayout column() {
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }
    private TextView label(String title,int size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(title);t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(11),dp(10),dp(11),dp(10));
        if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        return t;
    }
    private void gap(LinearLayout l,int n){
        l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));
    }
    private void buildUi() {
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(bg);
        LinearLayout root=column();
        root.setPadding(dp(17),dp(18),dp(17),dp(24));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(label("🕌  المساجد القريبة",23,gold,true));
        root.addView(label("ابحث حسب الموقع ثم افتح الاتجاهات إلى المسجد",13,muted,false));
        gap(root,10);
        TextView location=label((manualLocation?"⌖ البحث حول الموقع المختار: ":"⌖ البحث حول موقعك: ")
            +place+String.format(Locale.US,"\n%.4f, %.4f",latitude,longitude),14,textColor,true);
        location.setBackground(shape(false));
        root.addView(location,new LinearLayout.LayoutParams(-1,-2));
        gap(root,12);

        root.addView(label("نطاق البحث",15,textColor,true));
        LinearLayout radiusRow=new LinearLayout(this);
        radiusRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for(int i=0;i<RADII.length;i++){
            final int index=i;
            TextView tab=label((RADII[i]/1000)+" كم",14,gold,true);
            radiusTabs[i]=tab;
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);
            lp.setMargins(dp(3),0,dp(3),0);
            radiusRow.addView(tab,lp);
            tab.setOnClickListener(v->{
                if(searching)return;
                selectedRadius=index;
                getSharedPreferences("bas_compass",MODE_PRIVATE).edit()
                    .putInt("mosqueRadius",index).apply();
                updateRadiusTabs();
            });
        }
        root.addView(radiusRow);
        updateRadiusTabs();
        gap(root,13);

        searchButton=label("بحث عن المساجد الآن",17,gold,true);
        searchButton.setBackground(shape(true));
        searchButton.setOnClickListener(v->searchMosques());
        root.addView(searchButton,new LinearLayout.LayoutParams(-1,dp(55)));
        gap(root,8);
        TextView external=label("فتح بحث المساجد في خرائط الهاتف",14,gold,true);
        external.setBackground(shape(false));
        external.setOnClickListener(v->openExternalSearch());
        root.addView(external,new LinearLayout.LayoutParams(-1,dp(50)));
        gap(root,12);
        root.addView(label("الخصوصية: عند الضغط على «بحث» تُرسل إحداثيات الموقع المختار "
            +"إلى خدمة Overpass لبيانات OpenStreetMap. البحث فقط يحتاج إنترنت؛ "
            +"البوصلة والقبلة تظلان تعملان دون اتصال.",12,muted,false));
        root.addView(label("النتائج من مساهمات OpenStreetMap وقد تكون ناقصة أو قديمة. "
            +"المسافة المعروضة بخط مستقيم وليست مسافة الطريق. "
            +"إذا اخترت مدينة يدوياً فالنتائج حول مركز المدينة لا حول موقعك الفعلي.",12,muted,false));
        gap(root,9);

        statusLabel=label("اختر نطاق البحث ثم اضغط «بحث عن المساجد الآن».",14,muted,false);
        root.addView(statusLabel);
        progress=new ProgressBar(this);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(dp(32),dp(32));
        progressParams.gravity=Gravity.CENTER_HORIZONTAL;
        progress.setVisibility(View.GONE);
        root.addView(progress,progressParams);
        gap(root,5);
        results=column();
        root.addView(results,new LinearLayout.LayoutParams(-1,-2));
    }
    private void updateRadiusTabs() {
        for(int i=0;i<radiusTabs.length;i++)
            radiusTabs[i].setBackground(shape(i==selectedRadius));
    }
    private void setBusy(boolean busy){
        searching=busy;
        progress.setVisibility(busy?View.VISIBLE:View.GONE);
        searchButton.setEnabled(!busy);
        searchButton.setAlpha(busy?0.55f:1f);
        searchButton.setText(busy?"جارٍ البحث عن المساجد…":"بحث عن المساجد الآن");
        for(TextView tab:radiusTabs)tab.setEnabled(!busy);
    }
    private void searchMosques(){
        if(searching)return;
        setBusy(true);
        results.removeAllViews();
        final double lat=latitude,lon=longitude;
        final int radius=RADII[selectedRadius];
        statusLabel.setText("جارٍ البحث عن المساجد في نطاق "+radius/1000+" كم…");
        new Thread(()->{
            try{
                ArrayList<Mosque> found=loadMosques(lat,lon,radius);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    setBusy(false);
                    showResults(found,radius);
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    setBusy(false);
                    statusLabel.setText("تعذر جلب المساجد. تحقق من الإنترنت ثم حاول مجدداً. "
                        +"يمكنك استخدام زر خرائط الهاتف كبديل.");
                });
            }
        },"BAS-mosque-search").start();
    }
    private ArrayList<Mosque> loadMosques(double lat,double lon,int radius) throws Exception{
        String center=String.format(Locale.US,"(around:%d,%.7f,%.7f)",radius,lat,lon);
        String query="[out:json][timeout:22];("
            +"nwr[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"]"+center+";"
            +"nwr[\"building\"=\"mosque\"]"+center+";"
            +");out center;";
        byte[] payload=("data="+URLEncoder.encode(query,"UTF-8")).getBytes("UTF-8");
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)new URL(OVERPASS_URL).openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
            c.setRequestProperty("Accept","application/json");
            c.setRequestProperty("User-Agent","BAS-Compass-Qibla/1.2 (nearby mosque lookup)");
            c.setConnectTimeout(12000);
            c.setReadTimeout(27000);
            c.setDoOutput(true);
            try(OutputStream out=c.getOutputStream()){out.write(payload);}
            int status=c.getResponseCode();
            if(status!=200)throw new java.io.IOException("Overpass HTTP "+status);
            ByteArrayOutputStream data=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] buf=new byte[4096];int read;
                while((read=in.read(buf))!=-1){
                    if(data.size()+read>4_000_000)throw new java.io.IOException("Response too large");
                    data.write(buf,0,read);
                }
            }
            return parse(new JSONObject(data.toString("UTF-8")),lat,lon,radius);
        }finally{
            if(c!=null)c.disconnect();
        }
    }
    private ArrayList<Mosque> parse(JSONObject response,double lat,double lon,int radius){
        JSONArray entries=response.optJSONArray("elements");
        ArrayList<Mosque> list=new ArrayList<>();
        if(entries==null)return list;
        for(int i=0;i<entries.length();i++){
            JSONObject object=entries.optJSONObject(i);
            if(object==null)continue;
            JSONObject center=object.optJSONObject("center");
            double mlat=center==null?object.optDouble("lat",Double.NaN)
                :center.optDouble("lat",Double.NaN);
            double mlon=center==null?object.optDouble("lon",Double.NaN)
                :center.optDouble("lon",Double.NaN);
            if(!valid(mlat,mlon))continue;
            double meters=distanceMeters(lat,lon,mlat,mlon);
            if(meters>radius+150)continue;
            JSONObject tags=object.optJSONObject("tags");
            String name="";
            String street="";
            if(tags!=null){
                name=clean(tags.optString("name:ar",""));
                if(name.isEmpty())name=clean(tags.optString("name",""));
                if(name.isEmpty())name=clean(tags.optString("official_name",""));
                street=clean(tags.optString("addr:street",""));
            }
            if(name.isEmpty())name="مسجد غير مسمى على الخريطة";
            list.add(new Mosque(name,street,mlat,mlon,meters));
        }
        Collections.sort(list,Comparator.comparingDouble(m->m.distance));
        ArrayList<Mosque> unique=new ArrayList<>();
        for(Mosque m:list){
            boolean duplicate=false;
            for(Mosque existing:unique){
                if(distanceMeters(m.lat,m.lon,existing.lat,existing.lon)<35
                    && (m.name.equals(existing.name)
                        ||m.name.startsWith("مسجد غير مسمى")
                        ||existing.name.startsWith("مسجد غير مسمى"))){
                    duplicate=true;break;
                }
            }
            if(!duplicate)unique.add(m);
        }
        return unique;
    }
    private void showResults(ArrayList<Mosque> mosques,int radius){
        if(mosques.isEmpty()){
            statusLabel.setText("لم نجد مساجد موثقة في OpenStreetMap ضمن "
                +radius/1000+" كم. قد تكون البيانات غير مكتملة. جرّب نطاقاً أكبر "
                +"أو زر البحث في خرائط الهاتف.");
            return;
        }
        int count=Math.min(MAX_DISPLAY,mosques.size());
        statusLabel.setText("عُثر على "+mosques.size()+" مسجد/مصلّى موثّق كمسجد. "
            +"الأقرب أولاً — عرض "+count+" نتيجة. المسافات بخط مستقيم.");
        for(int i=0;i<count;i++){
            Mosque m=mosques.get(i);
            LinearLayout card=column();
            card.setPadding(dp(7),dp(7),dp(7),dp(8));
            card.setBackground(shape(false));
            TextView name=label((i+1)+". "+m.name,16,textColor,true);
            name.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            card.addView(name);
            String distanceText=m.distance<1000
                ?String.format(Locale.US,"%.0f متر",m.distance)
                :String.format(Locale.US,"%.2f كم",m.distance/1000.0);
            String extra="المسافة التقريبية: "+distanceText;
            if(!m.street.isEmpty())extra+="  •  "+m.street;
            TextView details=label(extra,13,muted,false);
            details.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            card.addView(details);
            LinearLayout buttons=new LinearLayout(this);
            buttons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            TextView directions=label("الاتجاهات",14,gold,true);
            TextView onMap=label("عرض على الخريطة",14,gold,false);
            directions.setBackground(shape(true));onMap.setBackground(shape(false));
            LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,dp(45),1);
            first.setMargins(dp(3),dp(3),dp(3),dp(3));
            LinearLayout.LayoutParams second=new LinearLayout.LayoutParams(0,dp(45),1);
            second.setMargins(dp(3),dp(3),dp(3),dp(3));
            buttons.addView(directions,first);buttons.addView(onMap,second);
            directions.setOnClickListener(v->openDirections(m));
            onMap.setOnClickListener(v->openOnMap(m));
            card.addView(buttons);
            results.addView(card,new LinearLayout.LayoutParams(-1,-2));
            gap(results,9);
        }
        results.addView(label("© مساهمو OpenStreetMap • البيانات حسب توفرها في الخدمة.",
            11,muted,false));
    }
    private void openDirections(Mosque m){
        String target=String.format(Locale.US,"%.7f,%.7f",m.lat,m.lon);
        String link="https://www.google.com/maps/dir/?api=1&destination="
            +Uri.encode(target)+"&travelmode=walking";
        launch(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));
    }
    private void openOnMap(Mosque m){
        String coords=String.format(Locale.US,"%.7f,%.7f",m.lat,m.lon);
        Intent geo=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(coords)));
        try{startActivity(geo);}
        catch(ActivityNotFoundException ex){
            launch(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query="
                    +Uri.encode(coords))));
        }
    }
    private void openExternalSearch(){
        String geo=String.format(Locale.US,"geo:%.7f,%.7f?q=%s",
            latitude,longitude,Uri.encode("مساجد"));
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(geo)));}
        catch(ActivityNotFoundException ex){
            String query=String.format(Locale.US,"مساجد بالقرب من %.6f,%.6f",latitude,longitude);
            launch(new Intent(Intent.ACTION_VIEW,Uri.parse(
                "https://www.google.com/maps/search/?api=1&query="+Uri.encode(query))));
        }
    }
    private void launch(Intent intent){
        try{startActivity(intent);}
        catch(ActivityNotFoundException ex){
            Toast.makeText(this,"لا يوجد تطبيق خرائط أو متصفح لفتح الرابط",Toast.LENGTH_LONG).show();
        }
    }
    private static String clean(String input){
        if(input==null)return "";
        String value=input.trim();
        return value.length()>80?value.substring(0,80):value;
    }
    private static double distanceMeters(double la1,double lo1,double la2,double lo2){
        double p1=Math.toRadians(la1),p2=Math.toRadians(la2);
        double dlat=p2-p1,dlon=Math.toRadians(lo2-lo1);
        double h=Math.pow(Math.sin(dlat/2),2)
            +Math.cos(p1)*Math.cos(p2)*Math.pow(Math.sin(dlon/2),2);
        return 12742017.6*Math.asin(Math.min(1,Math.sqrt(h)));
    }
    private static final class Mosque{
        final String name,street;
        final double lat,lon,distance;
        Mosque(String name,String street,double lat,double lon,double distance){
            this.name=name;this.street=street;this.lat=lat;this.lon=lon;this.distance=distance;
        }
    }
}

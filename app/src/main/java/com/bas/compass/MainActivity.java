package com.bas.compass;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public final class MainActivity extends Activity implements SensorEventListener, LocationListener {
    private static final int LOCATION_REQUEST = 77;
    private static final double KAABA_LAT = 21.4224779, KAABA_LON = 39.8251832;
    private static final String[] CITY_NAMES = {
        "سكاكا", "الرياض", "جدة", "المدينة المنورة", "تبوك", "الدمام",
        "مكة المكرمة", "أبها", "بريدة", "حائل", "نجران", "القريات"
    };
    private static final double[][] CITIES = {
        {29.9697,40.2064},{24.7136,46.6753},{21.5433,39.1728},{24.4686,39.6142},
        {28.3998,36.5700},{26.4207,50.0888},{21.3891,39.8579},{18.2164,42.5053},
        {26.3592,43.9818},{27.5114,41.7208},{17.565,44.2289},{31.3457,37.3336}
    };

    private SharedPreferences prefs;
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private Sensor rotationSensor, accelSensor, magneticSensor;
    private final float[] accel = new float[3], magnetic = new float[3];
    private boolean haveAccel, haveMag, manual, night = true, vibrating = true, qiblaMode = true, aligned;
    private boolean locationActive;
    private double lat = Double.NaN, lon = Double.NaN, bearing = Double.NaN, distance = Double.NaN;
    private double declination, heading = Double.NaN, tilt = Double.NaN, fieldStrength = Double.NaN;
    private String place = "لم يتم تحديد الموقع";
    private int magneticAccuracy = -1;
    private long lastRender, lastVibration;
    private TextView locationText, headerLabel, headingText, targetText, instructionText, detailsText, accuracyText;
    private TextView qiblaTab, compassTab;
    private CompassFace face;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("bas_compass", MODE_PRIVATE);
        night = prefs.getBoolean("night", true);
        vibrating = prefs.getBoolean("vibrate", true);
        qiblaMode = prefs.getBoolean("qibla", true);
        if (prefs.getBoolean("manual", false)) {
            double storedLat = Double.longBitsToDouble(prefs.getLong("latitude", Double.doubleToLongBits(Double.NaN)));
            double storedLon = Double.longBitsToDouble(prefs.getLong("longitude", Double.doubleToLongBits(Double.NaN)));
            if (valid(storedLat, storedLon)) setCoordinates(storedLat, storedLon, prefs.getString("place", "موقع يدوي"), true);
        }
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            if (rotationSensor != null) sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
            else if (accelSensor != null) sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI);
            if (magneticSensor != null) sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI);
        }
        if (!manual) {
            if (hasLocationPermission()) startLocation();
            else if (!prefs.getBoolean("permissionAsked", false)) {
                prefs.edit().putBoolean("permissionAsked", true).apply();
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
            }
        }
        updateUi();
    }

    @Override protected void onPause() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        stopLocation();
        super.onPause();
    }

    private int dp(float n) { return (int)(getResources().getDisplayMetrics().density * n + .5f); }
    private int background() { return Color.parseColor(night ? "#080E17" : "#F0F3F8"); }
    private int panel() { return Color.parseColor(night ? "#121F2B" : "#FFFFFF"); }
    private int foreground() { return Color.parseColor(night ? "#F5F1E8" : "#172333"); }
    private int secondary() { return Color.parseColor(night ? "#96A6B5" : "#66778B"); }
    private int gold() { return Color.parseColor(night ? "#EBC57B" : "#987024"); }
    private GradientDrawable panelShape(boolean highlighted) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(panel());
        d.setCornerRadius(dp(19));
        d.setStroke(dp(1), highlighted ? gold() : Color.parseColor(night ? "#2A3C4A" : "#DFE5EC"));
        return d;
    }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextColor(color); t.setTextSize(size);
        t.setGravity(Gravity.CENTER); t.setPadding(dp(8), dp(7), dp(8), dp(7));
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL); l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }
    private void gap(LinearLayout l, int h) { l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h))); }
    private void buildUi() {
        getWindow().setStatusBarColor(background());
        getWindow().setNavigationBarColor(background());
        getWindow().getDecorView().setSystemUiVisibility(night ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false); scroll.setBackgroundColor(background());
        LinearLayout root = vertical(); root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root); setContentView(scroll);

        TextView title = label("✦  BAS COMPASS", 22, gold(), true);
        root.addView(title);
        root.addView(label("البوصلة الذكية • اتجاه القبلة", 13, secondary(), false));
        gap(root, 13);
        locationText = label("", 15, foreground(), true);
        locationText.setBackground(panelShape(false));
        locationText.setOnClickListener(v -> chooseLocation());
        root.addView(locationText, new LinearLayout.LayoutParams(-1, dp(58)));
        gap(root, 15);

        LinearLayout modes = new LinearLayout(this); modes.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        qiblaTab = label("اتجاه القبلة", 16, foreground(), true);
        compassTab = label("البوصلة", 16, foreground(), true);
        modes.addView(qiblaTab, new LinearLayout.LayoutParams(0, dp(50), 1));
        modes.addView(compassTab, new LinearLayout.LayoutParams(0, dp(50), 1));
        qiblaTab.setOnClickListener(v -> { qiblaMode = true; prefs.edit().putBoolean("qibla", true).apply(); updateUi(); });
        compassTab.setOnClickListener(v -> { qiblaMode = false; prefs.edit().putBoolean("qibla", false).apply(); updateUi(); });
        root.addView(modes); gap(root, 9);

        LinearLayout compassCard = vertical(); compassCard.setPadding(dp(8), dp(10), dp(8), dp(12));
        compassCard.setBackground(panelShape(true));
        headerLabel = label("", 15, gold(), true); compassCard.addView(headerLabel);
        face = new CompassFace(this);
        compassCard.addView(face, new LinearLayout.LayoutParams(-1, dp(315)));
        targetText = label("", 30, gold(), true); compassCard.addView(targetText);
        instructionText = label("", 16, foreground(), true); compassCard.addView(instructionText);
        root.addView(compassCard); gap(root, 11);

        LinearLayout readings = vertical(); readings.setPadding(dp(12), dp(10), dp(12), dp(10));
        readings.setBackground(panelShape(false));
        headingText = label("", 18, foreground(), true);
        detailsText = label("", 14, secondary(), false);
        readings.addView(headingText); readings.addView(detailsText);
        root.addView(readings); gap(root, 11);

        accuracyText = label("", 14, secondary(), false);
        accuracyText.setBackground(panelShape(false));
        root.addView(accuracyText); gap(root, 12);

        LinearLayout controls = new LinearLayout(this); controls.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView where = label("⌖ الموقع", 15, gold(), true);
        TextView settings = label("⚙ الإعدادات", 15, gold(), true);
        where.setBackground(panelShape(false)); settings.setBackground(panelShape(false));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1);
        p.setMargins(dp(3),0,dp(3),0); controls.addView(where,p);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(52), 1);
        p2.setMargins(dp(3),0,dp(3),0); controls.addView(settings,p2);
        where.setOnClickListener(v -> chooseLocation());
        settings.setOnClickListener(v -> settingsDialog());
        root.addView(controls);
        gap(root, 7);
        root.addView(label("لأفضل دقة: أمسك الهاتف مستوياً وبعيداً عن المغناطيس والمعادن.", 12, secondary(), false));
        updateUi();
    }

    private void settingsDialog() {
        String[] choices = {"التبديل إلى الوضع " + (night ? "النهاري" : "الليلي"),
                "الاهتزاز عند محاذاة القبلة: " + (vibrating ? "مفعل" : "متوقف"),
                "إرشادات معايرة البوصلة"};
        new AlertDialog.Builder(this).setTitle("إعدادات BAS Compass")
            .setItems(choices, (d, which) -> {
                if (which == 0) { night = !night; prefs.edit().putBoolean("night", night).apply(); buildUi(); }
                else if (which == 1) { vibrating = !vibrating; prefs.edit().putBoolean("vibrate", vibrating).apply(); updateUi(); }
                else new AlertDialog.Builder(this).setTitle("معايرة البوصلة")
                    .setMessage("حرّك الهاتف ببطء على شكل رقم ٨ عدة مرات بعيداً عن المعادن والمغناطيس، ثم ضعه مستوياً وأعد المقارنة مع اتجاه معروف. لا يمكن ضمان الدقة إذا كان المستشعر معطلاً.")
                    .setPositiveButton("حسناً", null).show();
            }).setNegativeButton("إغلاق",null).show();
    }

    private void chooseLocation() {
        String[] choices = new String[CITY_NAMES.length + 2];
        choices[0] = "⌖ تحديث موقعي الحالي (GPS)";
        System.arraycopy(CITY_NAMES, 0, choices, 1, CITY_NAMES.length);
        choices[choices.length - 1] = "إدخال إحداثيات مخصصة";
        new AlertDialog.Builder(this).setTitle("اختر الموقع")
            .setItems(choices, (d, i) -> {
                if (i == 0) {
                    manual = false; prefs.edit().putBoolean("manual", false).apply();
                    lat = Double.NaN; lon = Double.NaN; place = "جارٍ تحديد الموقع";
                    bearing = Double.NaN; distance = Double.NaN; updateUi();
                    if (hasLocationPermission()) startLocation();
                    else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
                } else if (i == choices.length - 1) manualCoordinates();
                else setCoordinates(CITIES[i-1][0], CITIES[i-1][1], CITY_NAMES[i-1], true);
            }).show();
    }

    private void manualCoordinates() {
        LinearLayout box = vertical(); box.setPadding(dp(22),dp(9),dp(22),0);
        EditText latitudeInput = new EditText(this); latitudeInput.setHint("خط العرض، مثال 29.9697");
        EditText longitudeInput = new EditText(this); longitudeInput.setHint("خط الطول، مثال 40.2064");
        int inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
        latitudeInput.setInputType(inputType); longitudeInput.setInputType(inputType);
        box.addView(latitudeInput); box.addView(longitudeInput);
        new AlertDialog.Builder(this).setTitle("إحداثيات الموقع").setView(box)
            .setPositiveButton("اعتماد", (d, w) -> {
                try {
                    double a = Double.parseDouble(latitudeInput.getText().toString().trim());
                    double b = Double.parseDouble(longitudeInput.getText().toString().trim());
                    if (!valid(a,b)) throw new IllegalArgumentException();
                    setCoordinates(a,b,"موقع يدوي",true);
                } catch(Exception ex) { Toast.makeText(this,"إحداثيات غير صالحة",Toast.LENGTH_LONG).show(); }
            }).setNegativeButton("إلغاء",null).show();
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    @Override public void onRequestPermissionsResult(int req, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(req, permissions, results);
        if (req == LOCATION_REQUEST) {
            if (hasLocationPermission()) startLocation();
            else { place = "صلاحية الموقع غير متاحة؛ اختر مدينة يدوياً"; updateUi(); }
        }
    }
    private void startLocation() {
        if (locationManager == null || !hasLocationPermission() || manual) return;
        stopLocation();
        try {
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (!locationManager.isProviderEnabled(provider)) continue;
                Location last = locationManager.getLastKnownLocation(provider);
                if (last != null && System.currentTimeMillis() - last.getTime() < 30 * 60 * 1000L)
                    onLocationChanged(last);
                locationManager.requestLocationUpdates(provider, 3000L, 5f, this);
                locationActive = true;
            }
            if (!locationActive && !Double.isFinite(lat)) { place="فعّل خدمة الموقع أو اختر مدينة"; updateUi(); }
        } catch (SecurityException | IllegalArgumentException ex) {
            place = "تعذر الوصول إلى الموقع؛ اختر مدينة"; updateUi();
        }
    }
    private void stopLocation() {
        if (locationManager != null && locationActive) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) {}
        }
        locationActive = false;
    }
    @Override public void onLocationChanged(Location location) {
        if (!manual && location != null && location.hasAccuracy() && location.getAccuracy() <= 10000
                && valid(location.getLatitude(), location.getLongitude()))
            setCoordinates(location.getLatitude(), location.getLongitude(),"موقعي الحالي",false);
    }
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}

    private void setCoordinates(double a,double b,String title,boolean userSelected) {
        if (!valid(a,b)) return;
        lat=a;lon=b;place=title; manual=userSelected;
        bearing=bearingTo(a,b,KAABA_LAT,KAABA_LON);
        distance=distanceTo(a,b,KAABA_LAT,KAABA_LON);
        try { declination = new GeomagneticField((float)a,(float)b,0,System.currentTimeMillis()).getDeclination(); }
        catch (Exception ex) { declination=0; }
        aligned=false;
        if (userSelected) {
            stopLocation();
            prefs.edit().putBoolean("manual",true)
                .putLong("latitude",Double.doubleToLongBits(a)).putLong("longitude",Double.doubleToLongBits(b))
                .putString("place",title).apply();
        }
        updateUi();
    }
    private static boolean valid(double a,double b) { return Double.isFinite(a) && Double.isFinite(b) && a>=-90 && a<=90 && b>=-180 && b<=180; }
    private static double norm(double angle) { angle%=360;return angle<0?angle+360:angle; }
    private static double difference(double a,double b) { return (norm(a-b+180)+360)%360-180; }
    private static double bearingTo(double la,double lo,double targetLat,double targetLon) {
        double p1=Math.toRadians(la),p2=Math.toRadians(targetLat),d=Math.toRadians(targetLon-lo);
        return norm(Math.toDegrees(Math.atan2(Math.sin(d)*Math.cos(p2),
                Math.cos(p1)*Math.sin(p2)-Math.sin(p1)*Math.cos(p2)*Math.cos(d))));
    }
    private static double distanceTo(double la,double lo,double la2,double lo2) {
        double p1=Math.toRadians(la),p2=Math.toRadians(la2),d1=p2-p1,d2=Math.toRadians(lo2-lo);
        double a=Math.pow(Math.sin(d1/2),2)+Math.cos(p1)*Math.cos(p2)*Math.pow(Math.sin(d2/2),2);
        return 12742.0176*Math.asin(Math.min(1,Math.sqrt(a)));
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magnetic = new float[]{event.values[0],event.values[1],event.values[2]};
            haveMag=true;
            fieldStrength=Math.sqrt(magnetic[0]*magnetic[0]+magnetic[1]*magnetic[1]+magnetic[2]*magnetic[2]);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values,0,accel,0,3); haveAccel=true;
        }
        float[] matrix = new float[9];
        if (rotationSensor != null) {
            if (event.sensor.getType()!=Sensor.TYPE_ROTATION_VECTOR) { updateUiThrottled(); return; }
            SensorManager.getRotationMatrixFromVector(matrix,event.values);
        } else if (!(haveAccel && haveMag && SensorManager.getRotationMatrix(matrix,null,accel,magnetic))) {
            updateUiThrottled();return;
        }
        float[] angles = new float[3];
        SensorManager.getOrientation(matrix,angles);
        double measured = norm(Math.toDegrees(angles[0]) + declination);
        heading = Double.isFinite(heading) ? norm(heading + difference(measured,heading)*0.22) : measured;
        tilt = Math.max(Math.abs(Math.toDegrees(angles[1])),Math.abs(Math.toDegrees(angles[2])));
        updateUiThrottled();
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) {
        if (sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD) magneticAccuracy=accuracy;
        updateUiThrottled();
    }
    private void updateUiThrottled() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRender >= 90) { lastRender=now; updateUi(); }
    }
    private boolean sensorReliable() {
        return Double.isFinite(heading) && (!Double.isFinite(tilt) || tilt < 55)
            && (magneticAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                || magneticAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
            && (!Double.isFinite(fieldStrength) || (fieldStrength >= 20 && fieldStrength <= 75));
    }
    private void updateUi() {
        if (locationText == null) return;
        locationText.setText("⌖  " + place + "   ▾");
        qiblaTab.setBackground(panelShape(qiblaMode));
        compassTab.setBackground(panelShape(!qiblaMode));
        qiblaTab.setTextColor(qiblaMode ? gold() : secondary());
        compassTab.setTextColor(!qiblaMode ? gold() : secondary());
        boolean hasFix=valid(lat,lon) && Double.isFinite(bearing);
        boolean hasHeading=Double.isFinite(heading);
        headerLabel.setText(qiblaMode ? "✦ اتجاه الكعبة" : "✦ البوصلة الحقيقية");
        if (qiblaMode) {
            targetText.setText(hasFix ? String.format(Locale.US,"%.1f°",bearing) : "—");
            if (!hasFix) instructionText.setText("اضغط على الموقع لاختيار مدينتك أو تشغيل GPS");
            else if (!hasHeading) instructionText.setText("الزاوية محسوبة، لكن مستشعر الاتجاه غير متاح");
            else {
                double turn=difference(bearing,heading);
                if (Math.abs(turn) <= 3) instructionText.setText("✓ أنت باتجاه القبلة تقريباً");
                else instructionText.setText(String.format(Locale.US,"أدر الهاتف %s %.0f°",turn>0?"يميناً":"يساراً",Math.abs(turn)));
                long now=SystemClock.elapsedRealtime();
                boolean currentlyAligned=Math.abs(turn)<=3 && sensorReliable();
                if (currentlyAligned && !aligned && vibrating && now-lastVibration>3000) {
                    Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);
                    if (v!=null && v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(80,VibrationEffect.DEFAULT_AMPLITUDE));
                    lastVibration=now;
                }
                aligned=currentlyAligned;
            }
        } else {
            targetText.setText(hasHeading ? String.format(Locale.US,"%.1f°",heading) : "—");
            instructionText.setText(hasHeading ? directionName(heading) : "مستشعر الاتجاه غير متاح في هذا الجهاز");
        }
        headingText.setText(hasHeading ? String.format(Locale.US,"اتجاه أعلى الهاتف: %.1f° من الشمال الحقيقي",heading)
            : "لم تتوفر قراءة اتجاه حية");
        detailsText.setText(hasFix ? String.format(Locale.US,"زاوية القبلة: %.1f°   •   المسافة إلى الكعبة: %,.0f كم\nانحراف مغناطيسي: %+.1f°  •  موقع: %.4f, %.4f",
                 bearing,distance,declination,lat,lon) : "لا توجد إحداثيات معتمدة. لا تستخدم زاوية افتراضية.");
        String quality;
        if (!hasHeading) quality="⚠ لا يمكن تحديد اتجاه الهاتف. تحقق من وجود المستشعر.";
        else if (Double.isFinite(tilt) && tilt>=55) quality="⚠ أمسك الهاتف مستوياً لتحديد الاتجاه بدقة أفضل.";
        else if (Double.isFinite(fieldStrength) && (fieldStrength<20 || fieldStrength>75))
            quality="⚠ تداخل مغناطيسي محتمل، أبعد الهاتف عن المعادن.";
        else if (magneticAccuracy<2) quality="⚠ دقة البوصلة غير مؤكدة. حرّك الهاتف على شكل ٨ لمعايرتها.";
        else quality="✓ قراءة المستشعر مقبولة، لكنها تحتاج التحقق عند الاستخدام الفعلي.";
        accuracyText.setText(quality + (Double.isFinite(fieldStrength) ?
            String.format(Locale.US,"\nالمجال المغناطيسي: %.1f µT",fieldStrength) : ""));
        face.invalidate();
    }
    private static String directionName(double h) {
        String[] points={"شمال","شمال شرقي","شرق","جنوب شرقي","جنوب","جنوب غربي","غرب","شمال غربي"};
        return points[((int)Math.round(norm(h)/45))%8];
    }
    private final class CompassFace extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        CompassFace(Context ctx) { super(ctx); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
        private void ink(int color,Paint.Style style,float stroke) {
            paint.setColor(color);paint.setStyle(style);paint.setStrokeWidth(stroke);paint.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.43f;
            ink(Color.parseColor(night?"#192B39":"#E4EAF1"),Paint.Style.FILL,0);
            canvas.drawCircle(cx,cy,r+dp(14),paint);
            ink(gold(),Paint.Style.STROKE,dp(2));canvas.drawCircle(cx,cy,r+dp(6),paint);
            ink(Color.parseColor(night?"#294353":"#B1BFCE"),Paint.Style.STROKE,dp(1));
            canvas.drawCircle(cx,cy,r-dp(16),paint);
            boolean hasHeading=Double.isFinite(heading);
            double spin=hasHeading?heading:0;
            for(int a=0;a<360;a+=5) {
                double radians=Math.toRadians(a-spin-90);
                float ux=(float)Math.cos(radians),uy=(float)Math.sin(radians);
                float inner=r-(a%30==0?dp(23):a%10==0?dp(16):dp(11));
                ink(a%30==0?gold():secondary(),Paint.Style.STROKE,a%30==0?dp(2):dp(1));
                canvas.drawLine(cx+ux*inner,cy+uy*inner,cx+ux*(r-dp(4)),cy+uy*(r-dp(4)),paint);
            }
            String[] cardinal={"N","E","S","W"};
            for(int i=0;i<4;i++) {
                double radians=Math.toRadians(i*90-spin-90);
                ink(i==0?Color.parseColor("#EF6963"):foreground(),Paint.Style.FILL,0);
                paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(19));paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.drawText(cardinal[i],cx+(float)Math.cos(radians)*(r-dp(44)),
                    cy+(float)Math.sin(radians)*(r-dp(44))+dp(7),paint);
            }
            if (!hasHeading || (qiblaMode && !valid(lat,lon))) {
                ink(secondary(),Paint.Style.FILL,0);paint.setTextSize(dp(13));paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("—",cx,cy+dp(6),paint);return;
            }
            float angle=(float)(qiblaMode?difference(bearing,heading):-heading);
            canvas.save();canvas.rotate(angle,cx,cy);
            Path needle = new Path();
            needle.moveTo(cx,cy-r*.70f);needle.lineTo(cx-dp(15),cy+dp(9));
            needle.lineTo(cx,cy-dp(2));needle.lineTo(cx+dp(15),cy+dp(9));needle.close();
            ink(qiblaMode?gold():Color.parseColor("#EF6963"),Paint.Style.FILL,0);
            canvas.drawPath(needle,paint);
            Path tail=new Path();tail.moveTo(cx,cy+dp(7));tail.lineTo(cx-dp(10),cy+r*.39f);
            tail.lineTo(cx+dp(10),cy+r*.39f);tail.close();
            ink(Color.parseColor(night?"#5E6D7B":"#B4C1CF"),Paint.Style.FILL,0);
            canvas.drawPath(tail,paint);canvas.restore();
            ink(panel(),Paint.Style.FILL,0);canvas.drawCircle(cx,cy,dp(16),paint);
            ink(gold(),Paint.Style.STROKE,dp(3));canvas.drawCircle(cx,cy,dp(15),paint);
            ink(foreground(),Paint.Style.FILL,0);paint.setTextSize(dp(12));paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(qiblaMode?"◆":"N",cx,cy+dp(4),paint);
        }
    }
}

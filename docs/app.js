"use strict";
(function(){
const K={lat:21.422487,lon:39.826206};
const el=id=>document.getElementById(id);
const wrap=x=>(x%360+360)%360;
const delta=x=>((x+540)%360)-180;
const rad=x=>x*Math.PI/180;
const deg=x=>x*180/Math.PI;
const val=(x,n=1)=>Number.isFinite(x)?x.toFixed(n):"—";
let loc=null,qibla=NaN,km=NaN,decl=NaN,heading=NaN,mag=NaN,accuracy=NaN;
let fromSensor=false,lastReading=0,started=false,cacheReady=false;
const text=(id,s)=>{el(id).textContent=s;};
function warn(s,level="warn"){text("notice",s);el("notice").dataset.kind=level;}
function coordinates(lat,lon){
 const p=rad(lat),m=rad(K.lat),l=rad(K.lon-lon);
 const y=Math.sin(l)*Math.cos(m);
 const x=Math.cos(p)*Math.sin(m)-Math.sin(p)*Math.cos(m)*Math.cos(l);
 const a=Math.sin((m-p)/2)**2+Math.cos(p)*Math.cos(m)*Math.sin(l/2)**2;
 return {q:wrap(deg(Math.atan2(y,x))),distance:6371.0088*2*Math.asin(Math.sqrt(Math.min(1,a)))};
}
// The fixed needle shows where the top of the phone points.
// Cardinal directions and Kaaba are geographically fixed on the rotating dial.
let previousHeading=NaN, smoothHeading=0;
function renderDial(){
 const headingAvailable=Number.isFinite(heading)&&Number.isFinite(decl);
 if(headingAvailable){
  if(!Number.isFinite(previousHeading))smoothHeading=heading;
  else smoothHeading+=delta(heading-previousHeading);
  previousHeading=heading;
  el("dial").style.transform="rotate("+(-smoothHeading)+"deg)";
 }else previousHeading=NaN;
 const canShowKaaba=headingAvailable&&Number.isFinite(qibla);
 el("qiblaTrack").hidden=!canShowKaaba;
 el("compass").dataset.active=canShowKaaba?"yes":"no";
 if(canShowKaaba){
  el("qiblaTrack").style.transform="rotate("+qibla+"deg)";
  el("kaabaMark").style.transform="translateX(-50%) rotate("+(smoothHeading-qibla)+"deg)";
 }
}

function render(){
 renderDial();
 text("posStatus",loc?({gps:"GPS الحالي",manual:"إحداثيات يدوية",city:"مركز مدينة تقريبي",saved:"موقع محفوظ"}[loc.source]||"موقع")+" • "+val(loc.lat,5)+"، "+val(loc.lon,5):"الموقع غير محدد");
 text("gpsQuality",loc&&loc.source==="gps"?"دقة GPS: ±"+Math.round(loc.accuracy)+" متر":loc?"هذا ليس موقع GPS مباشرًا":"لم يتحدد الموقع");
 text("bearing",Number.isFinite(qibla)?val(qibla)+"°":"—");
 text("heading",Number.isFinite(heading)?val(heading)+"°":"—");
 text("declination",Number.isFinite(decl)?(decl>=0?"+":"")+val(decl)+"°":"—");
 text("distance",Number.isFinite(km)?Math.round(km).toLocaleString("ar-SA")+" كم":"—");
 text("sensorQuality",!fromSensor?"لا توجد قراءة حية من البوصلة":Number.isFinite(accuracy)?"دقة المستشعر ±"+val(accuracy)+"°":"دقة الاتجاه غير متاحة من المتصفح");
 text("source",fromSensor?"اتجاه الهاتف: "+(Number.isFinite(accuracy)?"iPhone Compass":"مستشعر مطلق، يحتاج تحقق"):"لا توجد قراءة حية لاتجاه الهاتف");
 text("offline",cacheReady?"ملفات التطبيق محفوظة على هذا الهاتف للعمل دون إنترنت":"افتح الموقع متصلًا مرة أولًا وتأكد من جاهزية الحفظ");
 if(!Number.isFinite(qibla)){text("instruction","حدّد موقعك أولًا");text("degrees","—");return;}
 if(!Number.isFinite(heading)||!Number.isFinite(decl)){
  text("instruction","القبلة محسوبة، لكن القرص يحتاج قراءة بوصلة صالحة");
  text("degrees",val(qibla)+"° من الشمال الحقيقي");
  text("quality","المحاذاة الحية غير متاحة");el("quality").dataset.kind="warn";
  return;
 }
 const d=delta(qibla-heading);
 
 text("degrees",val(Math.abs(d),0)+"°");
 text("instruction",Math.abs(d)<=3?"علامة الكعبة عند السهم الثابت؛ أعلى الهاتف باتجاه القبلة حسابيًا":"أدر أعلى الهاتف "+val(Math.abs(d),0)+"° "+(d>0?"يمينًا":"يسارًا")+" لتصل الكعبة إلى المؤشر");
 const valid=loc&&loc.source==="gps"&&Number.isFinite(accuracy)&&accuracy>=0&&accuracy<=10;
 text("quality",valid?"القراءة مقبولة مبدئيًا":"تحقق من البوصلة والموقع قبل الاعتماد");
 el("quality").dataset.kind=valid?"good":"warn";
}
function recalc(){
 if(!loc)return;
 const result=coordinates(loc.lat,loc.lon);qibla=result.q;km=result.distance;
 const yr=new Date().getFullYear();
 if(yr<2025||yr>2029){decl=NaN;warn("انتهت صلاحية نموذج التصحيح المغناطيسي. لا يمكن عرض سهم محاذاة مصحح.","bad");}
 else try{decl=window.offlineDeclination(loc.lat,loc.lon,new Date());
 if(!Number.isFinite(decl))throw Error("Invalid declination");}
 catch(e){decl=NaN;warn("تعذر تصحيح الشمال المغناطيسي. زاوية القبلة متاحة لكن مؤشر الهاتف غير موثوق.","bad");}
 heading=Number.isFinite(mag)&&Number.isFinite(decl)?wrap(mag+decl):NaN;
 render();
}
function setLocation(p,persist=true){
 if(!Number.isFinite(p.lat)||Math.abs(p.lat)>90||!Number.isFinite(p.lon)||Math.abs(p.lon)>180){
 warn("خط العرض يجب أن يكون بين −٩٠ و٩٠ والطول بين −١٨٠ و١٨٠.","bad");return;
 }
 loc=p;el("lat").value=val(p.lat,6);el("lon").value=val(p.lon,6);
 if(persist)try{localStorage.setItem("bas_offline_qibla_location",JSON.stringify(p));}catch(e){}
 warn(p.source==="gps"?"تم تحديد الموقع. ابتعد عن جسم السيارة وشغّل البوصلة.":"الموقع ليس GPS مباشرًا؛ تحقق من إحداثيات موقعك الحالي قبل الصلاة.",p.source==="gps"?"good":"warn");
 recalc();
}
function getGPS(){
 if(!navigator.geolocation){warn("موقع GPS غير مدعوم في المتصفح؛ أدخل إحداثياتك.","bad");return;}
 el("locate").disabled=true;text("locate","جارٍ تحديد GPS…");
 navigator.geolocation.getCurrentPosition(p=>{
  el("locate").disabled=false;text("locate","تحديث GPS");
  setLocation({lat:p.coords.latitude,lon:p.coords.longitude,accuracy:p.coords.accuracy,source:"gps"});
 },e=>{
  el("locate").disabled=false;text("locate","تحديث GPS");
  warn((e.code===1?"تم رفض إذن الموقع":e.code===3?"انتهت مهلة GPS":"تعذر تحديد الموقع")+". تأكد من خدمة الموقع وخروجك لمكان مكشوف، أو أدخل الإحداثيات يدويًا.","warn");
 },{enableHighAccuracy:true,maximumAge:0,timeout:30000});
}
function orientation(e){
 let value=NaN,quality=NaN;
 if(typeof e.webkitCompassHeading==="number"&&e.webkitCompassHeading>=0){
  value=wrap(e.webkitCompassHeading);
  quality=typeof e.webkitCompassAccuracy==="number"?e.webkitCompassAccuracy:NaN;
 }else if((e.type==="deviceorientationabsolute"||e.absolute===true)&&Number.isFinite(e.alpha)){
  if(fromSensor&&Number.isFinite(accuracy))return;
  value=wrap(360-e.alpha+(window.screen&&window.screen.orientation?window.screen.orientation.angle||0:0));
 }else{return;}
 if(Number.isFinite(quality)&&quality<0){
  mag=NaN;heading=NaN;accuracy=quality;fromSensor=true;
  warn("حساس البوصلة غير معاير. ابتعد عن المعادن وحرك الهاتف على شكل رقم ٨.","bad");
  render();return;
 }
 mag=value;accuracy=quality;fromSensor=true;lastReading=Date.now();
 heading=Number.isFinite(decl)?wrap(mag+decl):NaN;
 render();
}
async function start(){
 if(started){warn("البوصلة مفعلة. أبقِ الهاتف شبه أفقي بعيدًا عن المغناطيس.","good");return;}
 try{
  // Prompt must originate from this user tap.
  const req=typeof DeviceOrientationEvent!=="undefined"&&typeof DeviceOrientationEvent.requestPermission==="function";
  const permission=req?await DeviceOrientationEvent.requestPermission(true):"granted";
  if(permission!=="granted"){warn("لم تُمنح صلاحية مستشعر اتجاه الهاتف.","bad");return;}
  window.addEventListener("deviceorientation",orientation,true);
  window.addEventListener("deviceorientationabsolute",orientation,true);
  started=true;text("start","البوصلة مفعلة");
  warn("اجعل أعلى الهاتف للأمام، وأبعده عن الحديد ومكبرات الصوت.","good");
  setTimeout(()=>{if(!fromSensor)warn("لم تصل قراءة بوصلة مطلقة من المتصفح. لا يمكن استنتاج اتجاه الهاتف من GPS وحده. جرّب جهازًا آخر أو بوصلة خارجية.","bad");},4500);
 }catch(e){warn("تعذر تفعيل مستشعر البوصلة: "+(e.message||e),"bad");}
}
function saveManual(){
 if(!el("lat").value.trim()||!el("lon").value.trim()){warn("أدخل خط العرض وخط الطول.","bad");return;}
 setLocation({lat:Number(el("lat").value),lon:Number(el("lon").value),source:"manual"});
}
function chooseCity(){
 if(!el("cities").value)return;
 const parts=el("cities").value.split(",");
 setLocation({lat:Number(parts[0]),lon:Number(parts[1]),source:"city"});
}
function manualHeading(){
 const s=el("manualHeading").value;
 const n=Number(s);
 if(!s.trim()||!Number.isFinite(n)||n<0||n>=360){warn("أدخل اتجاهًا مغناطيسيًا من ٠ إلى أقل من ٣٦٠.","bad");return;}
 if(!Number.isFinite(decl)){warn("حدد موقعًا واحسب التصحيح المغناطيسي أولًا.","bad");return;}
 // This is not a live sensor reading; label explicitly as unverified.
 mag=n;heading=wrap(n+decl);accuracy=NaN;fromSensor=false;lastReading=0;
 warn("وضع يدوي: قراءة البوصلة لا تتغير تلقائيًا مع دوران الهاتف.","warn");render();
}
el("start").addEventListener("click",start);
el("locate").addEventListener("click",getGPS);
el("saveManual").addEventListener("click",saveManual);
el("cities").addEventListener("change",chooseCity);
el("setHeading").addEventListener("click",manualHeading);
try{
 const saved=JSON.parse(localStorage.getItem("bas_offline_qibla_location")||"null");
 if(saved&&Number.isFinite(saved.lat)&&Number.isFinite(saved.lon))setLocation({...saved,source:"saved"},false);
 else{warn("حدد GPS وشغّل البوصلة. بعد الحفظ الأول سيعمل التطبيق دون شبكة.","neutral");render();}
}catch(e){render();}
if("serviceWorker" in navigator&&(location.protocol==="https:"||location.hostname==="localhost")){
 navigator.serviceWorker.register("./sw.js").then(()=>navigator.serviceWorker.ready).then(async()=>{
  const c=await caches.open("bas-qibla-offline-v2");
  cacheReady=!!(await c.match("./index.html"));render();
 }).catch(()=>render());
}
setInterval(()=>{
 if(started&&fromSensor&&Date.now()-lastReading>12000){
  mag=NaN;heading=NaN;fromSensor=false;render();
  warn("توقفت قراءة البوصلة. أعد فتح التطبيق وحرك الهاتف.","warn");
 }
},4500);
})();
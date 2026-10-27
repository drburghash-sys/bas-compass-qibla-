"use strict";
const CACHE="bas-qibla-offline-v2";
const ASSETS=["./","./index.html","./app.js","./wmm.js","./manifest.webmanifest","./icon.svg"];
self.addEventListener("install",event=>{
 event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(ASSETS)).then(()=>self.skipWaiting()));
});
self.addEventListener("activate",event=>{
 event.waitUntil(Promise.all([caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith("bas-qibla-")&&k!==CACHE).map(k=>caches.delete(k)))),self.clients.claim()]));
});
self.addEventListener("fetch",event=>{
 if(event.request.method!=="GET")return;
 const u=new URL(event.request.url);
 if(u.origin!==self.location.origin)return;
 event.respondWith((async()=>{
  try{
   const response=await fetch(event.request);
   if(response.ok){
    const c=await caches.open(CACHE);
    c.put(event.request,response.clone());
   }
   return response;
  }catch(e){
   const c=await caches.open(CACHE);
   return await c.match(event.request) || (event.request.mode==="navigate"?await c.match("./index.html"):Response.error());
  }
 })());
});
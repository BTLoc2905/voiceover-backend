import test from "node:test";
import assert from "node:assert/strict";
import { guestPoiItems, PoiDetailsLoader } from "./guestPoi.js";

const poi=(id,meters,status='PUBLISHED')=>({id,name:`POI ${id}`,latitude:0,longitude:meters/6371000*180/Math.PI,status,descriptionSource:'Original',sourceLanguageCode:'vi'});
test("only Published valid POIs appear; 500m highlighting uses real distance, not rounded label",()=>{
  const items=guestPoiItems([poi(1,499.9),poi(2,500.1),poi(3,10,'DRAFT'),poi(4,10,'HIDDEN'),{...poi(5,10),latitude:91}],{lat:0,lng:0});
  assert.deepEqual(items.map(i=>i.poi.id),[1,2]); assert.equal(items[0].nearby,true);assert.equal(items[1].nearby,false);
  assert.ok(items[1].distanceMeters>500);
  const moved=guestPoiItems([poi(1,499.9)],{lat:0,lng:-0.001});assert.equal(moved[0].nearby,false);
});
test("manual map still has Published POIs without GPS and does not invent distances",()=>{
  for(const position of [null,{lat:NaN,lng:0}]) {const items=guestPoiItems([poi(1,10)],position);assert.equal(items.length,1);assert.equal(items[0].distanceMeters,undefined);assert.equal(items[0].nearby,false);}
});
test("POI card prefers requested text independently of audio fallback, caching GPS redraws",async()=>{
  let calls=0; const loader=new PoiDetailsLoader(async()=>{calls++;return{poiId:1,activeLanguage:'vi',textContent:'Default audio text',textFallback:{activeLanguage:'en',textContent:'English detail'}};});
  const first=loader.load(poi(1,1),'en'), second=loader.load(poi(1,1),'en');assert.equal(first,second);
  assert.deepEqual(await first,{text:'English detail',language:'en'});assert.equal(calls,1);loader.clear();
});
test("language change aborts old details and ignores late translation, even if fetch ignores abort",async()=>{
  const pending=[]; const loader=new PoiDetailsLoader((id,lang,signal)=>new Promise(resolve=>pending.push({id,lang,signal,resolve})));
  const old=loader.load(poi(1,1),'en');const rejection=assert.rejects(old,{name:'AbortError'});
  const next=loader.load(poi(1,1),'fr');assert.equal(pending[0].signal.aborted,true);
  pending[1].resolve({poiId:1,textFallback:{activeLanguage:'fr',textContent:'Français'}});assert.equal((await next).language,'fr');
  pending[0].resolve({poiId:1,textContent:'English'});await rejection;loader.clear();
});
test("missing translation uses source description; wrong POI or network errors can be retried",async()=>{
  let result={poiId:1}; const loader=new PoiDetailsLoader(async()=>result);
  assert.deepEqual(await loader.load(poi(1,1),'en'),{text:'Original',language:'vi'});loader.clear();
  result={poiId:2};await assert.rejects(loader.load(poi(1,1),'en'));
  result={poiId:1,textFallback:{activeLanguage:'en',textContent:'Recovered'}};assert.equal((await loader.load(poi(1,1),'en')).text,'Recovered');loader.clear();
});

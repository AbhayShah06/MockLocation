package com.abhayshah.mocklocation;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import org.osmdroid.util.GeoPoint;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final String PREFS="mock_location_state";
    private EditText latitude,longitude,search;
    private TextView status,title,message,summary;
    private Button start,stop,find;
    private UpdateManager updater;
    private org.osmdroid.views.MapView map;
    private org.osmdroid.views.overlay.Marker targetPin;
    private boolean updatingFields,searching,dispatching,pendingSetupStart;
    private int pendingPermissionAction,searchGeneration;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService searchWorker=Executors.newSingleThreadExecutor();
    private Future<?> searchTask;
    private android.os.CancellationSignal realLocationRequest;
    private AlertDialog currentDialog;
    private final Runnable dispatchTimeout=()->{dispatching=false;refreshStatus();showMessage("Couldn't confirm the change. Check setup and try again.");};
    private final SharedPreferences.OnSharedPreferenceChangeListener stateListener=(prefs,key)->{
        if("session_revision".equals(key)||"active".equals(key)||"service_error".equals(key)||"latitude".equals(key)||"longitude".equals(key)){
            dispatching=false;main.removeCallbacks(dispatchTimeout);refreshStatus();
        }
    };
    private SharedPreferences prefs(){return getSharedPreferences(PREFS,MODE_PRIVATE);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(),false);setContentView(R.layout.activity_main);
        View screen=findViewById(R.id.screen);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(screen,(view,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()|androidx.core.view.WindowInsetsCompat.Type.displayCutout()|androidx.core.view.WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });androidx.core.view.ViewCompat.requestApplyInsets(screen);
        latitude=findViewById(R.id.latitude);longitude=findViewById(R.id.longitude);search=findViewById(R.id.locationSearch);
        status=findViewById(R.id.status);title=findViewById(R.id.statusTitle);message=findViewById(R.id.message);summary=findViewById(R.id.actionSummary);
        start=findViewById(R.id.start);stop=findViewById(R.id.stop);find=findViewById(R.id.searchLocation);
        setupMap();updater=UpdateManager.attach(this,findViewById(R.id.updates),this::showMessage);
        start.setOnClickListener(v->startMock());stop.setOnClickListener(v->stopMock());
        findViewById(R.id.setup).setOnClickListener(v->showSetup(false));findViewById(R.id.more).setOnClickListener(v->showMore());
        findViewById(R.id.savePlace).setOnClickListener(v->saveCurrentPlace());findViewById(R.id.useCurrent).setOnClickListener(v->useCurrentLocation());findViewById(R.id.pasteLocation).setOnClickListener(v->pasteLocation());
        findViewById(R.id.centerTarget).setOnClickListener(v->{double[] point=readTarget(true);if(point!=null){hideKeyboard();map.getController().animateTo(new GeoPoint(point[0],point[1]));}});
        findViewById(R.id.zoomIn).setOnClickListener(v->map.getController().zoomIn());findViewById(R.id.zoomOut).setOnClickListener(v->map.getController().zoomOut());
        find.setOnClickListener(v->{if(searching)cancelSearch();else searchLocation();});
        search.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){searchLocation();return true;}return false;});
        longitude.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){hideKeyboard();readTarget(true);return true;}return false;});
        updatingFields=true;latitude.setText(state==null?prefs().getString("last_target_lat","43.6532"):state.getString("target_lat","43.6532"));longitude.setText(state==null?prefs().getString("last_target_lon","-79.3832"):state.getString("target_lon","-79.3832"));updatingFields=false;
        if(state!=null){pendingPermissionAction=state.getInt("permission_action");pendingSetupStart=state.getBoolean("setup_start");}
        double[] point=readTarget(false);if(point!=null){targetPin.setPosition(new GeoPoint(point[0],point[1]));map.getController().setCenter(targetPin.getPosition());}
        if(state!=null){map.getController().setZoom(state.getDouble("map_zoom",13));map.getController().setCenter(new GeoPoint(state.getDouble("map_lat",43.6532),state.getDouble("map_lon",-79.3832)));}
        prefs().registerOnSharedPreferenceChangeListener(stateListener);renderSavedPlaces();renderRecentPlaces();if(state==null)handleIncoming(getIntent());refreshStatus();
    }

    private void setupMap(){
        org.osmdroid.config.IConfigurationProvider config=org.osmdroid.config.Configuration.getInstance();config.setUserAgentValue("MockLocation/"+BuildConfig.VERSION_NAME+" (+https://github.com/AbhayShah06/MockLocation)");
        config.setOsmdroidBasePath(new java.io.File(getCacheDir(),"maps"));config.setOsmdroidTileCache(new java.io.File(getCacheDir(),"maps/tiles"));config.setTileFileSystemCacheMaxBytes(100L*1024*1024);config.setTileFileSystemCacheTrimBytes(75L*1024*1024);
        map=findViewById(R.id.map);map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);map.setMultiTouchControls(true);map.setBuiltInZoomControls(false);map.getController().setZoom(13.0);
        map.setOnTouchListener((v,event)->{v.getParent().requestDisallowInterceptTouchEvent(event.getActionMasked()!=MotionEvent.ACTION_UP&&event.getActionMasked()!=MotionEvent.ACTION_CANCEL);return false;});
        map.getOverlays().add(new org.osmdroid.views.overlay.MapEventsOverlay(new org.osmdroid.events.MapEventsReceiver(){
            public boolean singleTapConfirmedHelper(GeoPoint point){pickTarget(point,false);return true;}public boolean longPressHelper(GeoPoint point){pickTarget(point,false);return true;}
        }));
        targetPin=new org.osmdroid.views.overlay.Marker(map);targetPin.setAnchor(.5f,1f);targetPin.setIcon(getDrawable(R.drawable.ic_target_pin));targetPin.setDraggable(true);targetPin.setTitle("Selected target");
        targetPin.setOnMarkerDragListener(new org.osmdroid.views.overlay.Marker.OnMarkerDragListener(){
            public void onMarkerDragStart(org.osmdroid.views.overlay.Marker marker){cancelSearch();hideKeyboard();}
            public void onMarkerDrag(org.osmdroid.views.overlay.Marker marker){pickTarget(marker.getPosition(),false);}
            public void onMarkerDragEnd(org.osmdroid.views.overlay.Marker marker){pickTarget(marker.getPosition(),false);}
        });map.getOverlays().add(targetPin);
        android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(android.text.Editable s){}
            public void onTextChanged(CharSequence s,int start,int before,int count){if(updatingFields)return;cancelSearch();latitude.setError(null);longitude.setError(null);double[] point=readTarget(false);if(point!=null){GeoPoint target=new GeoPoint(point[0],point[1]);targetPin.setPosition(target);map.getController().setCenter(target);persistTarget(point);}refreshStatus();}
        };latitude.addTextChangedListener(watcher);longitude.addTextChangedListener(watcher);
    }

    private double[] readTarget(boolean errors){
        double lat=Double.NaN,lon=Double.NaN;try{lat=Double.parseDouble(latitude.getText().toString().trim());}catch(NumberFormatException ignored){}try{lon=Double.parseDouble(longitude.getText().toString().trim());}catch(NumberFormatException ignored){}
        boolean latOk=Double.isFinite(lat)&&lat>=-90&&lat<=90,lonOk=Double.isFinite(lon)&&lon>=-180&&lon<=180;
        if(errors){latitude.setError(latOk?null:"Use a latitude from −90 to 90");longitude.setError(lonOk?null:"Use a longitude from −180 to 180");if(!latOk)latitude.requestFocus();else if(!lonOk)longitude.requestFocus();}
        return latOk&&lonOk?new double[]{lat,lon}:null;
    }
    private void persistTarget(double[] point){prefs().edit().putString("last_target_lat",String.valueOf(point[0])).putString("last_target_lon",String.valueOf(point[1])).apply();}
    private void pickTarget(GeoPoint point,boolean center){
        if(!Coordinates.isValid(point.getLatitude(),point.getLongitude()))return;cancelSearch();hideKeyboard();updatingFields=true;
        latitude.setText(String.format(Locale.US,"%.6f",point.getLatitude()));longitude.setText(String.format(Locale.US,"%.6f",point.getLongitude()));latitude.setError(null);longitude.setError(null);updatingFields=false;
        targetPin.setPosition(point);if(center)map.getController().animateTo(point);map.invalidate();persistTarget(new double[]{point.getLatitude(),point.getLongitude()});refreshStatus();renderSavedPlaces();renderRecentPlaces();
    }
    private boolean mockSelected(){try{return getSystemService(android.app.AppOpsManager.class).checkOpNoThrow(android.app.AppOpsManager.OPSTR_MOCK_LOCATION,android.os.Process.myUid(),getPackageName())==android.app.AppOpsManager.MODE_ALLOWED;}catch(RuntimeException unavailable){return false;}}
    private boolean active(){return prefs().getBoolean("active",false)&&MockLocationService.isPublishing();}
    private void refreshStatus(){
        if(title==null)return;boolean active=active();double[] target=readTarget(false);boolean changed=target==null||Math.abs(target[0]-Double.longBitsToDouble(prefs().getLong("latitude",0)))>.0000005||Math.abs(target[1]-Double.longBitsToDouble(prefs().getLong("longitude",0)))>.0000005;
        title.setText(active?"Mock location active":"Not active");title.setTextColor(active?0xff73e0b1:0xffa6afab);
        if(active)status.setText("Publishing "+format(Double.longBitsToDouble(prefs().getLong("latitude",0)),Double.longBitsToDouble(prefs().getLong("longitude",0))));
        else{String error=prefs().getString("service_error",null);status.setText(error!=null?error:mockSelected()?"Choose a target to start.":"Select this app in Android's mock-location setting.");}
        findViewById(R.id.setup).setVisibility(mockSelected()?View.GONE:View.VISIBLE);
        summary.setText(target==null?"Enter valid coordinates":format(target[0],target[1]));
        start.setText(dispatching?"Applying…":active?(changed?"Update location":"Location active"):"Start location");start.setEnabled(!dispatching&&(!active||changed));start.setAlpha(start.isEnabled()?1f:.55f);
        stop.setVisibility(active||prefs().getBoolean("active",false)?View.VISIBLE:View.GONE);stop.setEnabled(true);
    }
    private static String format(double lat,double lon){return String.format(Locale.US,"%.6f, %.6f",lat,lon);}
    private void showMessage(String text){message.setText(text);message.setVisibility(text==null||text.isEmpty()?View.GONE:View.VISIBLE);}
    private void hideKeyboard(){View focus=getCurrentFocus();if(focus!=null){getSystemService(InputMethodManager.class).hideSoftInputFromWindow(focus.getWindowToken(),0);focus.clearFocus();}}

    private void startMock(){
        if(dispatching)return;double[] point=readTarget(true);if(point==null)return;hideKeyboard();cancelSearch();
        if(!mockSelected()){showSetup(true);return;}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingPermissionAction=1;requestLocationPermission();return;}
        try{dispatching=true;refreshStatus();showMessage(null);main.removeCallbacks(dispatchTimeout);main.postDelayed(dispatchTimeout,5000);persistTarget(point);rememberRecent(point[0],point[1]);
            startForegroundService(new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_START).putExtra("latitude",point[0]).putExtra("longitude",point[1]));
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!prefs().getBoolean("notification_asked",false)){prefs().edit().putBoolean("notification_asked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},8);}
        }catch(RuntimeException error){dispatching=false;main.removeCallbacks(dispatchTimeout);refreshStatus();showMessage("Couldn't start. Check precise location permission and mock-location setup.");}
    }
    private void stopMock(){
        dispatching=false;main.removeCallbacks(dispatchTimeout);prefs().edit().remove("service_error").apply();
        try{startService(new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_STOP));}catch(RuntimeException error){stopService(new Intent(this,MockLocationService.class));}
        showMessage(null);refreshStatus();
    }
    private void requestLocationPermission(){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},7);}
    private boolean dialogShowing(){return currentDialog!=null&&currentDialog.isShowing();}
    private AlertDialog present(AlertDialog.Builder builder){if(dialogShowing())return currentDialog;AlertDialog dialog=builder.create();currentDialog=dialog;dialog.setOnDismissListener(d->{if(currentDialog==dialog)currentDialog=null;});dialog.show();return dialog;}
    private void showSetup(boolean resumeStart){
        if(dialogShowing())return;cancelSearch();present(new AlertDialog.Builder(this).setTitle("Mock-location setup").setMessage("In Developer options, choose Select mock location app, then Mock Location.\n\nIf Developer options is hidden, open About phone and tap Build number seven times.").setNegativeButton("Cancel",null).setPositiveButton("Open settings",(d,w)->{
            pendingSetupStart=resumeStart;try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(RuntimeException unavailable){pendingSetupStart=false;try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(RuntimeException noSettings){showMessage("Open Android Settings → Developer options → Select mock location app.");}}
        }));
    }
    private void showMore(){if(dialogShowing())return;cancelSearch();present(new AlertDialog.Builder(this).setTitle("Mock Location").setItems(new String[]{"Mock-location setup","App permissions","Check for updates","About"},(d,which)->{currentDialog=null;if(which==0)showSetup(false);else if(which==1){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}catch(RuntimeException unavailable){showMessage("Open Android Settings → Apps → Mock Location → Permissions.");}}else if(which==2)updater.checkManually();else present(new AlertDialog.Builder(this).setTitle("Mock Location "+BuildConfig.VERSION_NAME).setMessage("Android's mock-location utility. Map data © OpenStreetMap contributors.\n\nMap tiles and address search need a connection; entered coordinates work offline.").setPositiveButton("Done",null));}));}

    private void cancelSearch(){searchGeneration++;if(searchTask!=null)searchTask.cancel(true);searchTask=null;searching=false;if(find!=null)find.setText("Find");}
    private void searchLocation(){
        String query=search.getText().toString().trim();cancelSearch();if(query.isEmpty()){search.setError("Enter a place, coordinates or map link");return;}hideKeyboard();if(applyLocationText(query)){showMessage(null);return;}
        if(!Geocoder.isPresent()){showMessage("Address search isn't available on this device. Paste coordinates or choose a point on the map.");return;}
        int generation=++searchGeneration;searching=true;find.setText("Cancel");showMessage("Finding places…");
        searchTask=searchWorker.submit(()->{try{List<Address> results=new Geocoder(this).getFromLocationName(query,5);main.post(()->{
            if(isDestroyed()||isFinishing()||generation!=searchGeneration)return;searching=false;find.setText("Find");
            if(results==null||results.isEmpty()){showMessage("No results. Try a fuller address or coordinates.");return;}showMessage(null);if(dialogShowing())return;
            String[] labels=new String[results.size()];for(int i=0;i<results.size();i++){Address address=results.get(i);String line=address.getAddressLine(0);labels[i]=line==null?format(address.getLatitude(),address.getLongitude()):line;}
            present(new AlertDialog.Builder(this).setTitle("Choose a location").setItems(labels,(d,which)->{Address selected=results.get(which);pickTarget(new GeoPoint(selected.getLatitude(),selected.getLongitude()),true);}).setNegativeButton("Cancel",null));
        });}catch(Exception unavailable){main.post(()->{if(!isDestroyed()&&generation==searchGeneration){searching=false;find.setText("Find");showMessage("Search unavailable. Try again, paste coordinates or use the map.");}});}});
    }
    private boolean applyLocationText(String text){double[] point=Coordinates.parse(text);if(point==null)return false;pickTarget(new GeoPoint(point[0],point[1]),true);return true;}
    private void pasteLocation(){ClipboardManager clipboard=getSystemService(ClipboardManager.class);ClipData data=clipboard.getPrimaryClip();if(data==null||data.getItemCount()==0||!applyLocationText(String.valueOf(data.getItemAt(0).coerceToText(this))))showMessage("No coordinates found. Paste a coordinate pair or a map link containing coordinates.");else showMessage(null);}
    private void handleIncoming(Intent intent){if(intent==null)return;String text=Intent.ACTION_SEND.equals(intent.getAction())?intent.getStringExtra(Intent.EXTRA_TEXT):Intent.ACTION_VIEW.equals(intent.getAction())&&intent.getData()!=null?intent.getData().toString():null;if(text!=null&&!applyLocationText(text))showMessage("That link has no coordinates. Try copying the coordinates from the map.");}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleIncoming(intent);}

    private void useCurrentLocation(){
        if(active()){showMessage("Stop mock location before requesting your real location.");return;}cancelSearch();if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingPermissionAction=2;requestLocationPermission();return;}
        LocationManager manager=getSystemService(LocationManager.class);Location best=null;
        for(String provider:manager.getProviders(true))try{Location candidate=manager.getLastKnownLocation(provider);if(candidate!=null&&!candidate.isFromMockProvider()&&candidate.getTime()<=System.currentTimeMillis()&&System.currentTimeMillis()-candidate.getTime()<15*60*1000L&&(best==null||candidate.getTime()>best.getTime()))best=candidate;}catch(RuntimeException ignored){}
        if(best!=null){pickTarget(new GeoPoint(best.getLatitude(),best.getLongitude()),true);showMessage(null);return;}
        if(Build.VERSION.SDK_INT<30){showMessage("No recent real location yet. Turn on device location and try again.");return;}
        cancelRealLocation();realLocationRequest=new android.os.CancellationSignal();showMessage("Getting your location…");
        try{String provider=manager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;android.os.CancellationSignal request=realLocationRequest;
            main.postDelayed(()->{if(realLocationRequest==request){cancelRealLocation();showMessage("No location fix yet. Check device location, then try again.");}},12000);
            manager.getCurrentLocation(provider,request,getMainExecutor(),location->{if(realLocationRequest!=request||isDestroyed())return;realLocationRequest=null;if(location==null||location.isFromMockProvider())showMessage("No real location fix yet. Check device location and try again.");else{pickTarget(new GeoPoint(location.getLatitude(),location.getLongitude()),true);showMessage(null);}});
        }catch(RuntimeException unavailable){cancelRealLocation();showMessage("Couldn't get a location. Check device location and permission.");}
    }
    private void cancelRealLocation(){if(realLocationRequest!=null)realLocationRequest.cancel();realLocationRequest=null;}

    private JSONArray savedPlaces(){try{return new JSONArray(prefs().getString("places","[]"));}catch(JSONException invalid){return new JSONArray();}}
    private JSONArray recentPlaces(){try{return new JSONArray(prefs().getString("recent_places","[]"));}catch(JSONException invalid){return new JSONArray();}}
    private void saveCurrentPlace(){
        if(dialogShowing())return;double[] point=readTarget(true);if(point==null)return;cancelSearch();EditText name=new EditText(this);name.setHint("Place name");name.setSingleLine(true);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        present(new AlertDialog.Builder(this).setTitle("Save place").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String label=name.getText().toString().trim();if(label.isEmpty())label=format(point[0],point[1]);JSONArray values=savedPlaces();boolean replaced=false;
            try{JSONObject place=new JSONObject().put("name",label).put("lat",point[0]).put("lon",point[1]);for(int i=0;i<values.length();i++){JSONObject old=values.optJSONObject(i);if(old!=null&&same(old,point)){values.put(i,place);replaced=true;break;}}if(!replaced)values.put(place);prefs().edit().putString("places",values.toString()).apply();renderSavedPlaces();}catch(JSONException invalid){showMessage("Couldn't save this place. Try again.");}
        }));
    }
    private static boolean same(JSONObject place,double[] point){return point!=null&&Math.abs(place.optDouble("lat",Double.NaN)-point[0])<.000001&&Math.abs(place.optDouble("lon",Double.NaN)-point[1])<.000001;}
    private void renderSavedPlaces(){
        LinearLayout container=findViewById(R.id.savedPlaces);container.removeAllViews();JSONArray places=savedPlaces();if(places.length()==0){empty(container,"No saved places");return;}
        for(int i=0;i<places.length();i++){JSONObject place=places.optJSONObject(i);if(place==null||!Coordinates.isValid(place.optDouble("lat",Double.NaN),place.optDouble("lon",Double.NaN)))continue;int index=i;addPlaceRow(container,place.optString("name","Saved place"),place,()->placeMenu(index),true);}
    }
    private void placeMenu(int index){if(dialogShowing())return;JSONObject place=savedPlaces().optJSONObject(index);if(place==null)return;cancelSearch();present(new AlertDialog.Builder(this).setTitle(place.optString("name","Saved place")).setItems(new String[]{"Rename","Delete"},(d,which)->{currentDialog=null;if(which==1){present(new AlertDialog.Builder(this).setTitle("Delete this place?").setMessage(place.optString("name")).setNegativeButton("Cancel",null).setPositiveButton("Delete",(confirm,button)->deletePlace(index)));return;}
        EditText name=new EditText(this);name.setSingleLine(true);name.setText(place.optString("name"));name.setSelectAllOnFocus(true);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});present(new AlertDialog.Builder(this).setTitle("Rename place").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save",(confirm,button)->{String value=name.getText().toString().trim();if(value.isEmpty())return;try{JSONArray all=savedPlaces();JSONObject selected=all.optJSONObject(index);if(selected!=null){selected.put("name",value);prefs().edit().putString("places",all.toString()).apply();renderSavedPlaces();}}catch(JSONException invalid){showMessage("Couldn't rename this place.");}}));}));}
    private void deletePlace(int index){JSONArray old=savedPlaces(),next=new JSONArray();for(int i=0;i<old.length();i++)if(i!=index)next.put(old.opt(i));prefs().edit().putString("places",next.toString()).apply();renderSavedPlaces();}
    private void addPlaceRow(LinearLayout container,String label,JSONObject point,Runnable menu,boolean named){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackgroundResource(R.drawable.bg_place);row.setSelected(same(point,readTarget(false)));row.setPadding(dp(12),dp(6),0,dp(6));row.setMinimumHeight(dp(64));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);TextView primary=new TextView(this);primary.setText(label);primary.setTextColor(0xfff2f6f4);primary.setTextSize(15);primary.setMaxLines(2);primary.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(primary);
        if(named){TextView coordinates=new TextView(this);coordinates.setText(format(point.optDouble("lat"),point.optDouble("lon")));coordinates.setTextColor(0xffa6afab);coordinates.setTextSize(12);labels.addView(coordinates);}
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));row.setClickable(true);row.setFocusable(true);row.setContentDescription("Select "+label);row.setOnClickListener(v->{pickTarget(new GeoPoint(point.optDouble("lat"),point.optDouble("lon")),true);((ScrollView)findViewById(R.id.scroll)).smoothScrollTo(0,0);});
        if(menu!=null){TextView more=new TextView(this);more.setText("⋮");more.setTextColor(0xffc2d7cc);more.setTextSize(26);more.setGravity(Gravity.CENTER);more.setContentDescription("More options for "+label);more.setBackgroundResource(R.drawable.bg_touch);more.setOnClickListener(v->menu.run());row.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));row.setOnLongClickListener(v->{menu.run();return true;});}
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(8);container.addView(row,params);
    }
    private void empty(LinearLayout container,String text){TextView view=new TextView(this);view.setText(text);view.setTextColor(0xff8d9792);view.setTextSize(14);view.setPadding(0,dp(4),0,dp(4));container.addView(view);}
    private void rememberRecent(double lat,double lon){JSONArray old=recentPlaces(),next=new JSONArray();try{next.put(new JSONObject().put("lat",lat).put("lon",lon).put("at",System.currentTimeMillis()));}catch(JSONException ignored){}for(int i=0;i<old.length()&&next.length()<5;i++){JSONObject point=old.optJSONObject(i);if(point!=null&&!same(point,new double[]{lat,lon}))next.put(point);}prefs().edit().putString("recent_places",next.toString()).apply();renderRecentPlaces();}
    private void renderRecentPlaces(){LinearLayout container=findViewById(R.id.recentPlaces);container.removeAllViews();JSONArray recent=recentPlaces();if(recent.length()==0){empty(container,"No recent locations");return;}for(int i=0;i<recent.length();i++){JSONObject point=recent.optJSONObject(i);if(point!=null&&Coordinates.isValid(point.optDouble("lat",Double.NaN),point.optDouble("lon",Double.NaN)))addPlaceRow(container,format(point.optDouble("lat"),point.optDouble("lon")),point,null,false);}}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}

    @Override protected void onResume(){super.onResume();if(map!=null)map.onResume();if(status!=null)refreshStatus();if(updater!=null)updater.onResume();if(pendingSetupStart){pendingSetupStart=false;if(mockSelected())startMock();}}
    @Override protected void onPause(){if(map!=null)map.onPause();cancelRealLocation();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("target_lat",latitude.getText().toString());state.putString("target_lon",longitude.getText().toString());state.putInt("permission_action",pendingPermissionAction);state.putBoolean("setup_start",pendingSetupStart);state.putDouble("map_zoom",map.getZoomLevelDouble());state.putDouble("map_lat",map.getMapCenter().getLatitude());state.putDouble("map_lon",map.getMapCenter().getLongitude());super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){cancelSearch();cancelRealLocation();searchWorker.shutdownNow();main.removeCallbacksAndMessages(null);prefs().unregisterOnSharedPreferenceChangeListener(stateListener);if(currentDialog!=null&&currentDialog.isShowing())currentDialog.dismiss();if(updater!=null)updater.close();if(map!=null)map.onDetach();super.onDestroy();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==7){int action=pendingPermissionAction;pendingPermissionAction=0;if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){if(action==1)startMock();else if(action==2)useCurrentLocation();}else showMessage("Precise location permission is required. Choose App permissions in the menu to enable it. You can still choose a target.");}}
}

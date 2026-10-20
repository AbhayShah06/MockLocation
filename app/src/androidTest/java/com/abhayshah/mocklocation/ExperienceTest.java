package com.abhayshah.mocklocation;

import android.Manifest;
import android.content.*;
import android.location.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;

@RunWith(AndroidJUnit4.class)
public class ExperienceTest {
    private static final String PKG="com.abhayshah.mocklocation";
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private String shell(String command)throws Exception{
        try(ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");
        }
    }
    @Before public void prepare()throws Exception{
        shell("appops set "+PKG+" android:mock_location allow");
        shell("pm grant "+PKG+" "+Manifest.permission.ACCESS_COARSE_LOCATION);
        shell("pm grant "+PKG+" "+Manifest.permission.ACCESS_FINE_LOCATION);
        if(Build.VERSION.SDK_INT>=33)shell("pm grant "+PKG+" "+Manifest.permission.POST_NOTIFICATIONS);
        context().stopService(new Intent(context(),MockLocationService.class));SystemClock.sleep(150);
        context().getSharedPreferences(MainActivity.PREFS,0).edit().clear().putBoolean("notification_asked",true).commit();
        context().getSharedPreferences("updates",0).edit().clear().commit();
    }
    @After public void cleanup()throws Exception{
        context().stopService(new Intent(context(),MockLocationService.class));shell("appops set "+PKG+" android:mock_location allow");
    }
    private interface Check{boolean get();}
    private void await(Check check)throws Exception{long end=SystemClock.elapsedRealtime()+7000;while(!check.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(50);assertTrue("Timed out",check.get());}
    private void set(ActivityScenario<MainActivity> s,String lat,String lon){s.onActivity(a->{((EditText)a.findViewById(R.id.latitude)).setText(lat);((EditText)a.findViewById(R.id.longitude)).setText(lon);});}
    private void click(ActivityScenario<MainActivity> s,int id){s.onActivity(a->a.findViewById(id).performClick());}
    private String text(ActivityScenario<MainActivity> s,int id){AtomicReference<String> result=new AtomicReference<>();s.onActivity(a->result.set(((TextView)a.findViewById(id)).getText().toString()));return result.get();}
    private boolean visible(ActivityScenario<MainActivity> s,int id){AtomicBoolean result=new AtomicBoolean();s.onActivity(a->result.set(a.findViewById(id).isShown()));return result.get();}
    private void dialogButton(String label){onView(withText(label)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());}

    @Test public void startEditUpdateInvalidStopAndBackground()throws Exception{
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            set(s,"43.6532","-79.3832");click(s,R.id.start);await(MockLocationService::isPublishing);
            assertTrue(text(s,R.id.status).contains("43.653200"));
            set(s,"40.7128","-74.0060");assertTrue(text(s,R.id.status).contains("43.653200"));assertEquals("Update location",text(s,R.id.start));
            click(s,R.id.start);click(s,R.id.start);await(()->text(s,R.id.status).contains("40.712800"));
            s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);SystemClock.sleep(5500);assertTrue(MockLocationService.isPublishing());s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            Location last=context().getSystemService(LocationManager.class).getLastKnownLocation("gps");assertNotNull(last);assertTrue(last.isFromMockProvider());assertEquals(40.7128,last.getLatitude(),.000001);
            set(s,"NaN","181");click(s,R.id.start);assertTrue(MockLocationService.isPublishing());click(s,R.id.stop);await(()->!MockLocationService.isPublishing());assertEquals("Not active",text(s,R.id.statusTitle));
        }
    }
    @Test public void draftSurvivesRecreationAndNoDuplicateMenu()throws Exception{
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            set(s,"-33.8688","151.2093");s.recreate();assertEquals("-33.8688",text(s,R.id.latitude));assertEquals("151.2093",text(s,R.id.longitude));
            click(s,R.id.more);click(s,R.id.more);onView(withText("About")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());dialogButton("Done");click(s,R.id.more);dialogButton("Mock-location setup");dialogButton("Cancel");
            assertTrue(visible(s,R.id.start));
        }
    }
    @Test public void pasteSharedLinksAndSavedPlacesSequence()throws Exception{
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->a.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("point","https://maps.google.com/?q=51.5074,-0.1278")));
            click(s,R.id.pasteLocation);assertEquals("51.507400",text(s,R.id.latitude));click(s,R.id.savePlace);
            onView(androidx.test.espresso.matcher.ViewMatchers.withClassName(org.hamcrest.Matchers.equalTo(EditText.class.getName()))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(replaceText("London"));dialogButton("Save");
            click(s,R.id.savePlace);dialogButton("Save");assertEquals(1,new org.json.JSONArray(context().getSharedPreferences(MainActivity.PREFS,0).getString("places","[]")).length());
            s.onActivity(a->{Intent original=a.getIntent();a.onNewIntent(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("geo:48.8566,2.3522")));a.setIntent(original);});assertEquals("48.856600",text(s,R.id.latitude));
            onView(withContentDescription("Select 51.507400, -0.127800")).perform(scrollTo(),androidx.test.espresso.action.ViewActions.click());assertEquals("51.507400",text(s,R.id.latitude));
            onView(withContentDescription("More options for 51.507400, -0.127800")).perform(scrollTo(),androidx.test.espresso.action.ViewActions.click());dialogButton("Rename");onView(withClassName(org.hamcrest.Matchers.equalTo(EditText.class.getName()))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(replaceText("Home"));dialogButton("Save");
            onView(withContentDescription("More options for Home")).perform(scrollTo(),androidx.test.espresso.action.ViewActions.click());dialogButton("Delete");dialogButton("Cancel");assertEquals(1,new org.json.JSONArray(context().getSharedPreferences(MainActivity.PREFS,0).getString("places","[]")).length());
            onView(withContentDescription("More options for Home")).perform(androidx.test.espresso.action.ViewActions.click());dialogButton("Delete");dialogButton("Delete");assertEquals("[]",context().getSharedPreferences(MainActivity.PREFS,0).getString("places",""));
        }
    }
    @Test public void mapTapZoomAndCurrentLocationGuard()throws Exception{
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            String before=text(s,R.id.latitude);onView(withId(R.id.map)).perform(click( .3f,.4f));await(()->!text(s,R.id.latitude).equals(before));
            click(s,R.id.zoomIn);click(s,R.id.zoomOut);click(s,R.id.centerTarget);click(s,R.id.start);await(MockLocationService::isPublishing);click(s,R.id.useCurrent);assertTrue(text(s,R.id.message).contains("Stop mock location"));click(s,R.id.stop);await(()->!MockLocationService.isPublishing());
        }
    }
    private androidx.test.espresso.ViewAction click(float x,float y){return new androidx.test.espresso.action.GeneralClickAction(androidx.test.espresso.action.Tap.SINGLE,view->{int[] p=new int[2];view.getLocationOnScreen(p);return new float[]{p[0]+view.getWidth()*x,p[1]+view.getHeight()*y};},androidx.test.espresso.action.Press.FINGER);}
    @Test public void appOpRevocationStopsPublishingAndRecovers()throws Exception{
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            click(s,R.id.start);await(MockLocationService::isPublishing);shell("appops set "+PKG+" android:mock_location deny");await(()->!MockLocationService.isPublishing());await(()->text(s,R.id.status).contains("Could not publish"));
            shell("appops set "+PKG+" android:mock_location allow");click(s,R.id.start);await(MockLocationService::isPublishing);click(s,R.id.stop);await(()->!MockLocationService.isPublishing());
        }
    }
    @Test public void invalidInputAndBadPasteStayRecoverable()throws Exception{
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            set(s,"91","-181");click(s,R.id.start);assertFalse(MockLocationService.isPublishing());s.onActivity(a->assertNotNull(((EditText)a.findViewById(R.id.latitude)).getError()));
            s.onActivity(a->a.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("bad","not a coordinate")));click(s,R.id.pasteLocation);assertTrue(text(s,R.id.message).contains("No coordinates"));
            s.onActivity(a->((EditText)a.findViewById(R.id.locationSearch)).setText("35.6762,139.6503"));click(s,R.id.searchLocation);assertEquals("35.676200",text(s,R.id.latitude));click(s,R.id.start);await(MockLocationService::isPublishing);click(s,R.id.stop);await(()->!MockLocationService.isPublishing());
        }
    }
    @Test public void updateBannerOnlyForValidNewRelease()throws Exception{
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            verifyFeed(s,"{\"versionCode\":"+BuildConfig.VERSION_CODE+"}",false);
            verifyFeed(s,"{\"versionCode\":999,\"versionName\":\"test\",\"apkUrl\":\"https://github.com/AbhayShah06/MockLocation/releases/download/v999/app.apk\",\"sha256\":\""+new String(new char[64]).replace('\0','a')+"\"}",true);
            verifyFeed(s,"{\"versionCode\":999,\"apkUrl\":\"http://bad\",\"sha256\":\"PENDING\"}",false);
            verifyFeed(s,null,false);
        }
    }
    private void verifyFeed(ActivityScenario<MainActivity>s,String json,boolean expected)throws Exception{
        AtomicReference<UpdateManager> manager=new AtomicReference<>();AtomicReference<LinearLayout> host=new AtomicReference<>();AtomicInteger calls=new AtomicInteger();AtomicReference<String> feedback=new AtomicReference<>();
        s.onActivity(a->{LinearLayout h=new LinearLayout(a);((LinearLayout)a.findViewById(R.id.root)).addView(h,0);host.set(h);manager.set(new UpdateManager(a,h,feedback::set,()->{calls.incrementAndGet();if(json==null)throw new IOException("offline");return json;}));assertEquals(View.GONE,h.getChildAt(0).getVisibility());});
        await(()->calls.get()>0);SystemClock.sleep(250);s.onActivity(a->assertEquals(expected?View.VISIBLE:View.GONE,host.get().getChildAt(0).getVisibility()));
        if(expected){s.onActivity(a->{LinearLayout banner=(LinearLayout)host.get().getChildAt(0);((LinearLayout)banner.getChildAt(2)).getChildAt(1).performClick();assertEquals(View.GONE,banner.getVisibility());manager.get().checkManually();});await(()->calls.get()>1);SystemClock.sleep(250);s.onActivity(a->assertEquals(View.VISIBLE,host.get().getChildAt(0).getVisibility()));}
        s.onActivity(a->{manager.get().close();((LinearLayout)a.findViewById(R.id.root)).removeView(host.get());});
    }
}

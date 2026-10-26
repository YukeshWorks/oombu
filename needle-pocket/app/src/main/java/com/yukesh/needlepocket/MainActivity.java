package com.yukesh.needlepocket;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(8,11,17), CARD=Color.rgb(17,24,35), SURFACE=Color.rgb(24,33,47), WHITE=Color.rgb(240,244,250), MUTED=Color.rgb(151,164,183), VIOLET=Color.rgb(132,116,255), MINT=Color.rgb(92,232,192);
    private static final String MODEL_URL="https://huggingface.co/Cactus-Compute/needle3/resolve/d6c7d6a2e9655505e0f8e23339356dd1705900eb/needle3.cact";
    private static final String TOOLS="["+
      "{\"name\":\"add_task\",\"description\":\"Add a task to the local list when explicitly asked.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},\"due_date\":{\"type\":\"string\"}},\"required\":[\"title\"]}},"+
      "{\"name\":\"save_note\",\"description\":\"Save a note when asked to save or remember information.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"title\",\"content\"]}},"+
      "{\"name\":\"log_workout\",\"description\":\"Log exercise details. Never invent missing reps, sets or weight; omit unknown fields.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"exercise\":{\"type\":\"string\"},\"sets\":{\"type\":\"integer\"},\"reps\":{\"type\":\"integer\"},\"weight_kg\":{\"type\":\"number\"}},\"required\":[\"exercise\"]}},"+
      "{\"name\":\"track_habit\",\"description\":\"Record a habit status only when the user explicitly states it.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"habit\":{\"type\":\"string\"},\"completed\":{\"type\":\"boolean\"}},\"required\":[\"habit\",\"completed\"]}},"+
      "{\"name\":\"log_food\",\"description\":\"Log a food. Do not estimate amounts or nutrition not provided.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"food\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"},\"unit\":{\"type\":\"string\"}},\"required\":[\"food\"]}},"+
      "{\"name\":\"complete_task\",\"description\":\"Mark an existing task complete only when the user says it is finished.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"task_title\":{\"type\":\"string\"}},\"required\":[\"task_title\"]}},"+
      "{\"name\":\"start_focus_timer\",\"description\":\"Start an in-app countdown, not a system alarm.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"minutes\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":180},\"label\":{\"type\":\"string\"}},\"required\":[\"minutes\"]}}]";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TextView status, detail, result, records, timerView;
    private EditText input;
    private Button runButton, loadButton;
    private ProgressBar progress;
    private boolean ready=false, busy=false;
    private long timerEnds=0;
    private String timerLabel="Focus";
    private final Runnable tick=()->updateTimer();
    private final Runnable timerLoop=new Runnable(){ public void run(){ updateTimer(); if(timerEnds>0) handler.postDelayed(this,1000); }};

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("needle_pocket",MODE_PRIVATE); buildUI(); renderRecords();
        File f=modelFile(); detail.setText(f.exists()&&f.length()>1000000 ? "MODEL CACHED ON THIS PHONE · INITIALIZE TO LOAD" : "FIRST RUN DOWNLOAD · ABOUT 35 MB · THEN OFFLINE");
    }
    private void buildUI() {
        ScrollView sc=new ScrollView(this); sc.setBackgroundColor(BG);
        LinearLayout root=col(); root.setPadding(dp(20),dp(18),dp(20),dp(28)); sc.addView(root);
        TextView eyebrow=txt("ON-DEVICE ACTION ASSISTANT",10,MINT,true); root.addView(eyebrow);
        TextView title=txt("Needle Pocket",30,WHITE,true); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2); tp.topMargin=dp(5); root.addView(title,tp);
        TextView sub=txt("Small model. Useful actions. Data stays on your phone.",12,MUTED,false); root.addView(sub);
        LinearLayout engine=col(); engine.setPadding(dp(15),dp(15),dp(15),dp(15)); engine.setBackground(round(CARD,16,Color.rgb(43,57,76),1));
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2); ep.topMargin=dp(20); root.addView(engine,ep);
        status=txt("NEEDLE 3 · NOT LOADED",11,Color.rgb(255,204,120),true); engine.addView(status);
        detail=txt("MODEL DOWNLOADS ON FIRST USE",11,MUTED,false); LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2); dp1.topMargin=dp(8); engine.addView(detail,dp1);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setVisibility(View.GONE); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(4)); pp.topMargin=dp(10); engine.addView(progress,pp);
        loadButton=btn("INITIALIZE ENGINE",VIOLET,WHITE); LinearLayout.LayoutParams lb=new LinearLayout.LayoutParams(-1,dp(44)); lb.topMargin=dp(12); engine.addView(loadButton,lb); loadButton.setOnClickListener(v->loadOnly());
        TextView expl=txt("Needle is optimized for choosing actions and filling fields—not unrestricted chat. Supported actions: tasks, notes, workouts, habits, food logs and a focus timer.",12,MUTED,false); LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(-1,-2); xp.topMargin=dp(14); xp.bottomMargin=dp(20); root.addView(expl,xp);
        root.addView(txt("TRY A COMMAND",10,MUTED,true));
        addExample(root,"＋ Add a task","Add a task to revise genetic engineering tomorrow");
        addExample(root,"✎ Save a note","Save a note titled Biosensor patch with: biodegradable pH-responsive seafood freshness sensor");
        addExample(root,"↗ Log a workout","Log a workout: lat pulldown, 3 sets, 12 reps, 35 kg");
        addExample(root,"◉ Log food","Log food: chicken breast, 250 grams");
        TextView ask=txt("WHAT SHOULD I DO?",10,MUTED,true); LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2); ap.topMargin=dp(18); root.addView(ask,ap);
        input=new EditText(this); input.setTextColor(WHITE); input.setHintTextColor(Color.rgb(106,121,140)); input.setHint("Enter a command in plain English…"); input.setTextSize(14); input.setMinLines(3); input.setGravity(Gravity.TOP|Gravity.START); input.setPadding(dp(13),dp(12),dp(13),dp(12)); input.setBackground(round(SURFACE,14,Color.rgb(48,62,82),1));
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2); ip.topMargin=dp(8); root.addView(input,ip);
        runButton=btn("RUN LOCALLY  →",MINT,Color.rgb(6,20,19)); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(48)); rp.topMargin=dp(9); root.addView(runButton,rp); runButton.setOnClickListener(v->submit());
        timerView=txt("",12,MINT,true); timerView.setPadding(dp(10),dp(10),dp(10),dp(10)); timerView.setBackground(round(CARD,12,MINT,1)); timerView.setVisibility(View.GONE); LinearLayout.LayoutParams tvp=new LinearLayout.LayoutParams(-1,-2); tvp.topMargin=dp(9); root.addView(timerView,tvp);
        TextView local=txt("YOUR LOCAL LOG",10,MUTED,true); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(24); root.addView(local,lp);
        result=txt("No action yet",16,WHITE,true); LinearLayout.LayoutParams reslp=new LinearLayout.LayoutParams(-1,-2); reslp.topMargin=dp(8); root.addView(result,reslp);
        records=txt("Tasks, notes, workouts and logs created here are saved locally.",12,MUTED,false); root.addView(records);
        Button clear=btn("CLEAR LOCAL LOG",CARD,Color.rgb(255,145,145)); LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,dp(40)); cl.topMargin=dp(8); root.addView(clear,cl); clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Clear local log?").setMessage("Delete saved tasks, notes and logs from this phone?").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{prefs.edit().remove("entries").apply();renderRecords();}).show());
        TextView foot=txt("CACTUS COMPUTE · NEEDLE 3 · APACHE-2.0 MODEL",9,Color.rgb(92,106,124),false); foot.setGravity(Gravity.CENTER); LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2); fp.topMargin=dp(24); root.addView(foot,fp);
        setContentView(sc);
    }
    private void addExample(LinearLayout root,String label,String command){ Button b=btn(label,SURFACE,WHITE); b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(40)); p.topMargin=dp(6); root.addView(b,p); b.setOnClickListener(v->{input.setText(command);input.setSelection(input.length());}); }
    private void loadOnly(){ if(busy)return; busy=true; busyUI(true); worker.execute(()->{try{ensureReady();handler.post(()->{busy=false;busyUI(false);status.setText("NEEDLE 3 · READY");status.setTextColor(MINT);detail.setText("NATIVE ARM64 ENGINE · MODEL CACHED LOCALLY");result.setText("Engine initialized");records.setText("Ready for local actions.");});}catch(Throwable e){fail(e);}}); }
    private void submit(){ if(busy)return; String prompt=input.getText().toString().trim(); if(prompt.isEmpty()){input.setError("Enter a command first");return;} ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0); input.setText(""); busy=true; busyUI(true); worker.execute(()->{try{ensureReady();String raw=NeedleEngine.nativeComplete(prompt,160);if(raw==null)throw new Exception("No response from Needle.");if(raw.startsWith("__NEEDLE_ERROR__:"))throw new Exception(raw.substring(17));JSONObject env=new JSONObject(raw);JSONArray calls=env.optJSONArray("function_calls");StringBuilder summary=new StringBuilder();int count=0;if(calls!=null){for(int i=0;i<calls.length();i++){JSONObject c=calls.optJSONObject(i);if(c==null)continue;JSONObject a=c.optJSONObject("arguments");if(a==null)a=new JSONObject();String message=runTool(c.optString("name",""),a);if(message!=null&&!message.isEmpty()){if(summary.length()>0)summary.append("\n");summary.append("✓ ").append(message);count++;}}}final int number=count;final String msg=count>0?summary.toString():fallback(env);handler.post(()->{busy=false;busyUI(false);status.setText("NEEDLE 3 · READY");status.setTextColor(MINT);detail.setText("ON-DEVICE INFERENCE · NO CLOUD AI API");result.setText(number>0?"Action complete":"No action taken");records.setText(msg);renderRecords();});}catch(Throwable e){fail(e);}}); }
    private void ensureReady() throws Exception { if(ready)return; File f=modelFile();if(!f.exists()||f.length()<1000000)download(f);handler.post(()->{status.setText("LOADING MODEL…");status.setTextColor(Color.rgb(255,204,120));detail.setText("INITIALIZING NATIVE ARM64 ENGINE");progress.setVisibility(View.VISIBLE);progress.setIndeterminate(true);});String l=NeedleEngine.nativeLoad(f.getAbsolutePath());if(!"OK".equals(l))throw new Exception(l);String date=new SimpleDateFormat("yyyy-MM-dd EEE HH:mm",Locale.ENGLISH).format(new Date());String system="date: "+date+"; timezone: Asia/Kolkata. You are Needle Pocket, an on-device action router. Use only the tools provided. Never invent facts, dates, amounts, workout numbers or completion statuses. Omit optional fields if unknown. Only mark something complete when explicitly said. If no tool fits, return no call. This is not a general chat assistant.";String init=NeedleEngine.nativeInit(system,TOOLS);if(!"OK".equals(init))throw new Exception(init);ready=true; }
    private File modelFile(){return new File(getFilesDir(),"needle3.cact");}
    private void download(File dest)throws Exception{handler.post(()->{status.setText("DOWNLOADING MODEL…");detail.setText("FETCHING OFFICIAL NEEDLE 3 WEIGHTS");progress.setVisibility(View.VISIBLE);progress.setIndeterminate(false);progress.setProgress(0);});File part=new File(dest.getParentFile(),"needle3.cact.part");HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(MODEL_URL).openConnection();c.setConnectTimeout(20000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","NeedlePocket-Android/1.0");c.connect();int code=c.getResponseCode();if(code<200||code>=300)throw new Exception("Download failed with HTTP "+code+". Check internet and retry.");long total=c.getContentLengthLong(),got=0,last=0;try(InputStream in=new BufferedInputStream(c.getInputStream());FileOutputStream out=new FileOutputStream(part)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);got+=n;long now=System.currentTimeMillis();if(now-last>150){final long g=got,t=total;handler.post(()->{if(t>0){progress.setIndeterminate(false);progress.setProgress((int)Math.min(100,g*100/t));detail.setText(String.format(Locale.getDefault(),"DOWNLOADING · %.1f / %.1f MB",g/1048576.0,t/1048576.0));}else detail.setText(String.format(Locale.getDefault(),"DOWNLOADING · %.1f MB",g/1048576.0));});last=now;}}}if(part.length()<1000000)throw new Exception("Model download appears incomplete. Retry.");if(dest.exists())dest.delete();if(!part.renameTo(dest))throw new Exception("Could not save model to app storage.");}finally{if(c!=null)c.disconnect();if(part.exists())part.delete();}}
    private String runTool(String n,JSONObject a)throws JSONException{switch(n){
      case "add_task":{String title=s(a,"title");if(title.isEmpty())return "Task ignored (missing title).";String due=s(a,"due_date");add("TASK",title,due.isEmpty()?"No due date":"Due "+due,false);return "Task added: "+title+(due.isEmpty()?"":" · "+due);}
      case "save_note":{String t=s(a,"title"),c=s(a,"content");if(t.isEmpty()||c.isEmpty())return "Note ignored (missing title/content).";add("NOTE",t,c,false);return "Note saved: "+t;}
      case "log_workout":{String ex=s(a,"exercise");if(ex.isEmpty())return "Workout ignored (no exercise).";String d="Workout logged";if(has(a,"sets"))d=s(a,"sets")+" sets";if(has(a,"reps"))d+=(d.equals("Workout logged")?"":" × ")+s(a,"reps")+" reps";if(has(a,"weight_kg")&&a.optDouble("weight_kg",0)>0)d+=" · "+s(a,"weight_kg")+" kg";add("WORKOUT",ex,d,false);return "Workout logged: "+ex+" · "+d;}
      case "track_habit":{String h=s(a,"habit");if(h.isEmpty()||!a.has("completed")||a.isNull("completed"))return "Habit update ignored (missing status).";boolean yes=a.optBoolean("completed",false);add("HABIT",h,yes?"Completed":"Not completed",yes);return "Habit updated: "+h+" · "+(yes?"completed":"not completed");}
      case "log_food":{String f=s(a,"food");if(f.isEmpty())return "Food ignored (missing item).";String amount=has(a,"amount")&&a.optDouble("amount",0)>0?s(a,"amount")+" "+s(a,"unit"):"Food logged";add("FOOD",f,amount,false);return "Food logged: "+f+(amount.equals("Food logged")?"":" · "+amount);}
      case "complete_task":{String find=s(a,"task_title").toLowerCase(Locale.ROOT);JSONArray all=entries();for(int i=all.length()-1;i>=0;i--){JSONObject e=all.optJSONObject(i);if(e!=null&&"TASK".equals(e.optString("type"))&&!e.optBoolean("done")&&e.optString("title").toLowerCase(Locale.ROOT).contains(find)&&!find.isEmpty()){e.put("done",true);e.put("body","Completed");save(all);return "Task completed: "+e.optString("title");}}return "No matching open task found.";}
      case "start_focus_timer":{int m=a.optInt("minutes",0);if(m<1||m>180)return "Timer must be 1–180 minutes.";timerLabel=s(a,"label");if(timerLabel.isEmpty())timerLabel="Focus";timerEnds=System.currentTimeMillis()+m*60000L;add("TIMER",timerLabel,m+" minute in-app timer",false);handler.post(()->{handler.removeCallbacks(timerLoop);handler.post(timerLoop);});return "Started "+m+" minute in-app timer: "+timerLabel;}
      default:return "";}}
    private String fallback(JSONObject e){String t=e.optString("content",e.optString("text",""));if(!t.isEmpty()&&!"null".equals(t))return "Needle: "+t+"\n\nThis model is action-oriented. Try a task, note, workout, habit, food log or focus timer.";return "No supported action selected. Needle is an action model, not a general-purpose chatbot. Try adding a task, saving a note, logging a workout or food, updating a habit, completing a task, or starting a focus timer.";}
    private String s(JSONObject o,String k){if(!o.has(k)||o.isNull(k))return "";String v=String.valueOf(o.opt(k)).trim();return "null".equalsIgnoreCase(v)?"":v;}
    private boolean has(JSONObject o,String k){return o.has(k)&&!o.isNull(k)&&!s(o,k).isEmpty();}
    private void add(String type,String title,String body,boolean done){JSONArray a=entries();JSONObject e=new JSONObject();try{e.put("id",System.currentTimeMillis());e.put("type",type);e.put("title",title);e.put("body",body);e.put("done",done);e.put("created",new SimpleDateFormat("dd MMM · HH:mm",Locale.getDefault()).format(new Date()));a.put(e);save(a);}catch(JSONException ignored){}}
    private JSONArray entries(){try{return new JSONArray(prefs.getString("entries","[]"));}catch(JSONException e){return new JSONArray();}}
    private void save(JSONArray a){prefs.edit().putString("entries",a.toString()).apply();}
    private void renderRecords(){if(records==null)return;JSONArray a=entries();if(a.length()==0){records.setText("Nothing saved yet. Your local records will appear here.");return;}StringBuilder b=new StringBuilder();for(int i=a.length()-1,shown=0;i>=0&&shown<8;i--,shown++){JSONObject e=a.optJSONObject(i);if(e==null)continue;if(b.length()>0)b.append("\n\n");b.append("[").append(e.optString("type")).append("] ").append(e.optString("title"));if(e.optBoolean("done"))b.append(" ✓");b.append("\n").append(e.optString("body"));b.append("\n").append(e.optString("created"));}records.setText(b.toString());}
    private void updateTimer(){if(timerView==null)return;if(timerEnds<=0){timerView.setVisibility(View.GONE);return;}long sec=Math.max(0,(timerEnds-System.currentTimeMillis()+999)/1000);timerView.setVisibility(View.VISIBLE);if(sec==0){timerEnds=0;timerView.setText("FOCUS COMPLETE · "+timerLabel);Toast.makeText(this,"Focus session complete",Toast.LENGTH_LONG).show();return;}timerView.setText(String.format(Locale.getDefault(),"FOCUS %02d:%02d · %s",sec/60,sec%60,timerLabel));}
    private void busyUI(boolean b){runButton.setEnabled(!b);runButton.setText(b?"WORKING…":"RUN LOCALLY  →");loadButton.setEnabled(!b);loadButton.setText(b?"INITIALIZING…":ready?"ENGINE READY ✓":"INITIALIZE ENGINE");if(b){progress.setVisibility(View.VISIBLE);progress.setIndeterminate(true);}}
    private void fail(Throwable e){String msg=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();handler.post(()->{busy=false;busyUI(false);status.setText("ENGINE ERROR");status.setTextColor(Color.rgb(255,145,145));detail.setText(msg);progress.setVisibility(View.GONE);result.setText("Could not complete action");records.setText(msg+"\n\nFirst launch needs internet for the model download. Once cached, try again.");Toast.makeText(this,msg,Toast.LENGTH_LONG).show();});}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView txt(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button btn(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(fg);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setPadding(dp(12),0,dp(12),0);b.setBackground(round(bg,12,bg,0));return b;}
    private GradientDrawable round(int c,int r,int sc,int sw){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));if(sw>0)d.setStroke(dp(sw),sc);return d;}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    @Override protected void onDestroy(){handler.removeCallbacks(timerLoop);worker.shutdownNow();super.onDestroy();}
}
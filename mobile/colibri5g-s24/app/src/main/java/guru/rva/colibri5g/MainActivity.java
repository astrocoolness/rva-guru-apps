package guru.rva.colibri5g;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG=0xff0b1221, PANEL=0xff18243a, PRIMARY=0xff7de2c4, TEXT=0xffeaf2fd, MUTED=0xffb0c2d7;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout content, trendResults;
    private TextView info, transcript, inspector;
    private EditText apiKey, prompt, modelId, repo;
    private Spinner route, profile;
    private Button send;
    private final JSONArray messages = new JSONArray();

    private int dp(int px){return (int)(getResources().getDisplayMetrics().density*px+0.5f);}
    private GradientDrawable panelBg(){
        GradientDrawable d=new GradientDrawable();
        d.setColor(PANEL); d.setCornerRadius(dp(15)); return d;
    }
    private TextView text(String value, int size, int color, boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        t.setPadding(0,dp(5),0,dp(5)); return t;
    }
    private LinearLayout block(String title){
        LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);
        b.setBackground(panelBg());b.setPadding(dp(15),dp(13),dp(15),dp(15));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(15));content.addView(b,p);
        b.addView(text(title,18,TEXT,true));return b;
    }
    private EditText edit(String hint){
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);e.setHint(hint);e.setTextSize(15);
        e.setBackgroundTintList(ColorStateList.valueOf(PRIMARY));return e;
    }
    private Button button(String label, LinearLayout into){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setTextColor(BG);b.setBackgroundTintList(ColorStateList.valueOf(PRIMARY));
        into.addView(b,new LinearLayout.LayoutParams(-1,dp(46)));return b;
    }
    private Spinner spinner(String... options){
        Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
        s.setAdapter(a);s.setBackgroundTintList(ColorStateList.valueOf(PRIMARY));return s;
    }
    private void status(String message){runOnUiThread(()->info.setText(message));}
    private interface Task { String execute() throws Exception; }
    private void work(Task task, java.util.function.Consumer<String> success){
        worker.execute(()->{
            try{String answer=task.execute();runOnUiThread(()->success.accept(answer));}
            catch(Exception e){status("Error: "+e.getMessage());runOnUiThread(()->{if(send!=null)send.setEnabled(true);});}
        });
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ScrollView scroller=new ScrollView(this);scroller.setFillViewport(true);scroller.setBackgroundColor(BG);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18),dp(25),dp(18),dp(26));content.setFitsSystemWindows(true);
        scroller.addView(content);setContentView(scroller);
        content.addView(text("COLIBRI 5G",30,PRIMARY,true));
        content.addView(text("Samsung Galaxy S24 • Hugging Face explorer + AI chat",14,MUTED,false));

        LinearLayout box=block("Runtime & memory");
        box.addView(text("Profiles are recommendations for a separate Termux llama-server, NOT enforced by this app.",12,MUTED,false));
        profile=spinner("Cool · 3.5 GB / 2 threads","Balanced · 4.0 GB / 4 threads","Performance · 4.8 GB / 6 threads");
        profile.setSelection(1);box.addView(profile);
        box.addView(text("No model weights bundled. On-device chat requires a separately running server at 127.0.0.1:8080.",13,MUTED,false));

        box=block("Hugging Face models");
        repo=edit("owner/model-name");repo.setText("DreamFast/qwen3-8b-heretic");box.addView(repo);
        Button inspect=button("Inspect repository",box);
        inspector=text("Model architecture and available RAM must be checked separately.",13,MUTED,false);
        box.addView(inspector);
        Button trending=button("Load trending models",box);
        trendResults=new LinearLayout(this);trendResults.setOrientation(LinearLayout.VERTICAL);box.addView(trendResults);
        inspect.setOnClickListener(v->{
            String id=repo.getText().toString().trim();
            if(!id.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")){status("Enter a valid Hugging Face repo ID.");return;}
            status("Inspecting...");
            work(()->{
                JSONObject x=new JSONObject(httpGet("https://huggingface.co/api/models/"+id));
                String task=x.optString("pipeline_tag","unknown");
                int num=x.optInt("downloads",-1);
                JSONArray files=x.optJSONArray("siblings");int gguf=0;
                if(files!=null)for(int i=0;i<files.length();i++)
                    if(files.optJSONObject(i)!=null && files.optJSONObject(i).optString("rfilename","").endsWith(".gguf"))gguf++;
                return "Task: "+task+" • Downloads: "+num+" • GGUF files: "+gguf+
                    "\\nThis metadata does not establish whether the model will fit in 5 GB.";
            },response->{inspector.setText(response);status("Inspection complete.");});
        });
        trending.setOnClickListener(v->{
            status("Loading Hugging Face trending...");
            work(()->httpGet("https://huggingface.co/api/models?sort=trendingScore&direction=-1&limit=15"),response->{
                try{
                    trendResults.removeAllViews();JSONArray a=new JSONArray(response);
                    for(int i=0;i<a.length();i++){
                        JSONObject x=a.getJSONObject(i);String id=x.optString("id");
                        if(!id.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))continue;
                        Button item=button((i+1)+". "+id+" · "+x.optString("pipeline_tag","AI"),trendResults);
                        item.setTextSize(11);
                        item.setOnClickListener(view->{repo.setText(id);openURL("https://huggingface.co/"+id);});
                    }
                    status("Trending models loaded. Tap a model to open its Hugging Face page.");
                }catch(Exception e){status("Could not parse trending list: "+e.getMessage());}
            });
        });

        box=block("Chat");
        box.addView(text("Choose explicitly where messages are processed.",13,MUTED,false));
        route=spinner("Featherless cloud · provider account required","Termux local · 127.0.0.1:8080");
        box.addView(route);
        modelId=edit("Model identifier");modelId.setText("DreamFast/qwen3-8b-heretic");box.addView(modelId);
        apiKey=edit("Featherless API key (cloud only)");
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        SharedPreferences prefs=getSharedPreferences("provider",MODE_PRIVATE);
        apiKey.setText(prefs.getString("featherless_key",""));box.addView(apiKey);
        Button save=button("Save provider key on this phone",box);
        save.setOnClickListener(v->{prefs.edit().putString("featherless_key",apiKey.getText().toString().trim()).apply();status("Key saved to app-private storage. Cloud calls are always user-initiated.");});
        transcript=text("No messages yet. Cloud chat sends your prompt to Featherless; local chat sends it only to the Termux server.",14,TEXT,false);
        transcript.setTextIsSelectable(true);box.addView(transcript);
        prompt=edit("Ask a question...");prompt.setSingleLine(false);
        prompt.setMinLines(2);prompt.setMaxLines(5);box.addView(prompt);
        send=button("Send message",box);
        Button clear=button("Clear chat",box);
        clear.setOnClickListener(v->{while(messages.length()>0)messages.remove(0);transcript.setText("Chat cleared.");});
        route.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                while(messages.length()>0)messages.remove(0);
                transcript.setText(pos==0?"Cloud chat selected. Messages leave your phone.":"Local route selected. Requires an already running Termux inference server.");
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        send.setOnClickListener(v->sendMessage());
        box=block("About this build");
        box.addView(text("This APK provides a native interface and network integrations. It does NOT embed Colibri C, download GGUF weights, or run inference by itself. No hidden background services, wake locks, or automatic cloud fallback.",13,MUTED,false));
        info=text("Ready",13,PRIMARY,false);content.addView(info);
    }
    private void sendMessage(){
        String question=prompt.getText().toString().trim();
        if(question.isEmpty())return;
        boolean cloud=route.getSelectedItemPosition()==0;
        String model=modelId.getText().toString().trim();
        String key=apiKey.getText().toString().trim();
        if(cloud&&key.isEmpty()){status("Enter your Featherless API key to use cloud chat.");return;}
        if(model.isEmpty()){status("Enter a model ID.");return;}
        if(cloud){getSharedPreferences("provider",MODE_PRIVATE).edit().putString("featherless_key",key).apply();}
        if(messages.length()>=14){messages.remove(0);if(messages.length()>0)messages.remove(0);}
        JSONObject user=new JSONObject();
        try{user.put("role","user");user.put("content",question);messages.put(user);}
        catch(Exception e){status(e.getMessage());return;}
        prompt.setText("");send.setEnabled(false);
        transcript.append("\\n\\nYOU: "+question+"\\nASSISTANT: Working...");
        status(cloud?"Requesting Featherless cloud...":"Contacting local Termux server...");
        work(()->{
            JSONObject body=new JSONObject();body.put("model",model);body.put("messages",new JSONArray(messages.toString()));
            body.put("stream",false);body.put("max_tokens",256);
            String endpoint=cloud?"https://api.featherless.ai/v1/chat/completions":"http://127.0.0.1:8080/v1/chat/completions";
            JSONObject response=new JSONObject(httpPost(endpoint,body.toString(),cloud?key:null));
            return response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","");
        },answer->{
            try{JSONObject m=new JSONObject();m.put("role","assistant");m.put("content",answer);messages.put(m);}
            catch(Exception ignored){}
            String text=transcript.getText().toString();
            if(text.endsWith("Working..."))transcript.setText(text.substring(0,text.length()-10)+answer);
            else transcript.append("\\n"+answer);
            send.setEnabled(true);status("Ready.");
        });
    }
    private static String httpGet(String url) throws Exception{return httpRequest(url,null,null);}
    private static String httpPost(String url,String json,String key)throws Exception{return httpRequest(url,json,key);}
    private static String httpRequest(String url,String body,String key)throws Exception{
        URL target=new URL(url);
        String host=target.getHost();
        boolean loopback=host.equals("127.0.0.1")&&target.getPort()==8080 && "http".equals(target.getProtocol());
        boolean allowedHttps="https".equals(target.getProtocol()) &&
                (host.equals("huggingface.co")||host.equals("api.featherless.ai"));
        if(!loopback&&!allowedHttps)throw new SecurityException("Only approved hosts may be contacted");
        HttpURLConnection conn=(HttpURLConnection)target.openConnection();
        conn.setConnectTimeout(15000);conn.setReadTimeout(90000);conn.setRequestProperty("Accept","application/json");
        if(body!=null){
            conn.setRequestMethod("POST");conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json");
            if(key!=null)conn.setRequestProperty("Authorization","Bearer "+key);
            try(OutputStream out=conn.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}
        }
        int code=conn.getResponseCode();
        InputStream stream=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();
        if(stream==null)throw new IOException("HTTP "+code+" returned no response");
        try(InputStream in=stream;ByteArrayOutputStream buf=new ByteArrayOutputStream()){
            byte[] chunk=new byte[8192];int n;
            while((n=in.read(chunk))!=-1){buf.write(chunk,0,n);if(buf.size()>2000000)throw new IOException("Response too large");}
            String result=buf.toString("UTF-8");
            if(code<200||code>=300)throw new IOException("HTTP "+code+" "+result.substring(0,Math.min(200,result.length())));
            return result;
        }finally{conn.disconnect();}
    }
    private void openURL(String url){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}

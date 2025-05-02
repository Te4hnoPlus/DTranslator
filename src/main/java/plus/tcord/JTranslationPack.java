package plus.tcord;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import plus.tcord.task.TSubTask;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class JTranslationPack implements TSubTask {
    protected final HashMap<String, String>           srcLibrary   = new HashMap<>();
    protected final ConcurrentHashMap<String, String> translations = new ConcurrentHashMap<>();
    protected final AtomicInteger completedTasks = new AtomicInteger();
    protected final String libPath, trPath;
    protected final Type libType, trType;
    protected Iterator<String> iterator;
    protected long lastSavedState = 0, curDataState;
    protected final int saveDelay;
    protected int curDelay = 0;
    protected final HashSet<String> badKeys = new HashSet<>();
    protected final int backupDelay;

    public JTranslationPack(String from, String to, int saveDelay, int backupDelay) {
        libType = readFile(libPath = from, srcLibrary  );
        trType  = readFile(trPath  = to  , translations);
        this.saveDelay = saveDelay;
        this.backupDelay = backupDelay*1000;

        rescanComplededTasks();
        resetIterator();
    }


    public void rescanComplededTasks(){
        ConcurrentHashMap<String, String> translations = this.translations;
        int needComplete = 0;
        int total = srcLibrary.size();

        for (String s : srcLibrary.keySet()) {
            String next, value;
            if ((next = s) == null)
                break;
            if (((value = translations.get(next)) == null) || value.equals(next))
                ++needComplete;
        }
        this.completedTasks.set(total - needComplete);
    }


    public int completedTasks(){
        return completedTasks.get();
    }


    public int totalTasks(){
        return srcLibrary.size();
    }


    public void resetIterator(){
        iterator = srcLibrary.keySet().iterator();
    }


    public synchronized String nextItem(){
        Iterator<String> itr;
        if((itr = this.iterator) == null) return null;
        ConcurrentHashMap<String, String> translations = this.translations;
        int checks = 0;

        while (itr.hasNext()) {
            checks++;
            String next, value;
            if ((next = itr.next()) == null)
                return null;
            if (((value = translations.get(next)) == null) || value.equals(next)) {
                if(badKeys.add(next))
                    return next;
            }
        }
        if(checks == srcLibrary.size()){
            this.iterator = null;
        } else {
            resetIterator();
            return nextItem();
        }
        return null;
    }


    public void save(){
        synchronized (trPath) {
            if (lastSavedState == curDataState) return;
            writeFile(trPath, translations, trType);
            writeFile(trPath+"_b/f_"+(System.currentTimeMillis()/backupDelay)+".json", translations, trType);
            lastSavedState = curDataState;
        }
    }


    public String get(String key){
        return translations.getOrDefault(key, key);
    }


    public void set(String key, String value){
        if(value == null)return;
        String prev = translations.put(key, value);
        if(prev == null || !prev.equals(value)){
            completedTasks.addAndGet(1);
        }
        ++curDataState;
    }


    public static Type readFile(String path, Map<String, String> map) {
        File file = new File(path);
        if(!file.exists() || file.isDirectory()) return Type.ERROR;
        JsonElement jobj = null;
        boolean hasBOM = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            reader.mark(3);
            if (reader.read() != 0xFEFF) {
                reader.reset();
            } else {
                hasBOM = true;
            }
            jobj = JsonParser.parseReader(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(jobj == null) return Type.ERROR;

        jobj.getAsJsonObject().entrySet().forEach(
                stringJsonElementEntry -> map.put(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue().getAsString())
        );

        return hasBOM? Type.UTF8_BOM : Type.UTF8;
    }


    public static void writeFile(String path, Map<String, String> map, Type type) {
        File file = new File(path);
        createPathIfNeed(file);

        try(BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            if(type == Type.UTF8_BOM)
                out.write('\ufeff');

            JsonWriter jw = new JsonWriter(out);
            jw.setIndent("  ");

            jw.beginObject();
            map.forEach((s, s2) -> {
                try {
                    jw.name(s).value(s2);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            jw.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static boolean createPathIfNeed(File file){
        File parent = file.getParentFile();
        if(parent != null){
            return parent.mkdirs();
        }
        return false;
    }


    @Override
    public void onStart(TServer server) {
        Runtime.getRuntime().addShutdownHook(new Thread(this::save));
    }


    @Override
    public void onTick(TServer server) {
        if(++curDelay >= saveDelay){
            curDelay = 0;
            save();
        }
    }


    @Override
    public void onStop(TServer server) {
        save();
    }


    public enum Type {
        ERROR, UTF8, UTF8_BOM
    }
}
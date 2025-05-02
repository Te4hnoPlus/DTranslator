package plus.tcord.task;

import plus.tcord.JTranslationPack;
import plus.tcord.TServer;


public class PrintStatsTask implements TSubTask{
    private final JTranslationPack pack;
    private int curDelay = 0, delay;
    private int prev = 0, cur = 0;
    private int att2 = 0;
    private long prevTime = System.currentTimeMillis();
    private final float[] times = new float[20];
    private int timesCursor = 0;

    public PrintStatsTask(JTranslationPack pack, int delay) {
        this.pack = pack;
        this.delay = delay;
    }


    @Override
    public void onStart(TServer server) {
        System.out.printf("Completed tasks: %d/%s \n"
                , pack.completedTasks()
                , pack.totalTasks()
        );
    }


    @Override
    public void onTick(TServer tServer) {
        if(++curDelay > delay){
            curDelay = 0;
            cur = pack.completedTasks();
            if(prev != cur || att2++ >= 5) {
                att2 = 0;
                int delta = cur - prev;
                prev = cur;
                long curTime = System.currentTimeMillis();
                long dTime = (curTime - prevTime);
                prevTime = curTime;

                float speed = ((float) delta / (dTime / 1000f));
                times[(timesCursor++)%times.length] = speed;

                System.out.printf("Completed tasks: %d/%s, speed: %.1fts, left: %s\n"
                        , pack.completedTasks()
                        , pack.totalTasks()
                        , speed
                        , calcAwaitTime()
                );
            }
        }
    }


    private String calcAwaitTime(){
        float md = calcMid(times);
        int need = pack.totalTasks() - pack.completedTasks();
        int totalTime = (int)(need / md);

        int sec = totalTime%60;
        int minutes = totalTime/60;
        int hours = minutes/60;
        minutes = minutes%60;
        return hours+"h"+minutes+"m"+sec+"s";
    }


    private static float calcMid(float[] floats){
        float sum = 0;
        int total = 1;
        for (float fl:floats){
            if(fl != 0){
                sum += fl;
                total += 1;
            }
        }
        return sum / total;
    }
}
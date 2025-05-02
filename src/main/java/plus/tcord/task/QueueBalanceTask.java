package plus.tcord.task;

import plus.tcord.JTranslationPack;
import plus.tcord.TServer;


public class QueueBalanceTask implements TSubTask {
    final JTranslationPack pack;
    private int curTasks = 10;

    public QueueBalanceTask(JTranslationPack pack) {
        this.pack = pack;
    }


    @Override
    public void onTick(TServer tServer) {
        int curSize = tServer.queueSize();
        if(curSize <= 1 && curTasks < 2000){
            curTasks *= 2;
        }
        if(curSize < curTasks + 10) {
            addTasks(curTasks, tServer);
        } else if(curSize > curTasks && curSize > 10 && curTasks > 10) {
            curTasks -= 5;
        }
    }


    private void addTasks(int count, TServer server) {
        JTranslationPack pack = this.pack;
        String next = pack.nextItem();
        while (next != null && count > 0) {
            String finalNext = next;
            server.queue(finalNext, s -> pack.set(finalNext, s));
            next = pack.nextItem();
            --count;
        }
    }
}
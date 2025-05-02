package plus.tcord.task;

import plus.tcord.JTranslationPack;
import plus.tcord.TServer;


/**
 * Задача остановки сервера, если все задачи на перевод выполнены
 */
public class StopOnCompleteTasks implements TSubTask {
    private final JTranslationPack pack;
    private int attempts = 3;

    public StopOnCompleteTasks(JTranslationPack pack) {
        this.pack = pack;
    }


    @Override
    public void onTick(TServer server) {
        if(pack.completedTasks() == pack.totalTasks()) {
            if(--attempts == 0) {
                System.out.println("All tasks completed, stopping server ...");
                server.stop();
            } else {
                attempts = 3;
            }
        }
    }
}
package plus.tcord.task;


import plus.tcord.TServer;

@FunctionalInterface
public interface TSubTask {
    default void onStart(TServer server){};
    void onTick(TServer server);
    default void onStop(TServer server){};
}
package plus.tcord.task;

import plus.tcord.TServer;


/**
 * Саб-задача для сервера переводов {@link TServer}
 */
@FunctionalInterface
public interface TSubTask {
    /**
     * Вызывается 1 раз при старте сервера
     */
    default void onStart(TServer server){};


    /**
     * Вызывается каждую секунду, пока сервер активен
     */
    void onTick(TServer server);


    /**
     * Вызывается 1 раз при остановке сервера
     */
    default void onStop(TServer server){};
}
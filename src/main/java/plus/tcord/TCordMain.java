package plus.tcord;

import plus.tcord.task.PrintStatsTask;
import plus.tcord.task.StopOnCompleteTasks;


/**
 * Лаунчер децентрализованного сервера переводов
 */
public class TCordMain {
    public static boolean DEBUG = false;

    public static void main(String[] args) throws Exception {
        if(args.length == 0){
            System.out.println("Use: java -jar TCord.jar <port> <lang> <file_from> <file_to>");
            return;
        }
        String port = args[0]; //"23700";
        String lang = args[1]; //"eng:ru";

        TServer server = new TServer(Integer.parseInt(port));

        String fileFrom = args[2], fileTo = args[3];

        System.out.println("Loading packs ...");
        JTranslationPack pack = new JTranslationPack(fileFrom, fileTo, 15, 300);
        System.out.println("Packs loaded.");

        System.out.println("Starting server ...");
        server.setHello(lang, "ready");

        server.setPack(pack);
        server.sub(pack);
        //server.sub(new QueueBalanceTask(pack));
        server.sub(new PrintStatsTask(pack, 15));
        server.sub(new StopOnCompleteTasks(pack));

        server.start(false);

        System.out.printf("Server started at :%s, lang :%s\nPrint \"exit\" to stop\n", port, lang);

        while (true) {
            if(!handleConsoleInput(server, pack))
                break;
        }

        server.stop();
    }


    /**
     * Обработка ввода из консоли
     */
    private static boolean handleConsoleInput(TServer server, JTranslationPack pack) {
        String input = System.console().readLine();
        if(input == null || input.isEmpty()) return true;
        if(input.equals("exit")) {
            return false;
        }
        if(input.equals("stats")){
            System.out.printf("Completed tasks: %d/%s \n"
                    , pack.completedTasks()
                    , pack.totalTasks()
            );
        }
        return true;
    }
}
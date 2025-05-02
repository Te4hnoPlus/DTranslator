import plus.tcord.JTranslationPack;

import java.io.IOException;

public class TestJF {
    public static void main(String[] args) throws IOException {
        String f1 = "C:/Users/Te4hnoPlus/Desktop/GF2_OUT/translation_src.json";
        String t1 = "C:/Users/Te4hnoPlus/Desktop/GF2_OUT/translation_ru1.json";

        JTranslationPack pack = new JTranslationPack(f1, t1, 0, 0);

        for(int i = 0; i < 100; i++) {
            System.out.println(pack.nextItem());
        }
    }
}
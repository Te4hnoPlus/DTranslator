public class PackB1 {

    public static void main(String[] args) {
        System.out.println(new String(packByte("key", "value")));
    }


    public static byte[] packByte(String key, String value) {
        byte[] result = new byte[4 + key.length() + value.length()];
        writeInt(result, key.length() + value.length());

        System.arraycopy(key.getBytes() , 0 , result, 4, key.length());
        System.arraycopy(value.getBytes(), 0, result, 4 + key.length(), value.length());

        return result;
    }


    public static void writeInt(byte[] buffer, int value) {
        buffer[0] = (byte) (value & 0xff);
        buffer[1] = (byte) ((value >> 8) & 0xff);
        buffer[2] = (byte) ((value >> 16) & 0xff);
        buffer[3] = (byte) ((value >> 24) & 0xff);
    }
}

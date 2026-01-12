package bgu.spl.net.impl.stomp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class StompMessageEncoderDecoder implements MessageEncoderDecoder<String>{
    
    private byte[] bytes = new byte[1 << 10];
    private int len = 0;

    @Override
    public String decodeNextByte(byte nextByte){
        if(nextByte == '\u0000'){
            return this.popString();
        }

        pushByte(nextByte);
        return null;
    }

    @Override
    public byte[] encode(String message){
        return (message + "\n").getBytes(); //uses utf8 by default
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }
 
        bytes[len++] = nextByte;
    }

    /**
     * notice that we explicitly requesting that the string will be decoded from UTF-8, this is not actually required as it is the default encoding in java.
     * @return
     */
    private String popString() {
        String result = new String(bytes, 0, len, StandardCharsets.UTF_8);
        len = 0;
        return result;
    }
}

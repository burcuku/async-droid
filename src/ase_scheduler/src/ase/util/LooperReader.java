package ase.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.util.Log;

public class LooperReader {
    
    private static LooperReader INSTANCE;
    
    private final Field messagesField;
    private final Field nextField;
    private final Field queueField;
    
    private LooperReader() {
        try {
            queueField = Looper.class.getDeclaredField("mQueue");
            queueField.setAccessible(true);
            messagesField = MessageQueue.class.getDeclaredField("mMessages");
            messagesField.setAccessible(true);
            nextField = Message.class.getDeclaredField("next");
            nextField.setAccessible(true);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static LooperReader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LooperReader();
        }
        return INSTANCE;
    }
    
    public Looper getLooper(Thread t) {
        if (t instanceof HandlerThread) {
            return ((HandlerThread) t).getLooper();
        }
        if (t.getId() == 1) {
            return Looper.getMainLooper();
        }
        return null;
    }

    public boolean hasEmptyLooper(Thread t) {
        boolean empty = true;
        Looper looper = getLooper(t);
        
        if(looper != null) {
            try {
                MessageQueue messageQueue = (MessageQueue) queueField.get(looper);
                
                synchronized(messageQueue) {
                    Message nextMessage = (Message) messagesField.get(messageQueue);   
                    while(nextMessage != null) {
                        // TODO: Get recurring messages for the app from the user
                        if(nextMessage.toString().contains("BinderProxy") || nextMessage.toString().contains("barrier") || nextMessage.toString().contains("android.widget.Editor$Blink")
                                // for radio player
                                || nextMessage.toString().contains("android.widget.TextView$Marquee") || nextMessage.toString().contains("com.teleca.jamendo.media.PlayerEngineImpl$1")
                                        || nextMessage.toString().contains("com.teleca.jamendo.widget.ReflectiveSurface")
                                // for comics reader
                                || nextMessage.toString().contains("com.android.org.chromium.base.SystemMessageHandler")) {
                            nextMessage = (Message) nextField.get(nextMessage);
                        } else {
                            //Log.i("LooperReader", "Breaking here..");
                            empty = false;
                            break;
                        }
                    }   
                    //contents = this.dumpQueue(t);
                }
   
            } catch (Exception e) {
                Log.e("Reflection", "Could not check the emptiness of the message queue");
            }
        }
        
        return empty;
    }
    
    public String dumpQueue(Thread t) { 
        StringBuilder sb = new StringBuilder();
        
        Looper looper = getLooper(t);      
        if(looper != null) {
            try {
                MessageQueue messageQueue = (MessageQueue) queueField.get(looper);
                List<Message> messages = getMessages(t);
        
                synchronized(messageQueue) {
                    for(Message m: messages) {
                        sb.append("\n\t" + m.toString());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        return sb.toString();
    }

    public List<Message> getMessages(Thread t) {     
        Looper looper = getLooper(t);      
        if(looper != null) {
            try {
                MessageQueue messageQueue = (MessageQueue) queueField.get(looper);
                return getMessages(messageQueue);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
        return new ArrayList<Message>();
    }

    public List<Message> getMessages(MessageQueue messageQueue) { 
        List<Message> messages = new ArrayList<Message>(); 
        synchronized(messageQueue) {
            try {
                Message nextMessage = (Message) messagesField.get(messageQueue);          
                while(nextMessage != null) {
                    messages.add(nextMessage);
                    nextMessage = (Message) nextField.get(nextMessage);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        
        return messages;
    }

}

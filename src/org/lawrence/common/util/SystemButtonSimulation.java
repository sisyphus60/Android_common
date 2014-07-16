package org.lawrence.common.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Instrumentation;
import android.view.KeyEvent;
/**
 * Trigger an action like click the read back button.
 * @author Lawrence wang
 *
 */
public class SystemButtonSimulation {

    private static ExecutorService sSingleTaskExecutor = null;
    private static KeyRunnable sKeyRunnable = null;
    static {
        sSingleTaskExecutor = Executors.newSingleThreadExecutor();
        sKeyRunnable = new KeyRunnable();
    }

    static class KeyRunnable implements Runnable {

        private int mKeyCode = KeyEvent.KEYCODE_BACK;
        
        public void setKeyCode(int keyCode) {
            mKeyCode = keyCode;
        }
        
        @Override
        public void run() {
            try {
                Instrumentation inst = new Instrumentation();
                inst.sendKeyDownUpSync(mKeyCode);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
    }
    
    /**
     * Give a key action
     * 
     * @param keyCode
     */
    public static void actionKeyCode(int keyCode) {
        sKeyRunnable.setKeyCode(keyCode);
        sSingleTaskExecutor.execute(sKeyRunnable);
    }
}
